package cn.hautpocket.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.hautpocket.app.OfficialWebActivity
import cn.hautpocket.app.data.AccountVault
import cn.hautpocket.app.data.WanxiaoClient
import cn.hautpocket.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

private val weekNames = listOf("一", "二", "三", "四", "五", "六", "日")
private fun stamp(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
private fun amount(value: Double) = "%.2f".format(value)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PocketApp(state: PocketState, save: (PocketState) -> Unit, warning: String?) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var meterSelected by remember { mutableStateOf<Meter?>(null) }
    var editingMeter by remember { mutableStateOf<Meter?>(null) }
    var parsedImport by remember { mutableStateOf<ScheduleParser.Result?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val currentSave by rememberUpdatedState(save)
    val currentState by rememberUpdatedState(state)
    val scope = rememberCoroutineScope()
    val vault = remember { AccountVault(context) }
    var lastQueryAccount by remember { mutableStateOf(vault.read().orEmpty()) }
    var querying by remember { mutableStateOf(false) }
    var queryError by remember { mutableStateOf<String?>(null) }
    var queryJob by remember { mutableStateOf<Job?>(null) }
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
    val week = semesterWeek(state.semesterStart, today, state.totalWeeks)
    var viewedWeek by rememberSaveable { mutableIntStateOf(week ?: 1) }
    var viewedDay by rememberSaveable { mutableIntStateOf(today.dayOfWeek.value) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            val latest = currentState
            if (latest.focusEndsAt > 0 && now >= latest.focusEndsAt) {
                currentSave(latest.copy(focusEndsAt = 0, focusSessions = latest.focusSessions + FocusSession(latest.focusEndsAt, latest.focusMinutes)))
            }
            delay(1000)
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= 2_000_000) {
                    val read = stream.read(buffer, 0, minOf(buffer.size, 2_000_001 - output.size()))
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
                val bytes = output.toByteArray()
                require(bytes.size <= 2_000_000) { "文件超过 2 MB" }
                parsedImport = ScheduleParser.parse(bytes.toString(Charsets.UTF_8), state.campus)
            } ?: error("无法打开文件")
        }.onFailure { Toast.makeText(context, it.message ?: "导入失败，原课表已保留", Toast.LENGTH_LONG).show() }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(TimetableExport.json(currentState, System.currentTimeMillis()))
            } ?: error("无法写入文件")
        }.onSuccess { Toast.makeText(context, "已导出，可在大学课表中导入", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "课表导出失败，请重试", Toast.LENGTH_LONG).show() }
    }
    fun open(title: String, url: String, allowImport: Boolean = false) = OfficialWebActivity.open(context, title, url, allowImport)
    fun wanxiao() {
        val launch = context.packageManager.getLaunchIntentForPackage("com.newcapec.mobile.ncp")
        if (launch != null) runCatching { context.startActivity(launch) }.onFailure { dialog = "wanxiao" }
        else dialog = "wanxiao"
    }
    fun queryWaterElectricity(account: String) {
        if (querying) return
        if (lastQueryAccount != account) {
            currentSave(currentState.copy(onlineRooms = emptyList(), onlineRoomCampuses = emptyMap()))
        }
        lastQueryAccount = account
        querying = true
        queryError = null
        queryJob = scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    vault.save(account)
                    WanxiaoClient().queryOwnBoundRooms(account)
                }
                currentSave(currentState.copy(onlineRooms = result))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { queryError = e.message?.take(160) ?: "查询失败，请在完美校园确认绑定状态。" }
            finally { querying = false }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(10.dp)) {
                Text("工", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column { Text("口袋工大", fontWeight = FontWeight.Bold); Text("H A U T  P O C K E T", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } }, actions = { IconButton(onClick = { dialog = "settings" }) { Icon(Icons.Outlined.Tune, "设置") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        bottomBar = { NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            listOf("今天" to Icons.Outlined.Home, "课表" to Icons.Outlined.CalendarMonth, "宿舍" to Icons.Outlined.Bolt, "校园" to Icons.Outlined.GridView).forEachIndexed { index, (name, icon) ->
                NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = { Icon(icon, name) }, label = { Text(name) })
            }
        } },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp, 6.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                Campus.entries.forEach { campus -> FilterChip(selected = state.campus == campus, onClick = { save(state.copy(campus = campus)) }, label = { Text(campus.label) }) }
                Spacer(Modifier.weight(1f))
                Text("河南工业大学", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            if (warning != null) item { InfoCard(warning, true) }
            when (tab) {
                0 -> {
                    item { Column {
                        Text(today.format(DateTimeFormatter.ofPattern("M 月 d 日")) + " · 星期${weekNames[today.dayOfWeek.value - 1]}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("把校园日常，装进口袋。", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    } }
                    item {
                        val dayCourses = week?.let { coursesOn(state.courses, today.dayOfWeek.value, it) }.orEmpty()
                        val time = Instant.ofEpochMilli(now).atZone(ZoneId.of("Asia/Shanghai")).toLocalTime()
                        val next = dayCourses.firstOrNull { course ->
                            val end = state.sectionTimes.find { it.section == course.sections.maxOrNull() }?.end
                            end == null || LocalTime.parse(end).isAfter(time)
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.AutoStories, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp)); Text(if (week == null) "我的课表" else "第 $week 周 · 今日课程", style = MaterialTheme.typography.labelLarge)
                                }
                                Text(when { state.courses.isEmpty() -> "从第一张课表开始"; week == null -> "设置学期，安排每一天"; next != null -> next.name; else -> "今天的课程已结束" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(when { state.courses.isEmpty() -> "登录学校教务页面，导入后离线也能看。"; week == null -> "请核对学期起始周一和教学周数。"; next != null -> "${sectionSummary(next.sections)} · ${next.location.ifBlank { "未提供教室" }}\n${next.campus.label}校区"; else -> "留一点时间，做自己想做的事。" }, style = MaterialTheme.typography.bodyMedium)
                                Button(onClick = { if (state.courses.isEmpty()) dialog = "import" else if (week == null) dialog = "settings" else tab = 1 }) {
                                    Text(if (state.courses.isEmpty()) "导入课表" else if (week == null) "设置学期" else "查看今日课表")
                                }
                            }
                        }
                    }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickCard("宿舍用量", "${state.campus.label} · 查询与记录", Icons.Outlined.Bolt, Modifier.weight(1f)) { tab = 2 }
                        QuickCard("校园卡", "前往完美校园", Icons.Outlined.CreditCard, Modifier.weight(1f)) { wanxiao() }
                    } }
                    item { SectionTitle("留一段专注时间", "FOCUS") }
                    item { FocusCard(state, now, { minutes -> save(state.copy(focusMinutes = minutes, focusEndsAt = now + minutes * 60_000L)) }, { dialog = "stopFocus" }) }
                    item { ServiceRow("云上工大", "课表、办事与图书馆服务", Icons.Outlined.CloudQueue) { open("云上工大 · 官方网页", OfficialServices.PORTAL) } }
                }
                1 -> {
                    item { SectionTitle("我的课表", if (state.scheduleImportedAt == 0L) "离线保存" else "更新于 ${stamp(state.scheduleImportedAt)}") }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { dialog = "import" }) { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("导入课表") }
                        TextButton(onClick = { exportFile.launch("HAUT-timetable.json") }, enabled = state.courses.isNotEmpty()) { Text("导出") }
                        TextButton(onClick = { dialog = "settings" }) { Text("作息") }
                    } }
                    item { Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { viewedWeek = (viewedWeek - 1).coerceAtLeast(1) }, enabled = viewedWeek > 1) { Text("上一周") }
                        Text("第 $viewedWeek 周", Modifier.weight(1f).wrapContentWidth(), fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { viewedWeek = (viewedWeek + 1).coerceAtMost(state.totalWeeks) }, enabled = viewedWeek < state.totalWeeks) { Text("下一周") }
                    } }
                    item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        weekNames.forEachIndexed { i, name -> FilterChip(selected = viewedDay == i + 1, onClick = { viewedDay = i + 1 }, label = { Text("周$name") }) }
                    } }
                    if (state.semesterStart.isBlank()) item { InfoCard("尚未设置学期起始日。当前为手动选周预览。") }
                    if (state.courses.isEmpty()) item { EmptyCard("还没有课表", "可从官方教务网页导入，也可导入大学课表 .md/.json 文件。", "导入课表") { dialog = "import" } }
                    else {
                        val list = coursesOn(state.courses, viewedDay, viewedWeek)
                        if (list.isEmpty()) item { EmptyCard("这一天没有已导入课程", "第 $viewedWeek 周 · 星期${weekNames[viewedDay - 1]}", "回到本周") { viewedWeek = week ?: 1; viewedDay = today.dayOfWeek.value } }
                        list.forEach { c -> item(key = c.id) { CourseCard(c, state.sectionTimes) } }
                    }
                    item { Text("各课程保留所在校区，跨校区课程也会显示。时间未配置时只显示节次。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                2 -> {
                    item { SectionTitle("宿舍与用量", "${state.campus.label}校区") }
                    item { InfoCard("智能水电接口已加入，河南工大账户尚待真机验证。查询只读取本人已绑定房间；首次查询后请选择每个房间所属校区。") }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (lastQueryAccount.isBlank()) dialog = "waterAccount" else queryWaterElectricity(lastQueryAccount) }, enabled = !querying) {
                            if (querying) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            else Icon(Icons.Outlined.Sync, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text(if (querying) "正在查询…" else "查询智能水电")
                        }
                        TextButton(onClick = { dialog = "waterAccount" }, enabled = !querying) { Text("账号") }
                    } }
                    if (querying) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    queryError?.let { message -> item { InfoCard(message + if (state.onlineRooms.isNotEmpty()) "\n下方保留上次查询结果，请核对更新时间。" else "", true) } }
                    state.onlineRooms.filter { room -> state.onlineRoomCampuses[room.id] == null || state.onlineRoomCampuses[room.id] == state.campus }.forEach { room ->
                        item(key = "online-${room.id}") { OnlineRoomCard(room, state.onlineRoomCampuses[room.id]) { campus -> save(state.copy(onlineRoomCampuses = state.onlineRoomCampuses + (room.id to campus))) } }
                    }
                    if (state.onlineRooms.isNotEmpty() && state.onlineRooms.none { state.onlineRoomCampuses[it.id] == null || state.onlineRoomCampuses[it.id] == state.campus }) item {
                        InfoCard("当前校区没有已归属的在线房间。可切换校区查看；手工记录在下方。")
                    }
                    item { SectionTitle("我的手工记录", "与在线读数分开") }
                    item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { editingMeter = null; dialog = "meter" }) { Icon(Icons.Outlined.Add, null, Modifier.size(18.dp)); Text("添加房间表计") }
                        OutlinedButton(onClick = { wanxiao() }) { Text("完美校园") }
                    } }
                    val meters = state.meters.filter { it.campus == state.campus }
                    if (meters.isEmpty()) item { EmptyCard("还没有配置房间", "按官方服务里的楼栋、房间和表计类型填写。两个校区的配置独立保存。", "添加房间") { editingMeter = null; dialog = "meter" } }
                    meters.forEach { meter -> item(key = meter.id) {
                        MeterCard(meter, state.readings.filter { it.meterId == meter.id },
                            onRecord = { meterSelected = meter; dialog = "reading" },
                            onDetails = { meterSelected = meter; dialog = "history" },
                            onEdit = { editingMeter = meter; dialog = "meter" })
                    } }
                    item { InfoCard("房间标签和服务端房间 ID 含义不同。不要按其他学校的编号规则推算；空调、水表只在官方确有独立表计时添加。") }
                }
                else -> {
                    item { SectionTitle("校园服务", "常用入口") }
                    item { Text("常用的校园服务，在这里找到。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { ServiceRow("云上工大", "统一认证 · 一网通办 · 场馆预约", Icons.Outlined.CloudQueue) { open("云上工大 · 官方网页", OfficialServices.PORTAL) } }
                    item { ServiceRow("教务系统", "正方教务 · 课程与成绩", Icons.Outlined.School) { open("教务系统", OfficialServices.TEACHING, true) } }
                    item { ServiceRow("完美校园", "校园卡 · 校内缴费 · 宿舍服务", Icons.Outlined.CreditCard) { wanxiao() } }
                    item { ServiceRow("图书馆", "馆藏与公告 · 座位预约请进入云上工大", Icons.Outlined.LocalLibrary) { open("河南工业大学图书馆", OfficialServices.LIBRARY) } }
                    item { SectionTitle("关于与帮助", "") }
                    item { ServiceRow("使用与接入状态", "哪些已可用，哪些仍需验证", Icons.Outlined.Info) { dialog = "about" } }
                    item { ServiceRow("学校使用指南", "云上工大官方安装与使用说明", Icons.AutoMirrored.Outlined.HelpOutline) { open("学校使用指南", OfficialServices.HELP) } }
                    item { ServiceRow("登录会话", "清除本应用保存的学校网页会话", Icons.Outlined.Lock) { dialog = "logout" } }
                }
            }
            item { Text("口袋工大 · 非官方校园助手", Modifier.fillMaxWidth().wrapContentWidth(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    } }

    when (dialog) {
        "settings" -> SettingsDialog(state, { dialog = null }) { updated -> save(updated); viewedWeek = semesterWeek(updated.semesterStart, today, updated.totalWeeks) ?: 1; dialog = null }
        "import" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("导入我的课表") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("两种方式都保存为本地课表，可离线查看。")
            Button(onClick = { dialog = null; open("教务课表导入", OfficialServices.TEACHING, true) }, modifier = Modifier.fillMaxWidth()) { Text("从教务网页导入") }
            OutlinedButton(onClick = { dialog = null; importFile.launch(arrayOf("application/json", "text/plain", "text/markdown", "text/x-markdown", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("从大学课表文件导入（MD / JSON）") }
            Text("支持大学课表导出的文件、agent 按模板生成的文件，以及正方 JSON。可选替换、更新合并或追加；应用内暂不提供 AI 截图识别。", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } })
        "meter" -> MeterDialog(state.campus, editingMeter, state, { dialog = null }) { meter ->
            save(state.copy(meters = state.meters.filterNot { it.id == meter.id } + meter)); dialog = null
        }
        "reading" -> meterSelected?.let { meter -> ReadingDialog(meter, { dialog = null }) { reading -> save(state.copy(readings = state.readings + reading)); dialog = null } }
        "history" -> meterSelected?.let { meter -> HistoryDialog(meter, state.readings.filter { it.meterId == meter.id }, { dialog = null }) { reading -> save(state.copy(readings = removeReading(state.readings, reading.id))) } }
        "waterAccount" -> WaterAccountDialog(lastQueryAccount, { dialog = null }, {
            queryJob?.cancel()
            runCatching { vault.clear() }.onSuccess {
                lastQueryAccount = ""; queryError = null
                save(state.copy(onlineRooms = emptyList(), onlineRoomCampuses = emptyMap())); dialog = null
            }.onFailure { Toast.makeText(context, "账号清除失败，请重试", Toast.LENGTH_SHORT).show() }
        }) { account -> dialog = null; queryWaterElectricity(account) }
        "stopFocus" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("结束本次专注？") }, text = { Text("未完成的专注不会计入已完成时长。") }, confirmButton = { TextButton(onClick = { save(state.copy(focusEndsAt = 0)); dialog = null }) { Text("结束") } }, dismissButton = { TextButton(onClick = { dialog = null }) { Text("继续专注") } })
        "wanxiao" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("前往完美校园") }, text = { Text("没有找到可直接打开的完美校园 App。请在手机应用商店安装，绑定河南工业大学校园卡后使用。\n\n水电入口会因校区而异，自动读取仍待核实。") }, confirmButton = { TextButton(onClick = { dialog = null; open("一卡通 · 学校说明", OfficialServices.CARD_HELP) }) { Text("查看学校说明") } }, dismissButton = { TextButton(onClick = { dialog = null }) { Text("关闭") } })
        "about" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("口袋工大 0.2.0") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("面向莲花街、嵩山路两个校区的非官方校园助手。")
            Text("已实现\n• 课表 JSON 导入、周次/节次解析和离线展示\n• 学校网页登录与课表导入流程\n• 智能水电原生查询客户端、已绑房间与表计展示\n• 双校区房间归属与独立手工读数\n• 本地专注计时与完成记录")
            Text("尚待联调\n• 教务账号真机导入\n• 智能水电接口在河南工大账户上的有效性\n• 两校区各自的房间名称、表计类型与单位")
            Text("读数标注手工来源，不作实时余额。专注计时不提供后台通知。登录仅在官方网页完成，会话由本应用 WebView 保存；可在校园页清除。")
            Text("设计与 Gradle Wrapper 参考 ZZUPocket（MIT）。不代表河南工业大学或完美校园官方。")
        } }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("知道了") } })
        "logout" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("清除学校网页会话？") }, text = { Text("将退出本应用内学校网页的登录状态。课表、房间和读数会保留。") }, confirmButton = { TextButton(onClick = {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.CookieManager.getInstance().flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
            dialog = null
            Toast.makeText(context, "网页登录会话已清除", Toast.LENGTH_SHORT).show()
        }) { Text("清除会话") } }, dismissButton = { TextButton(onClick = { dialog = null }) { Text("取消") } })
    }
    parsedImport?.let { result -> ScheduleImportDialog(result, state.courses.isNotEmpty(), { parsedImport = null }) { mode, calendar ->
        runCatching {
            val updated = ScheduleImport.apply(state, result, mode, calendar, System.currentTimeMillis())
            save(updated); viewedWeek = semesterWeek(updated.semesterStart, today, updated.totalWeeks) ?: 1
            parsedImport = null; tab = 1
        }.onFailure { Toast.makeText(context, it.message ?: "导入失败", Toast.LENGTH_LONG).show() }
    } }
}

