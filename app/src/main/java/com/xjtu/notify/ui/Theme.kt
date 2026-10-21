package com.xjtu.notify.ui

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

val XjtuBlue = Color(0xFF1A56A0)
val XjtuBlueDark = Color(0xFF90B4E0)

val LightColors = lightColorScheme(
    primary = XjtuBlue, onPrimary = Color.White,
    secondary = Color(0xFF3E7C4F), tertiary = Color(0xFF8A5A00)
)
val DarkColors = darkColorScheme(primary = XjtuBlueDark)

val UiTypography = Typography(
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp)
)
