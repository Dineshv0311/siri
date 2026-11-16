package com.siri.assistant.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.siri.assistant.assistant.AssistantState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AssistantOrb(
    state: AssistantState,
    rmsDb: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Rotation angle
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulse scale for standby & speaking
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Fast rotation for processing
    val fastRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fast_rotation"
    )

    val normalizedRms = (rmsDb.coerceIn(0f, 10f) / 10f)

    Box(
        modifier = modifier
            .size(240.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val baseRadius = size.minDimension / 2.6f

            when (state) {
                AssistantState.STANDBY -> {
                    // Elegant dark metallic orb with subtle neon rim
                    val orbRadius = baseRadius * pulseScale
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF1E222F),
                                Color(0xFF12141A),
                                Color(0xFF08090C)
                            ),
                            center = center,
                            radius = orbRadius
                        ),
                        radius = orbRadius,
                        center = center
                    )
                    // Subtle glowing perimeter
                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.6f),
                                Color(0xFF7C4DFF).copy(alpha = 0.6f),
                                Color(0xFFFF4081).copy(alpha = 0.6f),
                                Color(0xFF00E5FF).copy(alpha = 0.6f)
                            ),
                            center = center
                        ),
                        radius = orbRadius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                AssistantState.LISTENING -> {
                    // Dynamic Siri-like fluid multi-color reactive wave orb
                    val reactiveRadius = baseRadius * (1.0f + normalizedRms * 0.35f)

                    // Outer fluid glow rings
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00E5FF).copy(alpha = 0.25f + normalizedRms * 0.3f),
                                Color(0xFF7C4DFF).copy(alpha = 0.15f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = reactiveRadius * 1.35f
                        ),
                        radius = reactiveRadius * 1.35f,
                        center = center
                    )

                    // Core gradient
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF80D8FF),
                                Color(0xFF651FFF),
                                Color(0xFF0D0D15)
                            ),
                            center = center,
                            radius = reactiveRadius
                        ),
                        radius = reactiveRadius,
                        center = center
                    )

                    // Waveform concentric rings
                    val waveCount = 3
                    for (i in 1..waveCount) {
                        val rad = reactiveRadius * (0.8f + (i * 0.15f) * (1f + normalizedRms))
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = (0.7f / i) + normalizedRms * 0.2f),
                            radius = rad,
                            center = center,
                            style = Stroke(width = (2.5f + normalizedRms * 3f).dp.toPx())
                        )
                    }
                }

                AssistantState.PROCESSING, AssistantState.EXECUTING -> {
                    // Thinking swirl animation
                    val currentRotation = fastRotation
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF7C4DFF),
                                Color(0xFF2979FF),
                                Color(0xFF0A0C14)
                            ),
                            center = center,
                            radius = baseRadius
                        ),
                        radius = baseRadius,
                        center = center
                    )

                    // Spinning bright neon ring
                    val rad = Math.toRadians(currentRotation.toDouble())
                    val ringOffset = Offset(
                        center.x + (baseRadius * 0.1f * cos(rad)).toFloat(),
                        center.y + (baseRadius * 0.1f * sin(rad)).toFloat()
                    )

                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFF00E5FF),
                                Color(0xFFFF4081),
                                Color(0xFFFFD600),
                                Color(0xFF00E5FF)
                            ),
                            center = ringOffset
                        ),
                        radius = baseRadius * 1.05f,
                        center = center,
                        style = Stroke(width = 4.dp.toPx())
                    )
                }

                AssistantState.SPEAKING -> {
                    // Expanding vibrant vocalizing orb
                    val speakRadius = baseRadius * pulseScale
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF4081).copy(alpha = 0.3f),
                                Color(0xFF7C4DFF).copy(alpha = 0.2f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = speakRadius * 1.4f
                        ),
                        radius = speakRadius * 1.4f,
                        center = center
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF4081),
                                Color(0xFF7C4DFF),
                                Color(0xFF0B0D18)
                            ),
                            center = center,
                            radius = speakRadius
                        ),
                        radius = speakRadius,
                        center = center
                    )

                    drawCircle(
                        brush = Brush.sweepGradient(
                            listOf(
                                Color(0xFFFF4081),
                                Color(0xFF00E5FF),
                                Color(0xFFFF4081)
                            ),
                            center = center
                        ),
                        radius = speakRadius,
                        center = center,
                        style = Stroke(width = 3.5.dp.toPx())
                    )
                }

                AssistantState.ERROR -> {
                    // Red error glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF5252),
                                Color(0xFFB71C1C),
                                Color(0xFF08090C)
                            ),
                            center = center,
                            radius = baseRadius
                        ),
                        radius = baseRadius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFFF5252),
                        radius = baseRadius,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }
    }
}