@Composable
private fun OnlineRoomCard(room: OnlineRoom, campus: Campus?, assign: (Campus) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Apartment, null, tint = MaterialTheme.colorScheme.primary)
                Text(room.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            }
            Text(if (campus == null) "请确认这个房间所属校区" else "${campus.label}校区 · 完美校园智能水电", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Campus.entries.forEach { value -> FilterChip(selected = campus == value, onClick = { assign(value) }, label = { Text(value.label) }) }
            }
            if (room.failed) Text("这个房间查询失败，请稍后刷新。", color = MaterialTheme.colorScheme.error)
            else if (room.devices.isEmpty()) Text("服务未返回可识别的水电表计，请在完美校园核对。")
            room.devices.forEach { device ->
                HorizontalDivider()
                Text("${device.category} · ${device.name}", fontWeight = FontWeight.SemiBold)
                Text(device.balance, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("今日用量 ${device.todayUse} · 累计购入 ${device.totalBought}", style = MaterialTheme.typography.bodySmall)
                Text("状态 ${device.status}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!room.failed) Text("查询于 ${stamp(room.fetchedAt)} · 数值与单位按服务原文展示", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WaterAccountDialog(initial: String, close: () -> Unit, forget: () -> Unit, query: (String) -> Unit) {
    var account by remember { mutableStateOf(initial) }
    var confirmed by remember { mutableStateOf(false) }
    val valid = account.trim().matches(Regex("[A-Za-z0-9_-]{4,32}"))
    AlertDialog(onDismissRequest = close, title = { Text("连接智能水电") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("请先在完美校园“智能水电”绑定宿舍，然后填写本人的校园卡账号。通常为学号，请保留前导 0。")
        OutlinedTextField(account, { account = it }, label = { Text("本人校园卡账号") }, singleLine = true, supportingText = { Text("账号仅发往完美校园服务，在本机加密保存") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
            Text("这是我的账号，查询我已绑定的房间", style = MaterialTheme.typography.bodySmall)
        }
        if (initial.isNotBlank()) TextButton(onClick = forget) { Text("移除账号及在线缓存") }
        Text("当前版本尚未用河南工大真实账户验证。若查询失败，继续使用完美校园，手工记录仍可用。", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton(onClick = { query(account.trim()) }, enabled = valid && confirmed) { Text("查询已绑房间") } }, dismissButton = { TextButton(onClick = close) { Text("取消") } })
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoCard(message: String, error: Boolean = false) {
    Surface(shape = RoundedCornerShape(14.dp), color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Icon(Icons.Outlined.Info, null, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String, action: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(action) }
        }
    }
}

@Composable
private fun QuickCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ServiceRow(title: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) { Icon(icon, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CourseCard(course: Course, times: List<SectionTime>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(4.dp).height(78.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary))
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(course.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val selectedTimes = course.sections.mapNotNull { section -> times.find { it.section == section } }
                Text(sectionSummary(course.sections) + if (selectedTimes.size == course.sections.size) " · " + selectedTimes.joinToString(" / ") { "${it.start}–${it.end}" } else "", style = MaterialTheme.typography.bodySmall)
                Text("${course.location.ifBlank { "未提供教室" }} · ${course.campus.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (course.teacher.isNotBlank()) Text(course.teacher, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (course.note.isNotBlank()) Text(course.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MeterCard(meter: Meter, readings: List<Reading>, onRecord: () -> Unit, onDetails: () -> Unit, onEdit: () -> Unit) {
    val sorted = readings.sortedBy { it.timestamp }
    val latest = sorted.lastOrNull()
    val usage = if (sorted.size > 1) consumption(sorted[sorted.lastIndex - 1], sorted.last()) else null
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (meter.kind == MeterKind.WATER) Icons.Outlined.WaterDrop else Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(7.dp)); Text(meter.kind.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("编辑") }
            }
            Text(meter.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(latest?.remaining?.let(::amount) ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                Text(meter.kind.unit, Modifier.padding(bottom = 5.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (latest == null) "尚无读数" else "手工记录 · ${stamp(latest.timestamp)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (usage != null) Text("上次至本次使用 ${amount(usage)} ${meter.kind.unit}（已计入所填充值量）", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onRecord) { Text("记录读数") }
                TextButton(onClick = onDetails) { Text("历史记录") }
            }
        }
    }
}

@Composable
private fun FocusCard(state: PocketState, now: Long, start: (Int) -> Unit, stop: () -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    val seconds = ceil(((state.focusEndsAt - now).coerceAtLeast(0)) / 1000.0).toLong()
    val running = state.focusEndsAt > 0
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (running) "%02d:%02d".format(seconds / 60, seconds % 60) else "$minutes:00", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("已完成 ${state.focusSessions.sumOf { it.minutes }} 分钟专注", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { if (running) stop() else start(minutes) }) { Text(if (running) "结束" else "开始专注") }
            }
            if (!running) Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(15, 25, 45).forEach { value -> FilterChip(selected = value == minutes, onClick = { minutes = value }, label = { Text("$value 分钟") }) }
            }
            Text("切回应用时恢复计时；暂不支持后台到点通知。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsDialog(state: PocketState, close: () -> Unit, save: (PocketState) -> Unit) {
    var start by remember { mutableStateOf(state.semesterStart) }
    var weeks by remember { mutableStateOf(state.totalWeeks.toString()) }
    var times by remember { mutableStateOf(state.sectionTimes.joinToString("\n") { "${it.section} ${it.start} ${it.end}" }) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text("学期与作息") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("以学校最新校历与作息为准。留空时不推算当前教学周或课程时间。", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(start, { start = it }, label = { Text("第一教学周的周一") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true)
        OutlinedTextField(weeks, { weeks = it }, label = { Text("教学周数（1–60）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(times, { times = it }, label = { Text("节次时间，每行一节") }, placeholder = { Text("1 08:30 09:15\n2 09:20 10:05") }, minLines = 4, maxLines = 7)
        Text("时间格式示例仅说明填写方式，不代表学校当前作息。", style = MaterialTheme.typography.labelSmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = {
        runCatching {
            if (start.isNotBlank()) require(LocalDate.parse(start.trim()).dayOfWeek.value == 1) { "请填写第一教学周的周一日期" }
            val count = weeks.toIntOrNull() ?: error("周数应为整数")
            require(count in 1..60) { "周数应为 1–60" }
            val parsed = times.lines().filter { it.isNotBlank() }.map { line ->
                val fields = line.trim().split(Regex("\\s+"))
                require(fields.size == 3) { "每行格式：节次 开始时间 结束时间" }
                SectionTime(fields[0].toInt(), fields[1], fields[2])
            }
            require(parsed.distinctBy { it.section }.size == parsed.size) { "节次不可重复" }
            val ordered = parsed.sortedBy { it.section }
            require(ordered.zipWithNext().all { (a, b) -> !LocalTime.parse(a.end).isAfter(LocalTime.parse(b.start)) }) { "节次时间不能重叠或倒序" }
            save(state.copy(semesterStart = start.trim(), totalWeeks = count, sectionTimes = ordered))
        }.onFailure { error = it.message ?: "设置格式有误" }
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = close) { Text("取消") } })
}

@Composable
private fun MeterDialog(campus: Campus, existing: Meter?, state: PocketState, close: () -> Unit, save: (Meter) -> Unit) {
    var building by remember { mutableStateOf(existing?.building.orEmpty()) }
    var room by remember { mutableStateOf(existing?.room.orEmpty()) }
    var providerId by remember { mutableStateOf(existing?.providerRoomId.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: MeterKind.ELECTRICITY) }
    var error by remember { mutableStateOf<String?>(null) }
    val hasReadings = existing != null && state.readings.any { it.meterId == existing.id }
    AlertDialog(onDismissRequest = close, title = { Text("${existing?.campus?.label ?: campus.label} · 房间表计") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(building, { building = it }, label = { Text("楼栋（按官方名称填写）") }, singleLine = true)
        OutlinedTextField(room, { room = it }, label = { Text("房间标签") }, singleLine = true)
        OutlinedTextField(providerId, { providerId = it }, label = { Text("服务端房间 ID（可暂留空）") }, singleLine = true)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { MeterKind.entries.forEach { type ->
            FilterChip(selected = kind == type, onClick = { kind = type }, enabled = !hasReadings, label = { Text(type.label) })
        } }
        Text(if (hasReadings) "已有读数的表计不能更改计量类型。" else "只添加学校服务中确实存在的表计。不确定是否独立计费时，请先核实。", style = MaterialTheme.typography.bodySmall)
        if (existing != null) Text("编辑用于修正标签；更换宿舍请新建表计，避免混合历史读数。", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = {
        runCatching {
            val meter = Meter(id = existing?.id ?: java.util.UUID.randomUUID().toString(), campus = existing?.campus ?: campus,
                building = building.trim(), room = room.trim(), providerRoomId = providerId.trim(), kind = kind)
            require(state.meters.none { it.id != meter.id && it.campus == meter.campus && it.building == meter.building && it.room == meter.room && it.kind == meter.kind }) { "这个房间的同类表计已存在" }
            save(meter)
        }.onFailure { error = it.message ?: "请检查填写内容" }
    }) { Text("保存") } }, dismissButton = { TextButton(onClick = close) { Text("取消") } })
}

@Composable
private fun ReadingDialog(meter: Meter, close: () -> Unit, save: (Reading) -> Unit) {
    var remaining by remember { mutableStateOf("") }
    var added by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text("记录${meter.kind.label}") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${meter.campus.label} · ${meter.label}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(remaining, { remaining = it }, label = { Text("当前剩余量（${meter.kind.unit}）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        OutlinedTextField(added, { added = it }, label = { Text("自上次记录后充值量（${meter.kind.unit}）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        Text("确定未充值请填 0；不清楚请留空，此时不计算区间用量。这里填写充值的量，不是金额。", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, maxLines = 3)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = {
        runCatching {
            val value = remaining.toDoubleOrNull() ?: error("请填写有效剩余量")
            val topUp = if (added.isBlank()) null else added.toDoubleOrNull() ?: error("充值量格式有误")
            save(Reading(meterId = meter.id, timestamp = System.currentTimeMillis(), remaining = value, addedSincePrevious = topUp,
                note = "手工抄录自官方服务" + note.trim().takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()))
        }.onFailure { error = it.message ?: "读数格式有误" }
    }) { Text("保存读数") } }, dismissButton = { TextButton(onClick = close) { Text("取消") } })
}

@Composable
private fun HistoryDialog(meter: Meter, readings: List<Reading>, close: () -> Unit, remove: (Reading) -> Unit) {
    val ordered = readings.sortedBy { it.timestamp }
    var removal by remember { mutableStateOf<Reading?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text("${meter.label} · 历史") }, text = { Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (ordered.isEmpty()) Text("还没有记录。")
        ordered.withIndex().toList().asReversed().forEach { (index, reading) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${amount(reading.remaining)} ${meter.kind.unit} · ${stamp(reading.timestamp)}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { removal = reading }) { Icon(Icons.Outlined.DeleteOutline, "删除这条手工读数") }
                }
                Text(reading.note, style = MaterialTheme.typography.bodySmall)
                if (index > 0) {
                    val usage = consumption(ordered[index - 1], reading)
                    Text(if (usage == null) "无法计算区间用量：充值量未知或读数不一致" else "区间使用 ${amount(usage)} ${meter.kind.unit}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
        }
    } }, confirmButton = { TextButton(onClick = close) { Text("关闭") } })
    removal?.let { reading -> AlertDialog(onDismissRequest = { removal = null }, title = { Text("删除这条读数？") },
        text = { Text("删除后，下一条记录的期间充值量将标为未知，避免把不同区间误算为用量。") },
        confirmButton = { TextButton(onClick = { remove(reading); removal = null }) { Text("删除") } },
        dismissButton = { TextButton(onClick = { removal = null }) { Text("取消") } }) }
}
