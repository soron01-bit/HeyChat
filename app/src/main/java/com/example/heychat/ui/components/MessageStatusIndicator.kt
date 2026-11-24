package com.example.heychat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.heychat.data.models.MessageStatus

// Custom Status Indicator Colors
val SentOutlineColor = Color(0xB3FFFFFF) // 70% White outline
val DeliveredHalfColor = Color(0xFFA7F3D0) // Mint/Emerald half fill
val SeenSolidColor = Color(0xFF38BDF8) // Electric Cyan solid fill for SEEN / READ receipts

@Composable
fun MessageStatusIndicator(
    status: MessageStatus,
    modifier: Modifier = Modifier
) {
    val circleColor by animateColorAsState(
        targetValue = when (status) {
            MessageStatus.SEEN -> SeenSolidColor
            MessageStatus.DELIVERED -> DeliveredHalfColor
            else -> SentOutlineColor
        },
        animationSpec = tween(300),
        label = "CircleColorAnimation"
    )

    val scaleState by animateFloatAsState(
        targetValue = if (status == MessageStatus.SEEN) 1.15f else 1.0f,
        animationSpec = tween(300),
        label = "CircleScaleAnimation"
    )

    Canvas(
        modifier = modifier
            .padding(start = 3.dp)
            .size(11.dp)
            .scale(scaleState)
    ) {
        val strokeWidth = 1.6.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2f
        val centerOffset = Offset(size.width / 2f, size.height / 2f)

        when (status) {
            MessageStatus.SENT -> {
                // 1. SENT: Hollow Circle Outline (○)
                drawCircle(
                    color = circleColor,
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = strokeWidth)
                )
            }
            MessageStatus.DELIVERED -> {
                // 2. DELIVERED: Half-Filled Circle (◐)
                // Draw outer circular border
                drawCircle(
                    color = circleColor,
                    radius = radius,
                    center = centerOffset,
                    style = Stroke(width = strokeWidth)
                )

                // Draw solid left half fill
                val arcSize = Size(radius * 2f, radius * 2f)
                val arcTopLeft = Offset(centerOffset.x - radius, centerOffset.y - radius)
                drawArc(
                    color = circleColor,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = arcTopLeft,
                    size = arcSize
                )
            }
            MessageStatus.SEEN -> {
                // 3. SEEN: Completely Filled Solid Circle (●)
                drawCircle(
                    color = circleColor,
                    radius = radius + (strokeWidth / 2f),
                    center = centerOffset
                )
            }
        }
    }
}
