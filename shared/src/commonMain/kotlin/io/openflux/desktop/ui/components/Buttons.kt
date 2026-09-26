package io.openflux.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.openflux.desktop.ui.theme.AppTheme
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class ButtonStyle { Primary, Secondary, Ghost, Danger }

/** A clickable that shows the hand cursor and exposes hover/press state. */
@Composable
fun Modifier.appClickable(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier = this
    .hoverable(interaction, enabled)
    .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
    .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    leadingResource: DrawableResource? = null,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val (base, active, content, border) = when (style) {
        ButtonStyle.Primary -> Quad(colors.accent, colors.accentPressed, colors.onAccent, Color.Transparent)
        ButtonStyle.Danger -> Quad(colors.danger, colors.dangerPressed, Color.White, Color.Transparent)
        ButtonStyle.Secondary -> Quad(colors.surface, colors.surfaceTonal, colors.text, colors.border)
        ButtonStyle.Ghost -> Quad(Color.Transparent, colors.accentSoft, colors.accent, Color.Transparent)
    }
    val fill by animateColorAsState(if (pressed || hovered) active else base)
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = AppTheme.dimens.buttonHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(AppTheme.shapes.button)
            .background(fill)
            .border(1.dp, border, AppTheme.shapes.button)
            .appClickable(interaction, enabled, onClick = onClick)
            .padding(horizontal = AppTheme.spacing.l),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) Icon(leading, null, tint = content, modifier = Modifier.size(18.dp))
        if (leadingResource != null) Icon(painterResource(leadingResource), null, tint = content, modifier = Modifier.size(18.dp))
        Text(text, style = AppTheme.typography.bodyStrong, color = content)
    }
}

private data class Quad(val a: Color, val b: Color, val c: Color, val d: Color)

/** A square icon button with a hover tint and a tooltip. */
@Composable
fun AppIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    resource: DrawableResource? = null,
    enabled: Boolean = true,
    tint: Color = AppTheme.colors.textSecondary,
    selected: Boolean = false,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            selected -> colors.accentSoft
            hovered && enabled -> colors.surfaceTonal
            else -> Color.Transparent
        },
    )
    WithTooltip(tooltip) {
        Box(
            modifier = modifier
                .size(AppTheme.dimens.iconButton)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(AppTheme.shapes.small)
                .background(fill)
                .appClickable(interaction, enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val color = if (selected) colors.accent else tint
            if (icon != null) Icon(icon, tooltip, tint = color, modifier = Modifier.size(AppTheme.dimens.icon))
            if (resource != null) Icon(painterResource(resource), tooltip, tint = color, modifier = Modifier.size(AppTheme.dimens.icon))
        }
    }
}

/** A text-only action, as the Android app's "Сгенерировать ключ" links. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = AppTheme.colors.accent, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(AppTheme.shapes.small)
            .background(if (hovered && enabled) AppTheme.colors.accentSoft else Color.Transparent)
            .appClickable(interaction, enabled, onClick = onClick)
            .padding(horizontal = AppTheme.spacing.s),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.typography.bodyStrong, color = if (enabled) color else AppTheme.colors.textHint)
    }
}
