package cn.hautpocket.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.hautpocket.app.domain.*
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(snapshot: CatalogSnapshot, query: String, updateQuery: (String) -> Unit, busy: Boolean,
    error: String?, search: (String) -> Unit, openBook: (CatalogBook) -> Unit, more: () -> Unit,
    back: () -> Unit, official: () -> Unit) {
    var expanded by remember(snapshot.title) { mutableStateOf(false) }
    var previewCover by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { TopAppBar(title = { Text(if (snapshot.kind == "detail") "书目详情" else "图书查询", fontWeight = FontWeight.Bold) },
        navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
        actions = { TextButton(onClick = official) { Text("完整检索") } }) }, containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (snapshot.kind != "detail") OutlinedTextField(value = query, onValueChange = updateQuery, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("输入书名、作者或关键词") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { TextButton(onClick = { search(query) }, enabled = !busy && query.isNotBlank()) { Text("查找") } },
                shape = MaterialTheme.shapes.large, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (!busy && query.isNotBlank()) search(query) }))
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                error?.let { item { Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Text(it, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                } } }
                when (snapshot.kind) {
                    "results" -> {
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("检索结果", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("已显示 ${snapshot.books.size} 条", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            TextButton(onClick = official) { Text("筛选与排序") }
                        } }
                        if (snapshot.books.isEmpty() && !busy) item { Text(if (snapshot.empty || snapshot.finished) "没有找到相关书目，试试其他关键词。" else "正在读取检索结果…") }
                        items(snapshot.books, key = { "${it.index}/${it.title}" }) { book ->
                            Card(onClick = { if (!busy) openBook(book) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CatalogCover(book.coverUrl, book.title, Modifier.size(68.dp, 92.dp)) { previewCover = book.coverUrl }
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (book.bibliography.isNotBlank()) Text(book.bibliography, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (book.resources.isNotBlank()) Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                                            Text(book.resources, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "查看详情", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (snapshot.books.isNotEmpty()) item {
                            if (snapshot.finished) Text("已显示当前检索的全部结果", style = MaterialTheme.typography.bodySmall)
                            else if (snapshot.books.size < 200) OutlinedButton(onClick = more, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
                            else TextButton(onClick = official) { Text("在完整检索中继续查看") }
                        }
                    }
                    "detail" -> {
                        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                CatalogCover(snapshot.coverUrl, snapshot.title, Modifier.size(90.dp, 120.dp)) { previewCover = snapshot.coverUrl }
                                Text(snapshot.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                Text(snapshot.bibliography, style = MaterialTheme.typography.bodyMedium)
                            }
                        } }
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("到哪里找这本书", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${snapshot.holdings.size} 份馆藏", style = MaterialTheme.typography.labelMedium)
                        } }
                        if (snapshot.holdings.isEmpty()) item { Text("暂未取得纸本馆藏信息，可在完整检索中查看其他资源。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(snapshot.holdings) { holding ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary)
                                        Text(holding.location.removePrefix("馆藏地/书架号：").trim().ifBlank { "馆藏位置未返回" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                        Surface(color = if (holding.status == "可借") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                                            Text(holding.status.ifBlank { "状态未返回" }, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                    holding.lines.filter { it.substringAfter('：', it).trim().isNotEmpty() && it.substringAfter(':', it).trim().isNotEmpty() }.forEachIndexed { index, line ->
                                        Text(line, style = if (index == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodySmall,
                                            color = if (index == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        item { OutlinedButton(onClick = official, modifier = Modifier.fillMaxWidth()) { Text("查看层架位置与完整馆藏") } }
                        if (snapshot.description.isNotBlank()) item {
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起书目信息" else "展开书目信息") }
                            if (expanded) Text(snapshot.description, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    "official" -> item { Card { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("请在学校页面继续", style = MaterialTheme.typography.titleMedium)
                        Text("此资源或登录流程需要完整检索页面。")
                        Button(onClick = official) { Text("打开完整检索") }
                    } } }
                    else -> {
                        item { PageHeading("查找馆藏", "一本好书，从这里开始。") }
                        item { FeatureBanner("你的随身书架入口", "输入书名、作者或关键词，查看馆藏位置与可借状态。", Icons.Outlined.LocalLibrary) }
                        if (snapshot.recommendations.isNotEmpty()) {
                            item { Text("热门检索", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                snapshot.recommendations.forEach { word -> SuggestionChip(onClick = { if (!busy) search(word) }, label = { Text(word) }) }
                            } }
                        }
                        item { ListItem(headlineContent = { Text("找到书后，直接看馆藏") }, supportingContent = { Text("结果中点开一本书，集中查看位置、索书号和借阅状态。") }, leadingContent = { Icon(Icons.Outlined.Place, null) }) }
                    }
                }
            }
        }
    }
    previewCover?.let { cover -> Dialog(onDismissRequest = { previewCover = null }) {
        Surface(shape = MaterialTheme.shapes.large) { Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(model = cover, contentDescription = "图书封面预览", contentScale = ContentScale.Fit,
                error = rememberVectorPainter(Icons.AutoMirrored.Outlined.MenuBook), modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 480.dp))
            TextButton(onClick = { previewCover = null }) { Text("关闭预览") }
        } }
    } }
}

@Composable
private fun CatalogCover(url: String, title: String, modifier: Modifier, preview: () -> Unit) {
    var available by remember(url) { mutableStateOf(false) }
    var failed by remember(url) { mutableStateOf(url.isBlank()) }
    Surface(onClick = preview, enabled = available, modifier = modifier, shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(model = url.ifBlank { null }, contentDescription = "$title，封面", contentScale = ContentScale.Fit,
                onLoading = { available = false; failed = false }, onSuccess = { available = true; failed = false },
                onError = { available = false; failed = true }, modifier = Modifier.fillMaxSize().padding(4.dp))
            if (!available) {
                if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("暂无封面", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                } else CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
    }
}
