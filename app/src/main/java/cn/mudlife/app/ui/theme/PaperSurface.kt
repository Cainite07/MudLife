package cn.mudlife.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** A static, very faint paper grain beneath content; no layout or input changes. */
@Composable
fun Modifier.paperBackground(): Modifier {
    val paper = AppColors.Background
    val ink = AppColors.TextPrimary
    val dark = AppColors.isDark
    return drawWithCache {
        val random = Random(417)
        val spacing = 20.dp.toPx()
        val count = (size.width * size.height / (spacing * spacing)).toInt().coerceIn(0, 480)
        val fibers = List(count) { Offset(random.nextFloat() * size.width, random.nextFloat() * size.height) }
        onDrawBehind {
            drawRect(paper)
            drawPoints(fibers, PointMode.Points, ink.copy(alpha = if (dark) 0.028f else 0.035f), strokeWidth = 0.5.dp.toPx())
        }
    }
}
