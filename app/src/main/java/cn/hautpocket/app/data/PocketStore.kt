package cn.hautpocket.app.data

import android.content.Context
import cn.hautpocket.app.domain.*
import org.json.JSONArray
import org.json.JSONObject

/** Private app storage; credentials/cookies are never copied here. */
class PocketStore(context: Context) {
    companion object { private val lock = Any() }
    private val prefs = context.getSharedPreferences("pocket", Context.MODE_PRIVATE)
    var loadWarning: String? = null
        private set

    fun load(): PocketState {
        val text = prefs.getString("state", null) ?: return PocketState()
        return runCatching { StateCodec.decode(text) }.getOrElse {
            loadWarning = "本地数据无法读取，已阻止覆盖。请保留应用和原始数据并联系维护者。"
            PocketState()
        }
    }

    fun save(state: PocketState) = synchronized(lock) {
        check(loadWarning == null) { "本地数据读取异常，已阻止覆盖。请先保留备份。" }
        check(prefs.edit().putString("state", StateCodec.encode(state)).commit()) { "保存失败，请检查手机存储空间" }
    }

    fun update(change: (PocketState) -> PocketState): PocketState = synchronized(lock) {
        val old = load()
        val next = change(old)
        if (next != old) save(next)
        next
    }
}

object StateCodec {
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    private fun JSONObject.array(key: String) = optJSONArray(key) ?: JSONArray()
    private fun JSONObject.ints(key: String) = array(key).let { a -> (0 until a.length()).map { a.getInt(it) } }

    fun encode(state: PocketState): String = JSONObject().apply {
        put("version", 1)
        put("campus", state.campus.name)
        put("themeColor", state.themeColor); put("fontScale", state.fontScale); put("weekView", state.weekView); put("autoSampling", state.autoSampling); put("reduceMotion", state.reduceMotion)
        put("textColor", TextColors.normalize(state.textColor))
        put("semesterStart", state.semesterStart)
        put("totalWeeks", state.totalWeeks)
        put("scheduleImportedAt", state.scheduleImportedAt)
        put("courses", JSONArray(state.courses.map { c -> JSONObject().apply {
            put("id", c.id); put("name", c.name); put("teacher", c.teacher); put("location", c.location)
            put("day", c.day); put("weeks", JSONArray(c.weeks)); put("sections", JSONArray(c.sections)); put("campus", c.campus.name); put("note", c.note)
        } }))
        put("sectionTimes", JSONArray(state.sectionTimes.map { t -> JSONObject().apply {
            put("section", t.section); put("start", t.start); put("end", t.end)
        } }))
        put("meters", JSONArray(state.meters.map { m -> JSONObject().apply {
            put("id", m.id); put("campus", m.campus.name); put("building", m.building); put("room", m.room)
            put("kind", m.kind.name); put("providerRoomId", m.providerRoomId)
        } }))
        put("readings", JSONArray(state.readings.map { r -> JSONObject().apply {
            put("id", r.id); put("meterId", r.meterId); put("timestamp", r.timestamp); put("remaining", r.remaining)
            put("added", r.addedSincePrevious ?: JSONObject.NULL); put("note", r.note)
        } }))
        put("focusSessions", JSONArray(state.focusSessions.map { s -> JSONObject().apply {
            put("finishedAt", s.finishedAt); put("minutes", s.minutes)
        } }))
        put("focusEndsAt", state.focusEndsAt)
        put("focusMinutes", state.focusMinutes)
        put("onlineRooms", JSONArray(state.onlineRooms.map { room -> JSONObject().apply {
            put("id", room.id); put("name", room.name); put("fetchedAt", room.fetchedAt); put("failed", room.failed)
            put("devices", JSONArray(room.devices.map { d -> JSONObject().apply {
                put("name", d.name); put("category", d.category); put("balance", d.balance); put("todayUse", d.todayUse)
                put("totalBought", d.totalBought); put("status", d.status)
            } }))
        } }))
        put("onlineRoomCampuses", JSONObject().apply { state.onlineRoomCampuses.forEach { (id, campus) -> put(id, campus.name) } })
    }.toString()

    fun decode(text: String): PocketState {
        val root = JSONObject(text)
        require(root.getInt("version") == 1) { "不支持的数据版本" }
        val meters = root.array("meters").objects().map { m -> Meter(
            id = m.getString("id"), campus = Campus.from(m.getString("campus")), building = m.getString("building"),
            room = m.getString("room"), kind = MeterKind.valueOf(m.getString("kind")), providerRoomId = m.optString("providerRoomId")) }
        val readings = root.array("readings").objects().map { r -> Reading(
            id = r.getString("id"), meterId = r.getString("meterId"), timestamp = r.getLong("timestamp"),
            remaining = r.getDouble("remaining"), addedSincePrevious = if (r.isNull("added")) null else r.getDouble("added"),
            note = r.optString("note", "手工抄录自官方服务")) }
        require(readings.all { r -> meters.any { it.id == r.meterId } }) { "读数缺少对应表计" }
        return PocketState(
            textColor = TextColors.normalize(root.optString("textColor", "auto")),
            campus = Campus.from(root.optString("campus")), themeColor = root.optString("themeColor", "green").let { if (it in listOf("green", "blue", "purple", "orange")) it else if (TextColors.validCustom(it)) TextColors.normalize(it) else "green" }, fontScale = root.optInt("fontScale", 100).coerceIn(85, 130), weekView = root.optBoolean("weekView", false), autoSampling = root.optBoolean("autoSampling", true), reduceMotion = root.optBoolean("reduceMotion", false), semesterStart = root.optString("semesterStart"), totalWeeks = root.optInt("totalWeeks", 20),
            scheduleImportedAt = root.optLong("scheduleImportedAt"),
            courses = root.array("courses").objects().map { c -> Course(id = c.getString("id"), name = c.getString("name"),
                teacher = c.optString("teacher"), location = c.optString("location"), day = c.getInt("day"),
                weeks = c.ints("weeks"), sections = c.ints("sections"), campus = Campus.from(c.optString("campus")), note = c.optString("note")) },
            sectionTimes = root.array("sectionTimes").objects().map { SectionTime(it.getInt("section"), it.getString("start"), it.getString("end")) },
            meters = meters, readings = readings,
            focusSessions = root.array("focusSessions").objects().map { FocusSession(it.getLong("finishedAt"), it.getInt("minutes")) },
            focusEndsAt = root.optLong("focusEndsAt"), focusMinutes = root.optInt("focusMinutes", 25),
            onlineRooms = root.array("onlineRooms").objects().map { room -> OnlineRoom(
                room.getString("id"), room.getString("name"), room.array("devices").objects().map { d -> OnlineDevice(
                    d.getString("name"), d.getString("category"), d.getString("balance"), d.getString("todayUse"), d.getString("totalBought"), d.getString("status")) },
                room.getLong("fetchedAt"), room.optBoolean("failed")) },
            onlineRoomCampuses = root.optJSONObject("onlineRoomCampuses")?.let { map -> map.keys().asSequence().associateWith { Campus.from(map.getString(it)) } } ?: emptyMap(),
        )
    }
}
