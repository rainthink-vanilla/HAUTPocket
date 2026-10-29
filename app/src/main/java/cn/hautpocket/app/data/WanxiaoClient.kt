package cn.hautpocket.app.data

import cn.hautpocket.app.domain.OnlineDevice
import cn.hautpocket.app.domain.OnlineRoom
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Independently implemented from publicly documented protocol fields; read-only operations only. */
class WanxiaoClient {
    companion object {
        const val API = "https://xqh5.17wanxiao.com/smartWaterAndElectricityService/SWAEServlet"
        // Historical official school directory fact; live HAUT tenant verification is still required.
        const val HAUT_CODE = "43"
    }

    fun queryOwnBoundRooms(account: String): List<OnlineRoom> {
        require(account.matches(Regex("[A-Za-z0-9_-]{4,32}"))) { "请填写本人校园卡账号（通常为学号）" }
        val binding = call("getbindroom", JSONObject().put("cmd", "getbindroom").put("account", account))
        val rooms = WanxiaoProtocol.boundRooms(binding)
        require(rooms.isNotEmpty()) { "未找到绑定房间，请先在完美校园“智能水电”绑定自己的宿舍。" }
        require(rooms.size <= 20) { "返回房间数异常，已停止查询" }
        val start = System.currentTimeMillis()
        return rooms.map { room ->
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            val id = MessageDigest.getInstance("SHA-256").digest(room.verification.toByteArray()).joinToString("") { "%02x".format(it) }
            if (System.currentTimeMillis() - start > 45_000) OnlineRoom(id, room.name, emptyList(), 0, failed = true)
            else try {
                val timestamp = ZonedDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "000"
                val data = call("h5_getstuindexpage", JSONObject().put("cmd", "h5_getstuindexpage").put("account", account)
                    .put("roomverify", room.verification).put("timestamp", timestamp))
                OnlineRoom(id, room.name, WanxiaoProtocol.devices(data), System.currentTimeMillis())
            } catch (_: Exception) {
                OnlineRoom(id, room.name, emptyList(), 0, failed = true)
            }
        }
    }

    private fun call(method: String, payload: JSONObject): JSONObject {
        val params = mapOf("customercode" to HAUT_CODE, "method" to method, "param" to payload.toString())
            .entries.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
        val connection = URL("$API?$params").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("Accept", "application/json")
        try {
            require(connection.responseCode in 200..299) { "智能水电服务暂时不可用，请稍后重试" }
            val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val output = StringBuilder()
                val chunk = CharArray(4096)
                while (true) {
                    val count = reader.read(chunk)
                    if (count < 0) break
                    require(output.length + count <= 1_000_000) { "服务响应过大，已停止读取" }
                    output.append(chunk, 0, count)
                }
                output.toString()
            }
            return WanxiaoProtocol.body(text)
        } catch (e: IllegalArgumentException) { throw e }
        catch (_: Exception) { throw IllegalArgumentException("连接智能水电失败，请检查网络后重试") }
        finally { connection.disconnect() }
    }
}

object WanxiaoProtocol {
    data class BoundRoom(val verification: String, val name: String)

    fun body(text: String): JSONObject {
        val outer = runCatching { JSONObject(text) }.getOrElse { throw IllegalArgumentException("智能水电响应格式变化，尚未读取数据") }
        require(outer.optString("code_") == "0" && outer.optString("result_").equals("true", ignoreCase = true)) { "智能水电返回失败，请在官方 App 确认账号和绑定状态" }
        val raw = outer.opt("body")
        val body = if (raw is JSONObject) raw else runCatching { JSONObject(raw as String) }.getOrElse { throw IllegalArgumentException("智能水电业务数据无法解析") }
        require(body.optString("result") == "0") { "暂未读取到水电数据，请在完美校园确认绑定，或等待接口适配" }
        return body
    }

    fun boundRooms(body: JSONObject): List<BoundRoom> {
        val entries = when (body.optString("existflag")) {
            "0" -> return emptyList()
            "1" -> listOf(body)
            "2" -> (body.optJSONArray("roomlist") ?: throw IllegalArgumentException("房间列表格式变化")).let { array ->
                require(array.length() in 1..20) { "房间数量异常" }
                (0 until array.length()).map { array.getJSONObject(it) }
            }
            else -> throw IllegalArgumentException("房间绑定状态无法识别，请在官方 App 确认")
        }
        return entries.mapIndexed { index, row ->
            val verification = row.optString("roomverify").trim()
            require(verification.isNotBlank() && verification != "null") { "房间缺少查询凭据" }
            val name = listOf("roomfullname", "roomname", "roomName", "room_name", "room", "dormname", "dormName")
                .map { row.optString(it).trim() }.firstOrNull { it.isNotBlank() && it != "null" } ?: "绑定房间 ${index + 1}"
            BoundRoom(verification, name)
        }.distinctBy { it.verification }
    }

    fun devices(body: JSONObject): List<OnlineDevice> {
        val list = body.optJSONArray("modlist") ?: throw IllegalArgumentException("服务未返回设备列表")
        require(list.length() <= 50) { "表计数量异常" }
        fun value(row: JSONObject, key: String): String = if (row.isNull(key)) "—" else row.optString(key).trim().ifBlank { "—" }
        return (0 until list.length()).mapNotNull { index ->
            val row = list.optJSONObject(index) ?: return@mapNotNull null
            val type = row.optString("bussnesstype").ifBlank { row.optString("businesstype") }
            val category = when (type) { "0" -> "电"; "1" -> "水"; else -> return@mapNotNull null }
            // odd/todayuse/sumbuy may already contain units. Never infer yuan, kWh or m³.
            OnlineDevice(value(row, "devicename"), category, value(row, "odd"), value(row, "todayuse"), value(row, "sumbuy"), value(row, "status"))
        }
    }
}
