package cn.hautpocket.app.data

import android.Manifest
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cn.hautpocket.app.MainActivity
import cn.hautpocket.app.R
import cn.hautpocket.app.domain.*

object FocusReminders {
    private const val CHANNEL = "focus-completion"
    private const val ID = 2001
    private const val END = "expectedEnd"
    internal const val TEST = "cn.hautpocket.FOCUS_TEST"
    private fun alarms(context: Context) = context.getSystemService(AlarmManager::class.java)
    private fun pending(context: Context, end: Long) = PendingIntent.getBroadcast(context, ID,
        Intent(context, FocusAlarmReceiver::class.java).setAction("cn.hautpocket.FOCUS_DONE").addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtra(END, end),
        PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "专注完成提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "专注计时结束时提醒休息"; enableVibration(true)
            })
    }
    fun notificationsEnabled(context: Context): Boolean {
        val allowed = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return allowed && NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun exactAllowed(context: Context) = Build.VERSION.SDK_INT < 31 || alarms(context).canScheduleExactAlarms()

    fun schedule(context: Context, state: PocketState) {
        if (state.focusEndsAt <= 0) { alarms(context).cancel(pending(context, 0)); return }
        val alarm = pending(context, state.focusEndsAt)
        FocusDelivery.planned(context,"focus",state.focusEndsAt,exactAllowed(context))
        try {
            if (exactAllowed(context)) alarms(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.focusEndsAt, alarm)
            else alarms(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.focusEndsAt, alarm)
        } catch (_: SecurityException) {
            FocusDelivery.planned(context,"focus",state.focusEndsAt,false)
            alarms(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.focusEndsAt, alarm)
        }
    }
    fun start(context: Context, minutes: Int): PocketState {
        require(minutes in 1..180)
        createChannel(context)
        val next = PocketStore(context).update { it.copy(focusMinutes = minutes, focusEndsAt = System.currentTimeMillis() + minutes * 60_000L) }
        NotificationManagerCompat.from(context).cancel(ID)
        schedule(context, next)
        return next
    }
    fun test(context: Context) {
        createChannel(context)
        NotificationManagerCompat.from(context).cancel(ID + 1)
        val alarm = PendingIntent.getBroadcast(context, ID + 1, Intent(context, FocusAlarmReceiver::class.java).setAction(TEST).addFlags(Intent.FLAG_RECEIVER_FOREGROUND), PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val at = System.currentTimeMillis() + 10_000
        FocusDelivery.planned(context,"test",at,exactAllowed(context))
        try {
            if (exactAllowed(context)) alarms(context).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm)
            else alarms(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm)
        } catch (_: SecurityException) { FocusDelivery.planned(context,"test",at,false); alarms(context).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm) }
    }
    fun stop(context: Context): PocketState {
        val next = PocketStore(context).update { it.copy(focusEndsAt = 0) }
        schedule(context, next)
        return next
    }
    fun finish(context: Context, expectedEnd: Long, now: Long = System.currentTimeMillis(), notify: Boolean = true): PocketState {
        var completed = false
        var minutes = 0
        val next = PocketStore(context).update { old ->
            val result = FocusCompletion.finish(old, expectedEnd, now)
            completed = result != old; minutes = old.focusMinutes
            result
        }
        if (completed) {
            schedule(context, next)
            if (notify) showCompletion(context, minutes)
        }
        return next
    }
    fun restore(context: Context) {
        createChannel(context)
        val current = PocketStore(context).load()
        if (current.focusEndsAt > 0 && current.focusEndsAt <= System.currentTimeMillis())
            finish(context, current.focusEndsAt, notify = System.currentTimeMillis() - current.focusEndsAt <= 15 * 60_000L)
        else schedule(context, current)
    }
    private fun showCompletion(context: Context, minutes: Int) {
        showNotification(context, ID, "专注完成，休息一下", "已完成 $minutes 分钟专注，给自己一点放松时间。")
    }
    internal fun showTest(context: Context) = showNotification(context, ID + 1, "专注提醒测试", "后台通知已送达。本次测试不计入专注记录。")
    private fun showNotification(context: Context, id: Int, title: String, text: String) {
        val kind = if (id == ID + 1) "test" else "focus"
        if (!notificationsEnabled(context)) { FocusDelivery.submitted(context,kind,"通知权限或渠道已关闭"); return }
        val open = PendingIntent.getActivity(context, ID, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build()
        try { NotificationManagerCompat.from(context).notify(id, notification); FocusDelivery.submitted(context,kind) }
        catch (_: SecurityException) { FocusDelivery.submitted(context,kind,"通知权限已变化，请检查系统设置") }
    }
    internal fun expectedEnd(intent: Intent) = intent.getLongExtra(END, 0)
}

class FocusAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            if (intent.action == FocusReminders.TEST) { FocusDelivery.received(context,"test"); FocusReminders.showTest(context) }
            else if (intent.action == "cn.hautpocket.FOCUS_DONE") { FocusDelivery.received(context,"focus"); FocusReminders.finish(context, FocusReminders.expectedEnd(intent)) }
        }
    }
}

class FocusRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
            runCatching { FocusReminders.restore(context) }
    }
}
