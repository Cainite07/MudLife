package cn.mudlife.app.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shower
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import cn.mudlife.app.R
import cn.mudlife.app.utils.AccountGuideRun
import cn.mudlife.app.ui.theme.AppColors

@Composable
internal fun AccountGuide(
    school: Boolean, campus: Boolean, logistics: Boolean,
    schoolExpired: Boolean, logisticsExpired: Boolean, presentation: AccountGuideRun, contentInsets: PaddingValues,
    leaving: Boolean, onSelect: (String) -> Unit, onContinue: (Boolean) -> Unit
) {
    val clock = remember(presentation) { Animatable(0f) }
    LaunchedEffect(clock) { clock.animateTo(1f, tween(2600, easing = LinearEasing)) }
    val rowsReady by remember(clock) { derivedStateOf { clock.value >= .72f } }
    val finishReady by remember(clock) { derivedStateOf { clock.value >= .90f } }

    val context = LocalContext.current
    val iconSize = 84.dp
    val pixels = with(LocalDensity.current) { iconSize.roundToPx() }
    val icon = remember(context, pixels) {
        requireNotNull(context.getDrawable(R.mipmap.ic_launcher)).toBitmap(pixels, pixels).asImageBitmap()
    }
    val ink = AppColors.Accent
    val completed = school && !schoolExpired && campus && logistics && !logisticsExpired
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safeTop = contentInsets.calculateTopPadding()
        val safeHeight = (maxHeight - safeTop - contentInsets.calculateBottomPadding()).coerceAtLeast(0.dp)
        val top = (safeHeight * .15f).coerceIn(20.dp, 145.dp)
        val sectionGap = (safeHeight * .065f).coerceIn(22.dp, 60.dp)
        val travel = (safeHeight * .45f - top - iconSize / 2).coerceIn(0.dp, 190.dp)
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val settle = phase(t, .22f, .77f)
            val spread = phase(t, .08f, .92f)
            val center = Offset(size.width / 2, (safeTop + top + iconSize / 2 + travel * (1f - settle)).toPx())
            drawRect(ink, alpha = .035f * phase(t, .02f, .82f))
            val radius = (125.dp.toPx() + size.width * .57f * spread)
            drawCircle(Brush.radialGradient(listOf(ink, ink.copy(alpha = 0f)), center, radius),
                radius, center, alpha = phase(t, 0f, .30f) * (.18f - .10f * spread))
        }
        Column(Modifier.align(Alignment.TopCenter).padding(contentInsets).consumeWindowInsets(contentInsets)
            .widthIn(max = 480.dp).fillMaxSize()
            .padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(top))
                Image(icon, null, Modifier.size(iconSize).graphicsLayer {
                    val settle = phase(clock.value, .22f, .77f)
                    alpha = phase(clock.value, .025f, .34f)
                    translationY = travel.toPx() * (1f - settle)
                    scaleX = 1.08f - .08f * settle
                    scaleY = scaleX
                })
                Spacer(Modifier.height(17.dp))
                Text(stringResource(R.string.app_name), Modifier.reveal(clock, .47f, .73f).semantics { heading() },
                    color = AppColors.TextPrimary, fontFamily = FontFamily.Serif,
                    fontSize = 30.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(sectionGap))
                Column(Modifier.fillMaxWidth().reveal(clock, .49f, .87f)) {
                    AccountRow("学校账号", "课表 · 一卡通 · 电费", Icons.Outlined.School, school, schoolExpired,
                        enabled = rowsReady && !leaving, showProgress = true) { onSelect("school") }
                    GuideDivider()
                    AccountRow("趣智校园", "饮水 · 吹风机", Icons.Outlined.WaterDrop, campus,
                        enabled = rowsReady && !leaving, showProgress = true) { onSelect("life") }
                    GuideDivider()
                    AccountRow("趣智后勤热水", "洗澡码", Icons.Outlined.Shower, logistics, logisticsExpired,
                        enabled = rowsReady && !leaving, showProgress = true) { onSelect("logistics") }
                }
            }
            Button(onClick = { onContinue(completed) }, enabled = finishReady && !leaving,
                modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth().heightIn(min = 50.dp)
                    .reveal(clock, .68f, 1f),
                shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.buttonColors(
                    containerColor = ink, contentColor = AppColors.OnAccent,
                    disabledContainerColor = ink, disabledContentColor = AppColors.OnAccent)) {
                Text(if (completed) "进入首页" else "稍后登录", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun GuideDivider() {
    HorizontalDivider(Modifier.padding(start = 36.dp), color = AppColors.Border.copy(alpha = .75f))
}

private fun Modifier.reveal(clock: Animatable<Float, *>, start: Float, end: Float) = graphicsLayer {
    val fraction = phase(clock.value, start, end)
    alpha = fraction
    translationY = 12.dp.toPx() * (1f - fraction)
}

private fun phase(value: Float, start: Float, end: Float) =
    FastOutSlowInEasing.transform(((value - start) / (end - start)).coerceIn(0f, 1f))
