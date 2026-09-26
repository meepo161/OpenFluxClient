package io.openflux.desktop.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.Tab
import io.openflux.desktop.model.profile
import io.openflux.desktop.service.LocalAppContainer
import io.openflux.desktop.ui.components.AppIconButton
import io.openflux.desktop.ui.components.AppIcons
import io.openflux.desktop.ui.components.WithTooltip
import io.openflux.desktop.ui.components.appClickable
import io.openflux.desktop.ui.components.toneColor
import io.openflux.desktop.ui.look
import io.openflux.desktop.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource

/**
 * Left navigation: sections, then the connection state at the bottom so it
 * stays visible from every screen. Collapses to icons on narrow windows.
 */
@Composable
fun Sidebar(
    current: Tab,
    tabs: List<Tab>,
    collapsed: Boolean,
    canExpand: Boolean,
    onSelect: (Tab) -> Unit,
    onToggleCollapsed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    Column(modifier.background(colors.sidebar).padding(vertical = AppTheme.spacing.l, horizontal = AppTheme.spacing.m)) {
        Brand(collapsed)
        Spacer(Modifier.height(AppTheme.spacing.xxl))
        tabs.forEachIndexed { index, tab ->
            SidebarItem(tab, selected = tab.key == current.key, collapsed = collapsed, shortcut = "Ctrl+${index + 1}") { onSelect(tab) }
            Spacer(Modifier.height(AppTheme.spacing.xs))
        }
        Spacer(Modifier.weight(1f))
        ConnectionFooter(collapsed) { onSelect(tabs.first()) }
        if (canExpand) {
            Spacer(Modifier.height(AppTheme.spacing.s))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.End) {
                AppIconButton(
                    tooltip = if (collapsed) "Развернуть меню" else "Свернуть меню",
                    onClick = onToggleCollapsed,
                    icon = if (collapsed) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                )
            }
        }
    }
}

@Composable
private fun Brand(collapsed: Boolean) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = if (collapsed) 0.dp else AppTheme.spacing.xs)) {
        Box(
            Modifier.size(40.dp).clip(AppTheme.shapes.button).background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(AppIcons.Logo), "OpenFlux", tint = Color.White, modifier = Modifier.size(34.dp))
        }
        if (!collapsed) {
            Spacer(Modifier.width(AppTheme.spacing.m))
            Column {
                Text("OpenFlux", style = AppTheme.typography.sectionTitle, color = colors.text)
                Text("Зашифрованный туннель", style = AppTheme.typography.caption, color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun SidebarItem(tab: Tab, selected: Boolean, collapsed: Boolean, shortcut: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            selected -> colors.accent
            hovered -> colors.surfaceTonal
            else -> Color.Transparent
        },
    )
    val content = if (selected) colors.onAccent else colors.textSecondary
    val options = tab.options
    WithTooltip(if (collapsed) "${options.title} ($shortcut)" else "") {
        Row(
            Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(AppTheme.shapes.button)
                .background(fill)
                .appClickable(interaction, onClick = onClick)
                .padding(horizontal = if (collapsed) 0.dp else AppTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
        ) {
            options.icon?.let { Icon(it, options.title, tint = content, modifier = Modifier.size(22.dp)) }
            if (!collapsed) {
                Spacer(Modifier.width(AppTheme.spacing.m))
                Text(options.title, style = AppTheme.typography.bodyStrong, color = if (selected) colors.onAccent else colors.text, modifier = Modifier.weight(1f))
                if (hovered && !selected) Text(shortcut, style = AppTheme.typography.caption, color = colors.textHint)
            }
        }
    }
}

@Composable
private fun ConnectionFooter(collapsed: Boolean, onClick: () -> Unit) {
    val container = LocalAppContainer.current
    val state by container.connection.state.collectAsState()
    val profiles by container.profiles.profiles.collectAsState()
    val settings by container.settings.settings.collectAsState()
    // Idle: the profile Home would connect (the chosen one, else the first).
    val profileName = state.profile?.name
        ?: (profiles.firstOrNull { it.id == settings.selectedProfileId } ?: profiles.firstOrNull())?.name
    val look = state.look()
    val tone = toneColor(look.tone)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dot = @Composable { Box(Modifier.size(10.dp).clip(CircleShape).background(tone)) }
    WithTooltip(if (collapsed) look.title else "") {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(AppTheme.shapes.card)
                .background(if (hovered) AppTheme.colors.surfaceTonal else AppTheme.colors.surface)
                .appClickable(interaction, onClick = onClick)
                .padding(AppTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.Start,
        ) {
            dot()
            if (!collapsed) {
                Spacer(Modifier.width(AppTheme.spacing.m))
                Column(Modifier.weight(1f)) {
                    Text(look.title, style = AppTheme.typography.bodyStrong, color = AppTheme.colors.text, maxLines = 1)
                    Text(
                        profileName ?: "Нет профилей",
                        style = AppTheme.typography.caption,
                        color = AppTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
