package cn.hautpocket.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.work.*
import cn.hautpocket.app.domain.OnlineRoom
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

data class DailySample(val roomId: String, val roomName: String, val device: String, val date: String, val raw: String, val sampledAt: Long)

object DailySampling {
    val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    fun accountKey(account: String): String = MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString("") { "%02x".format(it) }
    fun sameDay(startedAt: Long, finishedAt: Long): Boolean =
        Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate() == Instant.ofEpochMilli(finishedAt).atZone(zone).toLocalDate()
    fun due(now: Long, lastSuccess: Long): Boolean {
        val local = Instant.ofEpochMilli(now).atZone(zone)
        val interval = if (local.hour >= 23) 15 * 60_000L else 60 * 60_000L
        return lastSuccess == 0L || now < lastSuccess || !sameDay(now, lastSuccess) || now - lastSuccess >= interval
    }
    fun value(raw: String): Pair<Double, String>? {
        val match = Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*(度|kWh|kW·h|kW.h)?\\s*$", RegexOption.IGNORE_CASE).matchEntire(raw) ?: return null
        val number = match.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
        // The user confirmed this electric today-use field is measured in kWh.
        return number to "kWh"
    }
    fun samples(rooms: List<OnlineRoom>, timestamp: Long): List<DailySample> {
        val now = Instant.ofEpochMilli(timestamp).atZone(zone)
        return rooms.filterNot { it.failed }.flatMap { room ->
            val electric = room.devices.filter { it.category == "电" && it.name.isNotBlank() && it.name != "—" }
            val duplicates = electric.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
            electric.filter { it.name !in duplicates && value(it.todayUse) != null }.map {
                DailySample(room.id, room.name, it.name, now.toLocalDate().toString(), it.todayUse, timestamp)
            }
        }
    }
    fun schedule(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        // Retire the old daily five-minute window; its KEEP policy cannot migrate intervals.
        manager.cancelUniqueWork("daily-electricity")
        if (!enabled) { manager.cancelUniqueWork("electricity-sampling-v2"); manager.cancelUniqueWork("electricity-check-now"); return }
        val request = PeriodicWorkRequestBuilder<DailyElectricityWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        manager.enqueueUniquePeriodicWork("electricity-sampling-v2", ExistingPeriodicWorkPolicy.KEEP, request)
        checkNow(context)
    }
    fun checkNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("electricity-check-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<DailyElectricityWorker>().setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    fun status(context: Context): String {
        val account = AccountVault(context).read().orEmpty()
        val prefs = context.getSharedPreferences("sampling-status", Context.MODE_PRIVATE)
        if (prefs.getString("account", "") != accountKey(account)) return "尚未执行后台查询"
        val time = prefs.getLong("attempt", 0)
        val stamp = Instant.ofEpochMilli(time).atZone(zone).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm"))
        return "$stamp · ${prefs.getString("result", "等待结果") }"
    }
    fun record(context: Context, account: String, rooms: List<OnlineRoom>, startedAt: Long): Int {
        val finishedAt = System.currentTimeMillis()
        if (!sameDay(startedAt, finishedAt)) return 0
        val samples = samples(rooms, finishedAt)
        DailyHistory(context).use { it.save(account, samples) }
        return samples.size
    }
}

class DailyHistory(context: Context) : SQLiteOpenHelper(context, "electricity-history.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) { db.execSQL("CREATE TABLE samples (account TEXT NOT NULL, room TEXT NOT NULL, room_name TEXT NOT NULL, device TEXT NOT NULL, day TEXT NOT NULL, raw TEXT NOT NULL, sampled_at INTEGER NOT NULL, PRIMARY KEY(account,room,device,day))") }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun save(account: String, samples: List<DailySample>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            samples.forEach { s ->
                val values = ContentValues().apply { put("account", DailySampling.accountKey(account)); put("room", s.roomId); put("room_name", s.roomName); put("device", s.device); put("day", s.date); put("raw", s.raw); put("sampled_at", s.sampledAt) }
                // Concurrent manual and background requests must not replace a newer sample.
                val updated = db.update("samples", values, "account=? AND room=? AND device=? AND day=? AND sampled_at<=?",
                    arrayOf(DailySampling.accountKey(account), s.roomId, s.device, s.date, s.sampledAt.toString()))
                if (updated == 0) db.insertWithOnConflict("samples", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun list(account: String): List<DailySample> {
        if (account.isBlank()) return emptyList()
        val result = mutableListOf<DailySample>()
        readableDatabase.query("samples", arrayOf("room","room_name","device","day","raw","sampled_at"), "account=?", arrayOf(DailySampling.accountKey(account)), null, null, "day ASC") .use { c ->
            while (c.moveToNext()) result += DailySample(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getLong(5))
        }
        return result
    }
    fun clear(account: String) { if (account.isNotBlank()) writableDatabase.delete("samples", "account=?", arrayOf(DailySampling.accountKey(account))) }
}

class DailyElectricityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val account = AccountVault(applicationContext).read() ?: return Result.success()
        if (!PocketStore(applicationContext).load().autoSampling) return Result.success()
        val prefs = applicationContext.getSharedPreferences("sampling-status", Context.MODE_PRIVATE)
        val sameAccount = prefs.getString("account", "") == DailySampling.accountKey(account)
        val started = System.currentTimeMillis()
        if (sameAccount && !DailySampling.due(started, prefs.getLong("success", 0))) return Result.success()
        prefs.edit().apply { if (!sameAccount) clear(); putString("account", DailySampling.accountKey(account)); putLong("attempt", started); putString("result", "正在查询") }.apply()
        return try {
            val rooms = WanxiaoClient().queryOwnBoundRooms(account)
            if (AccountVault(applicationContext).read() != account || !PocketStore(applicationContext).load().autoSampling) return Result.success()
            val count = DailySampling.record(applicationContext, account, rooms, started)
            prefs.edit().apply {
                putString("result", if (count > 0) "已保存 $count 个电表的当日读数" else "未取得有效当日读数，请手动查询检查绑定和字段")
                if (count > 0) putLong("success", System.currentTimeMillis())
            }.apply()
            Result.success()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            prefs.edit().putString("result", "查询失败，等待联网重试；可手动查询检查账号").apply()
            Result.retry()
        }
    }
}
