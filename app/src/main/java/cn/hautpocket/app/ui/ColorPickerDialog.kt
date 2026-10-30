package cn.hautpocket.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.hautpocket.app.domain.TextColors
import java.util.Locale

@Composable
fun ColorPickerDialog(current: String, theme: Boolean, dismiss: () -> Unit, save: (String) -> Unit, preview: (String?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val initial = remember { if (TextColors.validCustom(current)) current else hex(if (theme) scheme.primary else scheme.onSurface) }
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(initial), it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var saturation by remember { mutableFloatStateOf(hsv[1]) }
    var brightness by remember { mutableFloatStateOf(hsv[2]) }
    var value by remember { mutableStateOf(initial) }
    var fine by remember { mutableStateOf(false) }
    val currentPreview by rememberUpdatedState(preview)
    val valid = TextColors.validCustom(value)
    val color = if (valid) Color(android.graphics.Color.parseColor(value.trim())) else Color(android.graphics.Color.HSVToColor(floatArrayOf(hue,saturation,brightness)))
    val ink = if (color.luminance() > .179f) Color.Black else Color.White
    val contrast = TextColors.contrast(if (theme) hex(ink) else hex(color), if (theme) hex(color) else hex(scheme.surface))
    fun choose(h: Float, s: Float, b: Float) {
        hue = h.coerceIn(0f,360f); saturation = s.coerceIn(0f,1f); brightness = b.coerceIn(0f,1f)
        value = hex(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue,saturation,brightness))))
    }
    fun chooseHex(text: String) {
        value = text.take(7)
        if (TextColors.validCustom(value)) {
            val channels = FloatArray(3)
            android.graphics.Color.colorToHSV(android.graphics.Color.parseColor(value.trim()), channels)
            hue = channels[0]; saturation = channels[1]; brightness = channels[2]
        }
    }
    LaunchedEffect(value) { if (TextColors.validCustom(value)) currentPreview(TextColors.normalize(value)) }
    DisposableEffect(Unit) { onDispose { currentPreview(null) } }
    // Keep the controls readable even while previewing black-on-black or white-on-white.
    val dark = scheme.surface.luminance() < .5f
    MaterialTheme(colorScheme = scheme.copy(primary = if (dark) Color(0xFF9CCBFF) else Color(0xFF285F99),
        onSurface = if (dark) Color(0xFFE8EDF2) else Color(0xFF18212B), onSurfaceVariant = if (dark) Color(0xFFBFC9D2) else Color(0xFF52606D))) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (theme) "主题调色盘" else "文字调色盘") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = if (theme) color else scheme.surface, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("实时预览 · 你好，工大", color = if (theme) ink else color, style = MaterialTheme.typography.titleMedium)
                    Text("书名、馆藏位置与用电详情\nAa 0123456789", color = if (theme) ink else color, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Canvas(Modifier.fillMaxWidth().height(150.dp).clip(MaterialTheme.shapes.medium)
                .semantics { contentDescription = "颜色调色盘，可拖动选择；也可展开精细调节使用滑块" }
                .pointerInput(hue) { detectTapGestures { choose(hue, it.x / size.width, 1 - it.y / size.height) } }
                .pointerInput(hue) { detectDragGestures(onDragStart = { choose(hue,it.x / size.width,1 - it.y / size.height) }) { change, _ ->
                    change.consume(); choose(hue,change.position.x / size.width,1 - change.position.y / size.height)
                } }) {
                val pure = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue,1f,1f)))
                drawRect(Brush.horizontalGradient(listOf(Color.White,pure)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)))
                val position = Offset(saturation * size.width,(1 - brightness) * size.height)
                drawCircle(Color.Black,10.dp.toPx(),position,style = Stroke(2.dp.toPx()))
                drawCircle(Color.White,8.dp.toPx(),position,style = Stroke(2.dp.toPx()))
            }
            Text("色相", style = MaterialTheme.typography.labelMedium)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)) {
                    drawRect(Brush.horizontalGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)))
                }
                Slider(hue, { choose(it,saturation,brightness) }, valueRange = 0f..360f, modifier = Modifier.semantics { contentDescription = "色相" },
                    colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent))
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("#FFFFFF","#18212B","#2474E5","#16836B","#E69531","#D75066","#8861C2","#94C5ED").forEach { swatch ->
                    val item = Color(android.graphics.Color.parseColor(swatch))
                    Surface(onClick = { chooseHex(swatch) }, color = item, shape = CircleShape,
                        border = BorderStroke(1.dp,scheme.outlineVariant), modifier = Modifier.size(40.dp).semantics { contentDescription = "选择颜色 $swatch" }) {
                        if (value.equals(swatch,true)) Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Check,null,tint = if (item.luminance() > .179f) Color.Black else Color.White) }
                    }
                }
            }
            OutlinedTextField(value, ::chooseHex, singleLine = true, label = { Text("HEX 颜色值") }, isError = !valid, modifier = Modifier.fillMaxWidth())
            Text("预览对比度 ${String.format(Locale.ROOT,"%.1f",contrast)}:1${if (contrast < 4.5) " · 建议换成更清晰的颜色" else ""}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { fine = !fine }) { Text(if (fine) "收起精细调节" else "精细调节") }
            if (fine) {
                Text("饱和度",style = MaterialTheme.typography.labelMedium)
                Slider(saturation,{ choose(hue,it,brightness) },modifier = Modifier.semantics { contentDescription = "饱和度" })
                Text("明亮度",style = MaterialTheme.typography.labelMedium)
                Slider(brightness,{ choose(hue,saturation,it) },modifier = Modifier.semantics { contentDescription = "明亮度" })
            }
        }
    }, confirmButton = { TextButton(onClick = { save(TextColors.normalize(value)) },enabled = valid) { Text("应用") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
    }
}

private fun hex(color: Color) = "#%06X".format(Locale.ROOT,color.toArgb() and 0xFFFFFF)
