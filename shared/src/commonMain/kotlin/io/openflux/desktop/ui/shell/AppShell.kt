package io.openflux.desktop.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import io.openflux.desktop.model.ThemeMode
import io.openflux.desktop.model.isActive
import io.openflux.desktop.service.AppContainer
import io.openflux.desktop.service.LocalAppContainer
import io.openflux.desktop.ui.BrowserViews
import io.openflux.desktop.ui.LocalBrowserViews
import io.openflux.desktop.ui.LocalScrollbars
import io.openflux.desktop.ui.NoBrowserViews
import io.openflux.desktop.ui.LocalShortcuts
import io.openflux.desktop.ui.Scrollbars
import io.openflux.desktop.ui.Shortcuts
import io.openflux.desktop.ui.components.LocalToaster
import io.openflux.desktop.ui.components.ToastHost
import io.openflux.desktop.ui.components.Toaster
import io.openflux.desktop.ui.home.HomeTab
import io.openflux.desktop.ui.logs.LogsTab
import io.openflux.desktop.ui.profiles.ProfilesTab
import io.openflux.desktop.ui.settings.SettingsTab
import io.openflux.desktop.ui.theme.AppTheme
import io.openflux.desktop.ui.theme.OpenFluxTheme

/** The window's width class; below [WidthClass.Medium] the sidebar shows icons only. */
enum class WidthClass { Compact, Medium, Expanded }

fun widthClassOf(width: Dp): WidthClass = when {
    width < 1000.dp -> WidthClass.Compact
    width < 1440.dp -> WidthClass.Medium
    else -> WidthClass.Expanded
}

/** Cross-screen requests: open a section, start an import. */
@Stable
class ShellController {
    var widthClass by mutableStateOf(WidthClass.Medium)
        internal set
    internal var tabNavigator: TabNavigator? = null

    /** Set by Home's empty state; the Profiles screen opens the import dialog. */
    var importRequested by mutableStateOf(false)
    /** Set by Home; the Profiles screen opens a new profile in the editor. */
    var newProfileRequested by mutableStateOf(false)
    /** A profile the Profiles screen should select. */
    var focusProfileId by mutableStateOf<String?>(null)

    fun open(tab: Tab) {
        tabNavigator?.current = tab
    }
}

val LocalShell = staticCompositionLocalOf { ShellController() }

val AppTabs: List<Tab> = listOf(HomeTab, ProfilesTab, LogsTab, SettingsTab)

@Composable
fun OpenFluxApp(container: AppContainer, scrollbars: Scrollbars, shortcuts: Shortcuts, browsers: BrowserViews = NoBrowserViews) {
    val settings by container.settings.settings.collectAsState()
    val dark = when (settings.theme) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val toaster = remember { Toaster() }
    val shell = remember { ShellController() }
    OpenFluxTheme(dark) {
        CompositionLocalProvider(
            LocalAppContainer provides container,
            LocalToaster provides toaster,
            LocalScrollbars provides scrollbars,
            LocalBrowserViews provides browsers,
            LocalShortcuts provides shortcuts,
            LocalShell provides shell,
        ) {
            AppShell(shell, toaster)
        }
    }
}

@Composable
private fun AppShell(shell: ShellController, toaster: Toaster) {
    val container = LocalAppContainer.current
    val shortcuts = LocalShortcuts.current
    val settings by container.settings.settings.collectAsState()
    TabNavigator(HomeTab) { navigator ->
        shell.tabNavigator = navigator
        DisposableEffect(navigator) {
            val unregister = shortcuts.register { event ->
                if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) return@register false
                val index = when (event.key) {
                    Key.One -> 0
                    Key.Two -> 1
                    Key.Three -> 2
                    Key.Four -> 3
                    else -> -1
                }
                when {
                    index >= 0 -> { navigator.current = AppTabs[index]; true }
                    event.key == Key.Enter -> {
                        val state = container.connection.state.value
                        if (state.isActive) container.connection.disconnect() else HomeTab.connectSelected(container)
                        true
                    }
                    else -> false
                }
            }
            onDispose { unregister() }
        }
        BoxWithConstraints(Modifier.fillMaxSize().background(AppTheme.colors.background)) {
            shell.widthClass = widthClassOf(maxWidth)
            val collapsed = settings.sidebarCollapsed || shell.widthClass == WidthClass.Compact
            Row(Modifier.fillMaxSize()) {
                Sidebar(
                    current = navigator.current,
                    tabs = AppTabs,
                    collapsed = collapsed,
                    canExpand = shell.widthClass != WidthClass.Compact,
                    onSelect = { navigator.current = it },
                    onToggleCollapsed = { container.settings.update { s -> s.copy(sidebarCollapsed = !s.sidebarCollapsed) } },
                    modifier = Modifier.fillMaxHeight().width(if (collapsed) AppTheme.dimens.sidebarCompactWidth else AppTheme.dimens.sidebarWidth),
                )
                AnimatedContent(
                    targetState = navigator.current,
                    transitionSpec = {
                        (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(260)) { it / 40 })
                            .togetherWith(fadeOut(tween(120)))
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentKey = { it.key },
                ) { tab ->
                    Box(Modifier.fillMaxSize()) { navigator.saveableState("tab", tab) { tab.Content() } }
                }
            }
            ToastHost(toaster, Modifier.align(Alignment.BottomCenter).padding(bottom = AppTheme.spacing.xxl))
            CaptchaDialog()
        }
    }
}
