package com.example.llama.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Log

@Composable
fun MessageBubble(
    text: String,
    isUser: Boolean,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    dotOffset: Float = 0f
) {
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else Color(0xFFE3F2FD) // Light blue for AI messages
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val dotAnimatable = remember { Animatable(0f) }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            dotAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1200
                        0f at 0 with LinearEasing
                        1f at 400 with LinearEasing
                        0f at 800 with LinearEasing
                    },
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    // Separate Box for content and tail to ensure proper layering
    Box(modifier = modifier) {
        // Main bubble content
        Box(
            modifier = Modifier
                .background(bubbleColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .align(Alignment.Center)
        ) {
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    for (i in 0..2) {
                        val delay = i * 200
                        val alpha = when {
                            dotAnimatable.value + (delay / 1200f) % 1f < 0.33f -> 0.3f
                            dotAnimatable.value + (delay / 1200f) % 1f < 0.66f -> 1f
                            else -> 0.3f
                        }
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    Color.Gray.copy(alpha = alpha),
                                    RoundedCornerShape(50)
                                )
                                .padding(end = 4.dp)
                        )
                    }
                }
            } else {
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 14.sp
                )
            }
        }

        // Tail Canvas - properly attached to bubble edge
        Canvas(
            modifier = Modifier
                .align(if (isUser) Alignment.BottomEnd else Alignment.BottomStart)
                .offset(x = if (isUser) (-4).dp else 4.dp, y = (-4).dp)
        ) {
            val tailHeight = 12.dp.toPx()
            val tailWidth = 16.dp.toPx()
            val path = Path().apply {
                if (isUser) {
                    moveTo(0f, 0f) // Start at bubble edge
                    lineTo(tailWidth, -tailHeight/2) // Point outward
                    lineTo(0f, -tailHeight) // Complete triangle
                } else {
                    moveTo(0f, 0f) // Start at bubble edge
                    lineTo(-tailWidth, -tailHeight/2) // Point outward
                    lineTo(0f, -tailHeight) // Complete triangle
                }
                close()
            }
            drawPath(
                path = path,
                color = bubbleColor
            )
        }
    }
}