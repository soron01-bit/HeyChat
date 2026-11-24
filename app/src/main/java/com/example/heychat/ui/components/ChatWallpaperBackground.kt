package com.example.heychat.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

enum class ChatWallpaperTheme(
    val title: String,
    val backgroundColorLight: Color,
    val backgroundColorDark: Color,
    val patternColorLight: Color,
    val patternColorDark: Color
) {
    EMERALD(
        title = "Emerald Doodle",
        backgroundColorLight = Color(0xFFE5F3ED),
        backgroundColorDark = Color(0xFF0B141A),
        patternColorLight = Color(0xFF059669).copy(alpha = 0.08f),
        patternColorDark = Color(0xFF8696A0).copy(alpha = 0.06f)
    ),
    SAPPHIRE(
        title = "Midnight Sapphire",
        backgroundColorLight = Color(0xFFE0F2FE),
        backgroundColorDark = Color(0xFF030712),
        patternColorLight = Color(0xFF0284C7).copy(alpha = 0.08f),
        patternColorDark = Color(0xFF38BDF8).copy(alpha = 0.06f)
    ),
    SUNSET(
        title = "Sunset Coral",
        backgroundColorLight = Color(0xFFFFF1F2),
        backgroundColorDark = Color(0xFF18181B),
        patternColorLight = Color(0xFFE11D48).copy(alpha = 0.08f),
        patternColorDark = Color(0xFFFB7185).copy(alpha = 0.06f)
    ),
    OLED(
        title = "Pure OLED Dark",
        backgroundColorLight = Color(0xFFF8FAFC),
        backgroundColorDark = Color(0xFF000000),
        patternColorLight = Color(0xFF64748B).copy(alpha = 0.08f),
        patternColorDark = Color(0xFF334155).copy(alpha = 0.08f)
    )
}

@Composable
fun ChatWallpaperBackground(
    wallpaperTheme: ChatWallpaperTheme = ChatWallpaperTheme.EMERALD,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val bgColor = if (isDarkTheme) wallpaperTheme.backgroundColorDark else wallpaperTheme.backgroundColorLight
    val patternColor = if (isDarkTheme) wallpaperTheme.patternColorDark else wallpaperTheme.patternColorLight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDoodlePattern(patternColor)
        }
        content()
    }
}

private fun DrawScope.drawDoodlePattern(color: Color) {
    val tileSize = 160f
    val strokeWidth = 1.8f

    val columns = (size.width / tileSize).toInt() + 2
    val rows = (size.height / tileSize).toInt() + 2

    for (c in 0..columns) {
        for (r in 0..rows) {
            val startX = c * tileSize
            val startY = r * tileSize

            val patternType = (c + r * 3) % 4
            when (patternType) {
                0 -> {
                    // Chat Bubble Icon
                    val path = Path().apply {
                        moveTo(startX + 20f, startY + 20f)
                        lineTo(startX + 60f, startY + 20f)
                        lineTo(startX + 60f, startY + 45f)
                        lineTo(startX + 35f, startY + 45f)
                        lineTo(startX + 20f, startY + 55f)
                        close()
                    }
                    drawPath(path, color = color, style = Stroke(strokeWidth))

                    // Heart Icon
                    drawCircle(color = color, radius = 10f, center = Offset(startX + 100f, startY + 100f), style = Stroke(strokeWidth))
                }
                1 -> {
                    // Star Icon
                    drawLine(color = color, start = Offset(startX + 30f, startY + 110f), end = Offset(startX + 50f, startY + 110f), strokeWidth = strokeWidth)
                    drawLine(color = color, start = Offset(startX + 40f, startY + 100f), end = Offset(startX + 40f, startY + 120f), strokeWidth = strokeWidth)

                    // Smile Circle
                    drawCircle(color = color, radius = 14f, center = Offset(startX + 110f, startY + 30f), style = Stroke(strokeWidth))
                }
                2 -> {
                    // Music Note
                    drawLine(color = color, start = Offset(startX + 40f, startY + 40f), end = Offset(startX + 40f, startY + 20f), strokeWidth = strokeWidth)
                    drawCircle(color = color, radius = 6f, center = Offset(startX + 34f, startY + 40f))

                    // Camera Box
                    drawRect(color = color, topLeft = Offset(startX + 90f, startY + 90f), size = Size(30f, 20f), style = Stroke(strokeWidth))
                }
                else -> {
                    // Sparkles & Dots
                    drawCircle(color = color, radius = 4f, center = Offset(startX + 30f, startY + 80f))
                    drawCircle(color = color, radius = 5f, center = Offset(startX + 120f, startY + 120f))
                    drawCircle(color = color, radius = 3f, center = Offset(startX + 80f, startY + 30f))
                }
            }
        }
    }
}
