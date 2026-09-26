package io.openflux.desktop.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.openflux.desktop.ui.components.AppIcons
import io.openflux.desktop.ui.components.Tone
import io.openflux.desktop.ui.components.appClickable
import io.openflux.desktop.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource

/**
 * The Android app's main toggle: a round button inside two static rings,
 * with a slow wave while connecting or connected. Its fill follows the
 * state: surface when idle, accent when connected, amber in between, red
 * on error.
 */
@Composable
fun ConnectButton(
    title: String,
    detail: String,
    extra: String,
    tone: Tone,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val idle = tone == Tone.Neutral
    val (fillTarget, pressedTarget) = when (tone) {
        Tone.Accent, Tone.Success -> colors.accent to colors.accentPressed
        Tone.Warning -> colors.warning to colors.warningPressed
        Tone.Danger -> Color(0xFFEF4444) to Color(0xFFB91C1C)
        Tone.Neutral -> colors.surface to colors.surfaceTonal
    }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(if (pressed || hovered) pressedTarget else fillTarget, tween(320))
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f)
    val content = if (idle) colors.text else Color.White
    val pulsing = tone == Tone.Accent || tone == Tone.Warning

    val transition = rememberInfiniteTransition()
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart))
    val ringColor = colors.border
    val waveColor = fillTarget

    Box(modifier.size(dimens.connectRingOuter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(dimens.connectRingOuter)) {
            val outer = size.minDimension / 2
            val inner = outer * (dimens.connectRingInner / dimens.connectRingOuter)
            drawCircle(ringColor, radius = outer - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            drawCircle(ringColor, radius = inner - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
            if (pulsing) {
                val base = outer * (dimens.connectButton / dimens.connectRingOuter)
                drawCircle(
                    waveColor.copy(alpha = 0.5f * (1f - wave)),
                    radius = base * (1f + 0.45f * wave),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }
        Column(
            Modifier
                .size(dimens.connectButton)
                .scale(scale)
                .shadow(if (idle) 2.dp else 10.dp, CircleShape, ambientColor = fillTarget, spotColor = fillTarget)
                .clip(CircleShape)
                .background(fill)
                .alpha(if (enabled) 1f else 0.6f)
                .appClickable(interaction, enabled, onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Icon(painterResource(AppIcons.Power), null, tint = content, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, style = AppTheme.typography.bodyStrong, color = content, textAlign = TextAlign.Center)
            if (detail.isNotEmpty()) Text(detail, style = AppTheme.typography.caption, color = content.copy(alpha = 0.85f))
            if (extra.isNotEmpty()) Text(extra, style = AppTheme.typography.caption, color = content.copy(alpha = 0.7f))
        }
    }
}
