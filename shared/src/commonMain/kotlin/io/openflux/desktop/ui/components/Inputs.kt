package io.openflux.desktop.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.openflux.desktop.ui.theme.AppTheme

/**
 * A labelled text field in the Android app's style (surface fill, 1 dp
 * border, accent border when focused). Secret fields get a show/hide toggle.
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String = "",
    enabled: Boolean = true,
    secret: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    monospace: Boolean = false,
    error: String? = null,
    helper: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    var revealed by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        when {
            error != null -> colors.danger
            focused -> colors.accent
            hovered && enabled -> colors.textHint
            else -> colors.border
        },
    )
    val style = (if (monospace) AppTheme.typography.mono else AppTheme.typography.body).copy(color = colors.text)
    Column(modifier) {
        if (label != null) {
            Text(label, style = AppTheme.typography.bodySmall, color = colors.textSecondary)
            Spacer(Modifier.height(6.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = style,
            cursorBrush = SolidColor(colors.accent),
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (secret && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().pointerHoverIcon(PointerIcon.Text),
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = AppTheme.dimens.fieldHeight)
                        .alpha(if (enabled) 1f else 0.55f)
                        .clip(AppTheme.shapes.field)
                        .background(colors.surface)
                        .border(if (focused) 1.5.dp else 1.dp, borderColor, AppTheme.shapes.field)
                        .padding(start = AppTheme.spacing.m, end = AppTheme.spacing.xs),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    Box(Modifier.weight(1f).padding(vertical = 11.dp)) {
                        if (value.isEmpty()) Text(placeholder, style = style, color = colors.textHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                    if (secret) {
                        AppIconButton(
                            tooltip = if (revealed) "Скрыть" else "Показать",
                            onClick = { revealed = !revealed },
                            resource = if (revealed) AppIcons.VisibilityOff else AppIcons.Visibility,
                        )
                    }
                    trailing?.invoke(this)
                }
            },
        )
        val below = error ?: helper
        if (below != null) {
            Spacer(Modifier.height(4.dp))
            Text(below, style = AppTheme.typography.caption, color = if (error != null) colors.danger else colors.textSecondary)
        }
    }
}

@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val colors = AppTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.pointerHoverIcon(PointerIcon.Hand),
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = colors.accent,
            checkedBorderColor = colors.accent,
            uncheckedThumbColor = colors.textHint,
            uncheckedTrackColor = colors.surfaceTonal,
            uncheckedBorderColor = colors.border,
        ),
    )
}

/** A setting row: title, description, switch; the whole row toggles. */
@Composable
fun SwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.field)
            .background(if (hovered && enabled) AppTheme.colors.surfaceTonal.copy(alpha = 0.6f) else Color.Transparent)
            .appClickable(interaction, enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = AppTheme.spacing.m, vertical = AppTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.typography.bodyStrong, color = AppTheme.colors.text)
            if (description != null) Text(description, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
        }
        Spacer(Modifier.size(AppTheme.spacing.m))
        AppSwitch(checked, null, enabled)
    }
}

/** Mutually exclusive options in one bar, as the Android app's segmented control. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    Row(
        modifier
            .clip(AppTheme.shapes.button)
            .background(colors.surfaceTonal.copy(alpha = if (colors.isDark) 1f else 0.7f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            val fill by animateColorAsState(
                when {
                    isSelected -> colors.surface
                    hovered && enabled -> colors.surface.copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(AppTheme.shapes.small)
                    .background(fill)
                    .then(if (isSelected) Modifier.border(1.dp, colors.border, AppTheme.shapes.small) else Modifier)
                    .appClickable(interaction, enabled, role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = AppTheme.spacing.s),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = if (isSelected) AppTheme.typography.bodyStrong else AppTheme.typography.body,
                    color = if (isSelected) colors.text else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
