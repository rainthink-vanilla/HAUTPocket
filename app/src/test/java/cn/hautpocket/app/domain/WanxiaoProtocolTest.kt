package cn.hautpocket.app.domain

import cn.hautpocket.app.data.WanxiaoProtocol
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WanxiaoProtocolTest {
    @Test fun supportsObjectAndStringBodies() {
        val objectBody = """{"code_":0,"result_":true,"body":{"result":0,"existflag":0}}"""
        val stringBody = JSONObject().put("code_", "0").put("result_", "true").put("body", """{"result":0,"existflag":0}""").toString()
        assertTrue(WanxiaoProtocol.boundRooms(WanxiaoProtocol.body(objectBody)).isEmpty())
        assertTrue(WanxiaoProtocol.boundRooms(WanxiaoProtocol.body(stringBody)).isEmpty())
    }
    @Test fun rejectsOuterOrBusinessFailures() {
        for (raw in listOf("<html>failure</html>", "{}", """{"code_":1,"result_":true,"body":{"result":0}}""",
            """{"code_":0,"result_":true,"body":{"result":1}}""")) {
            assertTrue(runCatching { WanxiaoProtocol.body(raw) }.isFailure)
        }
    }
    @Test fun readsOneAndMultipleRoomsWithoutGuessingCampus() {
        assertEquals("测试房间", WanxiaoProtocol.boundRooms(JSONObject("""{"existflag":1,"roomverify":"synthetic-verification","roomname":"测试房间"}""")).single().name)
        val rooms = WanxiaoProtocol.boundRooms(JSONObject("""{"existflag":2,"roomlist":[
            {"roomverify":"synthetic-a","roomfullname":"测试楼101"},
            {"roomverify":"synthetic-b","roomName":"测试楼202"},
            {"roomverify":"synthetic-a","roomname":"duplicate"}]}"""))
        assertEquals(2, rooms.size)
        assertEquals("测试楼202", rooms[1].name)
    }
    @Test fun missingVerificationIsNotReplacedWithAnInventedId() {
        assertTrue(runCatching { WanxiaoProtocol.boundRooms(JSONObject("""{"existflag":1,"roomname":"房间"}""")) }.isFailure)
    }
    @Test fun preservesServerUnitsAndBalancesWithoutInventingWaterOrAcMappings() {
        val devices = WanxiaoProtocol.devices(JSONObject("""{"modlist":[
            {"bussnesstype":"0","devicename":"测试电表","odd":"12.50 元","todayuse":"2.1 kWh","sumbuy":"100 元","status":"正常"},
            {"businesstype":"1","devicename":"测试水表","odd":"8m³"},
            {"bussnesstype":"99","devicename":"未知业务"}]}"""))
        assertEquals(2, devices.size)
        assertEquals("12.50 元", devices[0].balance)
        assertEquals("2.1 kWh", devices[0].todayUse)
        assertEquals("8m³", devices[1].balance)
        assertEquals("—", devices[1].todayUse)
    }
}
