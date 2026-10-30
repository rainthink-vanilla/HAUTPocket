package cn.hautpocket.app.ui

import androidx.compose.runtime.Composable

@Composable
fun TextColorDialog(current: String, dismiss: () -> Unit, save: (String) -> Unit, preview: (String?) -> Unit) =
    ColorPickerDialog(current, false, dismiss, save, preview)
