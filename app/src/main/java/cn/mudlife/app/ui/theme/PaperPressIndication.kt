package cn.mudlife.app.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.dp

/** A local tint avoids platform ripple expansion across rectangular rows and tabs. */
@Composable
internal fun rememberPaperPressIndication(color: Color): Indication {
    val ink = rememberUpdatedState(color)
    return remember { PaperPressIndication(ink) }
}

private class PaperPressIndication(private val ink: State<Color>) : Indication {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        val pressed by interactionSource.collectIsPressedAsState()
        val focused by interactionSource.collectIsFocusedAsState()
        val opacity = animateFloatAsState(if (pressed || focused) .055f else 0f, tween(90), label = "pressTint")
        return remember(interactionSource) {
            object : IndicationInstance {
                override fun ContentDrawScope.drawIndication() {
                    drawContent()
                    if (opacity.value > 0f) drawRoundRect(ink.value.copy(alpha = opacity.value),
                        cornerRadius = CornerRadius(12.dp.toPx()))
                }
            }
        }
    }
}
