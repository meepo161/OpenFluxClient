package io.openflux.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import io.openflux.desktop.ui.theme.AppTheme
import kotlinx.coroutines.delay

/** Shows [text] in a small bubble after the pointer rests on [content]. */
@Composable
fun WithTooltip(text: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        if (hovered) {
            delay(TOOLTIP_DELAY_MS)
            visible = true
        } else {
            visible = false
        }
    }
    Box(modifier.hoverable(interaction)) {
        content()
        if (visible && text.isNotEmpty()) {
            Popup(popupPositionProvider = remember { BelowAnchor(8) }) {
                Text(
                    text,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.colors.background,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(AppTheme.shapes.small)
                        .background(AppTheme.colors.text.copy(alpha = 0.92f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private const val TOOLTIP_DELAY_MS = 450L

/** Places a popup under its anchor, centered, kept inside the window. */
private class BelowAnchor(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + gapPx
        val y = if (below + popupContentSize.height <= windowSize.height) below else anchorBounds.top - gapPx - popupContentSize.height
        return IntOffset(x, y.coerceAtLeast(0))
    }
}

data class MenuAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector? = null,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val shortcut: String? = null,
)

/** Opens [actions] as a context menu where the right mouse button was pressed. */
@Composable
fun ContextMenuArea(actions: () -> List<MenuAction>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var menuAt by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    Box(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        menuAt = event.changes.first().position
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        },
    ) {
        content()
        val at = menuAt
        if (at != null) {
            val offset = with(density) { DpOffset(at.x.toDp(), at.y.toDp()) }
            Box(Modifier.padding(start = offset.x, top = offset.y)) {
                AppMenu(expanded = true, onDismiss = { menuAt = null }, actions = actions())
            }
        }
    }
}

@Composable
fun AppMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<MenuAction>) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = AppTheme.shapes.card,
        containerColor = AppTheme.colors.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppTheme.colors.border),
    ) {
        actions.forEach { action ->
            val color = if (action.danger) AppTheme.colors.danger else AppTheme.colors.text
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(action.label, style = AppTheme.typography.body, color = color, modifier = Modifier.weight(1f, fill = false))
                        if (action.shortcut != null) {
                            Spacer(Modifier.width(AppTheme.spacing.xl))
                            Text(action.shortcut, style = AppTheme.typography.caption, color = AppTheme.colors.textHint)
                        }
                    }
                },
                leadingIcon = action.icon?.let { { Icon(it, null, tint = color, modifier = Modifier.size(18.dp)) } },
                enabled = action.enabled,
                onClick = {
                    onDismiss()
                    action.onClick()
                },
                colors = MenuDefaults.itemColors(textColor = color),
                modifier = Modifier.height(36.dp),
            )
        }
    }
}

/**
 * A modal dialog: Esc cancels, Enter (or Ctrl+Enter in multi-line content)
 * runs the primary action when it is enabled.
 */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    primary: String? = null,
    onPrimary: () -> Unit = {},
    primaryEnabled: Boolean = true,
    primaryStyle: ButtonStyle = ButtonStyle.Primary,
    secondary: String? = "Отмена",
    enterSubmits: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A margin from the window's edges; a dialog taller than the window scrolls its body.
        Box(Modifier.padding(AppTheme.spacing.xl), contentAlignment = Alignment.Center) {
        Column(
            modifier
                .widthIn(min = 320.dp, max = AppTheme.dimens.dialogWidth)
                .shadow(24.dp, AppTheme.shapes.dialog)
                .clip(AppTheme.shapes.dialog)
                .background(AppTheme.colors.surface)
                .border(1.dp, AppTheme.colors.border, AppTheme.shapes.dialog)
                .focusRequester(focus)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.Escape -> { onDismiss(); true }
                        event.key == Key.Enter && primary != null && primaryEnabled && (enterSubmits || event.isCtrlPressed) -> { onPrimary(); true }
                        else -> false
                    }
                }
                .padding(AppTheme.spacing.xxl),
        ) {
            Text(title, style = AppTheme.typography.sectionTitle, color = AppTheme.colors.text)
            Spacer(Modifier.height(AppTheme.spacing.l))
            Column(Modifier.weight(1f, fill = false).verticalScroll(scroll)) { content() }
            if (primary != null || secondary != null) {
                Spacer(Modifier.height(AppTheme.spacing.xl))
                ButtonRow(Modifier.fillMaxWidth(), alignment = Alignment.End) {
                    if (secondary != null) AppButton(secondary, onDismiss, style = ButtonStyle.Secondary)
                    if (primary != null) AppButton(primary, onPrimary, style = primaryStyle, enabled = primaryEnabled)
                }
            }
        }
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/** One message at a time at the bottom of the window. */
class Toaster {
    var current by mutableStateOf<ToastMessage?>(null)
        private set
    private var serial = 0L

    fun show(text: String, tone: Tone = Tone.Neutral) {
        current = ToastMessage(++serial, text, tone)
    }

    fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

data class ToastMessage(val id: Long, val text: String, val tone: Tone)

val LocalToaster = staticCompositionLocalOf { Toaster() }

@Composable
fun ToastHost(toaster: Toaster, modifier: Modifier = Modifier) {
    val message = toaster.current ?: return
    LaunchedEffect(message.id) {
        delay(3200)
        toaster.dismiss(message.id)
    }
    Row(
        modifier
            .shadow(12.dp, AppTheme.shapes.card)
            .clip(AppTheme.shapes.card)
            .background(if (AppTheme.colors.isDark) AppTheme.colors.surfaceTonal else Color(0xFF202124))
            .padding(horizontal = AppTheme.spacing.l, vertical = AppTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (message.tone != Tone.Neutral) {
            Box(Modifier.size(8.dp).clip(AppTheme.shapes.pill).background(toneColor(message.tone)))
            Spacer(Modifier.width(AppTheme.spacing.s))
        }
        Text(message.text, style = AppTheme.typography.body, color = Color.White)
    }
}
