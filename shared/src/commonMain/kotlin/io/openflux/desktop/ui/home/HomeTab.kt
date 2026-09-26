package io.openflux.desktop.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.openflux.desktop.model.ConnectionMode
import io.openflux.desktop.model.ConnectionState
import io.openflux.desktop.model.ExitAddress
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.isActive
import io.openflux.desktop.service.AppContainer
import io.openflux.desktop.service.LocalAppContainer
import io.openflux.desktop.ui.Format
import io.openflux.desktop.ui.LocalScrollbars
import io.openflux.desktop.ui.components.AppButton
import io.openflux.desktop.ui.components.AppCard
import io.openflux.desktop.ui.components.AppIconButton
import io.openflux.desktop.ui.components.AppIcons
import io.openflux.desktop.ui.components.AppMenu
import io.openflux.desktop.ui.components.Banner
import io.openflux.desktop.ui.components.ButtonStyle
import io.openflux.desktop.ui.components.EmptyState
import io.openflux.desktop.ui.components.HorizontalRule
import io.openflux.desktop.ui.components.IconBubble
import io.openflux.desktop.ui.components.KeyValueRow
import io.openflux.desktop.ui.components.LocalToaster
import io.openflux.desktop.ui.components.MenuAction
import io.openflux.desktop.ui.components.PageHeader
import io.openflux.desktop.ui.components.QrCode
import io.openflux.desktop.ui.components.SectionLabel
import io.openflux.desktop.ui.components.Segmented
import io.openflux.desktop.ui.components.StatusBadge
import io.openflux.desktop.ui.components.SwitchRow
import io.openflux.desktop.ui.components.TextAction
import io.openflux.desktop.ui.components.Tone
import io.openflux.desktop.ui.components.appClickable
import io.openflux.desktop.ui.logs.LogsTab
import io.openflux.desktop.ui.look
import io.openflux.desktop.ui.profiles.ProfilesTab
import io.openflux.desktop.ui.shell.LocalShell
import io.openflux.desktop.ui.theme.AppTheme
import org.jetbrains.compose.resources.painterResource

object HomeTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 0u, title = "Главная", icon = painterResource(AppIcons.Home))

    /** Ctrl+Enter from anywhere: connect the selected profile. */
    fun connectSelected(container: AppContainer) {
        val list = container.profiles.profiles.value
        val id = container.settings.settings.value.selectedProfileId
        val profile = list.firstOrNull { it.id == id } ?: list.firstOrNull() ?: return
        container.connection.connect(profile)
    }

    @Composable
    override fun Content() {
        val container = LocalAppContainer.current
        val model = rememberScreenModel { HomeScreenModel(container) }
        HomeScreen(model)
    }
}

@Composable
private fun HomeScreen(model: HomeScreenModel) {
    val profiles by model.profiles.profiles.collectAsState()
    val settings by model.settings.settings.collectAsState()
    val state by model.connection.state.collectAsState()
    val selected = model.selectedProfile(profiles, settings.selectedProfileId)
    val shell = LocalShell.current

    Column(Modifier.fillMaxSize().padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.xl)) {
        PageHeader("Главная", "Состояние подключения и трафик")
        Spacer(Modifier.height(AppTheme.spacing.xl))
        if (profiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "Добавьте первый профиль",
                    message = "Профиль — это нода и способ до неё добраться. Вставьте ссылку openflux:// от владельца ноды или QR-код, либо создайте профиль вручную.",
                    resource = AppIcons.Public,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
                        AppButton("Импорт ссылки или QR", {
                            shell.importRequested = true
                            shell.open(ProfilesTab)
                        }, leading = Icons.Rounded.ContentPaste)
                        AppButton("Создать вручную", {
                            shell.newProfileRequested = true
                            shell.open(ProfilesTab)
                        }, style = ButtonStyle.Secondary)
                    }
                }
            }
            return
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 820.dp
            val scroll = rememberScrollState()
            val scrollbars = LocalScrollbars.current
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xl)) {
                    ControlPanel(model, profiles, selected, state, settings.mode, Modifier.width(360.dp))
                    Box(Modifier.weight(1f)) {
                        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = AppTheme.spacing.m)) {
                            DetailsColumn(model, selected, state)
                        }
                        scrollbars.Vertical(scroll, Modifier.align(Alignment.CenterEnd))
                    }
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(end = AppTheme.spacing.m)) {
                        ControlPanel(model, profiles, selected, state, settings.mode, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(AppTheme.spacing.l))
                        DetailsColumn(model, selected, state)
                    }
                    scrollbars.Vertical(scroll, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }
}

/** The connect button with what it will connect: profile and mode. */
@Composable
private fun ControlPanel(
    model: HomeScreenModel,
    profiles: List<Profile>,
    selected: Profile?,
    state: ConnectionState,
    mode: ConnectionMode,
    modifier: Modifier,
) {
    val look = state.look()
    val platform = LocalAppContainer.current.platform
    var now by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state) {
        while (state is ConnectionState.Connected || state is ConnectionState.Reconnecting) {
            now = platform.now()
            kotlinx.coroutines.delay(1000)
        }
    }
    val traffic by model.connection.traffic.collectAsState()
    val since = when (state) {
        is ConnectionState.Connected -> state.since
        is ConnectionState.Reconnecting -> state.since
        else -> 0L
    }
    val detail = if (since > 0 && now > 0) Format.duration(now - since) else ""
    val speed = if (traffic.live && state is ConnectionState.Connected) "↓ ${Format.speed(traffic.downBytesPerSec)}  ↑ ${Format.speed(traffic.upBytesPerSec)}" else ""
    val canToggle = state !is ConnectionState.Disconnecting && (state.isActive || selected != null)

    AppCard(modifier, padding = AppTheme.spacing.xl) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ConnectButton(
                title = look.title,
                detail = detail,
                extra = speed,
                tone = look.tone,
                enabled = canToggle,
                onClick = { model.toggle(selected) },
            )
        }
        Text(
            when {
                state.isActive -> "Нажмите, чтобы отключиться · Ctrl+Enter"
                else -> "Нажмите, чтобы подключиться · Ctrl+Enter"
            },
            style = AppTheme.typography.caption,
            color = AppTheme.colors.textHint,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AppTheme.spacing.xl))
        SectionLabel("Профиль")
        Spacer(Modifier.height(AppTheme.spacing.s))
        ProfilePicker(profiles, selected, onSelect = model::select)
        Spacer(Modifier.height(AppTheme.spacing.l))
        SectionLabel("Режим")
        Spacer(Modifier.height(AppTheme.spacing.s))
        Segmented(
            options = ConnectionMode.entries,
            selected = mode,
            label = { if (it == ConnectionMode.Client) "Клиент" else "Выходная нода" },
            onSelect = model::setMode,
            enabled = !state.isActive,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(AppTheme.spacing.xs))
        Text(
            if (state.isActive) "Режим меняется после отключения" else mode.description,
            style = AppTheme.typography.caption,
            color = AppTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ProfilePicker(profiles: List<Profile>, selected: Profile?, onSelect: (Profile) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(AppTheme.shapes.card)
                .background(if (hovered) AppTheme.colors.surfaceTonal else AppTheme.colors.background)
                .appClickable(interaction) { open = true }
                .padding(AppTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBubble(AppIcons.byName(selected?.icon ?: "ic_public"))
            Spacer(Modifier.width(AppTheme.spacing.m))
            Column(Modifier.weight(1f)) {
                Text(selected?.name ?: "Профиль не выбран", style = AppTheme.typography.bodyStrong, color = AppTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(selected?.summary ?: "", style = AppTheme.typography.caption, color = AppTheme.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Rounded.UnfoldMore, "Выбрать профиль", tint = AppTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
        }
        AppMenu(open, { open = false }, profiles.map { profile ->
            MenuAction(profile.name + if (profile.id == selected?.id) "  ✓" else "", { onSelect(profile) })
        })
    }
}

@Composable
private fun DetailsColumn(model: HomeScreenModel, selected: Profile?, state: ConnectionState) {
    val toaster = LocalToaster.current
    val shell = LocalShell.current
    val traffic by model.connection.traffic.collectAsState()
    val socks by model.connection.socksAddress.collectAsState()
    val exitAddress by model.connection.exitAddress.collectAsState()
    val shareLink by model.connection.exitShareLink.collectAsState()
    val settings by model.settings.settings.collectAsState()
    val container = LocalAppContainer.current
    val profile = when (state) {
        is ConnectionState.Connecting -> state.profile
        is ConnectionState.Connected -> state.profile
        is ConnectionState.Reconnecting -> state.profile
        else -> selected
    } ?: return
    val exitMode = (state as? ConnectionState.Connected)?.mode == ConnectionMode.Exit ||
        (!state.isActive && settings.mode == ConnectionMode.Exit)

    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.l)) {
        if (state is ConnectionState.Failed) {
            Banner(state.message, Tone.Danger, icon = Icons.Rounded.ErrorOutline) {
                Row {
                    TextAction("Журнал", { shell.open(LogsTab) })
                    state.profile?.let { failed -> TextAction("Повторить", { model.connection.connect(failed) }) }
                }
            }
        }
        if (state is ConnectionState.Reconnecting) {
            Banner("Связь с нодой потеряна. Ядро переподключается само, трафик пойдёт, как только канал поднимется.", Tone.Warning)
        }

        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Трафик", style = AppTheme.typography.sectionTitle, color = AppTheme.colors.text, modifier = Modifier.weight(1f))
                if (traffic.activeTransport.isNotEmpty()) StatusBadge("через ${traffic.activeTransport}", Tone.Accent)
            }
            Spacer(Modifier.height(AppTheme.spacing.l))
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.l)) {
                Metric("Загрузка", Format.speed(traffic.downBytesPerSec), Icons.Rounded.ArrowDownward, Modifier.weight(1f))
                Metric("Отдача", Format.speed(traffic.upBytesPerSec), Icons.Rounded.ArrowUpward, Modifier.weight(1f))
                Metric("За сессию", Format.bytes(traffic.totalDown + traffic.totalUp), null, Modifier.weight(1f))
            }
            if (!traffic.live && state is ConnectionState.Connected) {
                Spacer(Modifier.height(AppTheme.spacing.s))
                Text("Ядро не сообщает статистику для профилей без режима Session.", style = AppTheme.typography.caption, color = AppTheme.colors.textHint)
            }
        }

        AppCard(padding = 0.dp) {
            Text("Подключение", style = AppTheme.typography.sectionTitle, color = AppTheme.colors.text, modifier = Modifier.padding(AppTheme.spacing.l))
            HorizontalRule()
            KeyValueRow("Профиль", profile.name)
            HorizontalRule()
            KeyValueRow("Транспорт", profile.summary)
            HorizontalRule()
            KeyValueRow("Шифрование", if (profile.secret.isNotEmpty()) "AES-256-GCM" else "Нет ключа")
            if (!exitMode) {
                HorizontalRule()
                val socksAddr = socks ?: "127.0.0.1:${settings.socksPort}"
                KeyValueRow("SOCKS5", socksAddr) {
                    AppIconButton("Копировать", {
                        model.copy(socksAddr)
                        toaster.show("Адрес SOCKS5 скопирован")
                    }, icon = Icons.Rounded.ContentCopy)
                }
                HorizontalRule()
                val httpAddr = "127.0.0.1:${settings.socksPort + 1}"
                KeyValueRow("HTTP-прокси", httpAddr) {
                    AppIconButton("Копировать", {
                        model.copy(httpAddr)
                        toaster.show("Адрес HTTP-прокси скопирован")
                    }, icon = Icons.Rounded.ContentCopy)
                }
                HorizontalRule()
                val (ipText, ipColor) = when (val address = exitAddress) {
                    ExitAddress.Unknown -> "—" to AppTheme.colors.textSecondary
                    ExitAddress.Checking -> "проверяю…" to AppTheme.colors.textSecondary
                    is ExitAddress.Known -> address.ip to AppTheme.colors.text
                    is ExitAddress.Unavailable -> "не удалось: ${address.reason}" to AppTheme.colors.danger
                }
                KeyValueRow("Внешний IP", ipText, valueColor = ipColor) {
                    AppIconButton("Проверить ещё раз", model.connection::refreshExitAddress, icon = Icons.Rounded.Refresh, enabled = state is ConnectionState.Connected)
                }
            }
        }

        if (!exitMode && container.platform.fullTunnelSupported) {
            AppCard(padding = AppTheme.spacing.s) {
                SwitchRow(
                    title = "Весь трафик компьютера",
                    description = "Все программы, игры и UDP идут через ноду, как VPN на Android. Нужны права администратора.",
                    checked = settings.fullTunnel,
                    onCheckedChange = model::setFullTunnel,
                )
                if (settings.fullTunnel && !container.platform.elevated) {
                    Banner(
                        "OpenFlux запущен без прав администратора, а они нужны этому режиму.",
                        Tone.Warning,
                        modifier = Modifier.padding(AppTheme.spacing.s),
                        action = {
                            TextAction("Перезапустить от имени администратора", {
                                if (!model.restartElevated()) toaster.show("Не удалось перезапустить: разрешите запуск в окне Windows", Tone.Warning)
                            })
                        },
                    )
                }
            }
        }

        if (!exitMode && !settings.fullTunnel && container.platform.systemProxySupported) {
            AppCard(padding = AppTheme.spacing.s) {
                SwitchRow(
                    title = "Системный прокси Windows",
                    description = "Браузеры и большинство программ пойдут через OpenFlux, пока он подключён. При отключении прежние настройки вернутся.",
                    checked = settings.systemProxy,
                    onCheckedChange = model::setSystemProxy,
                )
            }
        }

        if (exitMode) {
            AppCard {
                Text("Подключение по QR", style = AppTheme.typography.sectionTitle, color = AppTheme.colors.text)
                Spacer(Modifier.height(AppTheme.spacing.s))
                val link = shareLink
                if (link == null) {
                    Text(
                        if (state.isActive) "Ядро готовит ссылку для клиентов…" else "Запустите ноду: здесь появится QR-код для клиентов.",
                        style = AppTheme.typography.body,
                        color = AppTheme.colors.textSecondary,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QrCode(remember(link) { model.qr(link) }, 200.dp)
                        Spacer(Modifier.width(AppTheme.spacing.l))
                        Column(Modifier.widthIn(max = 320.dp)) {
                            Text(
                                "Отсканируйте в OpenFlux на телефоне или вставьте ссылку в OpenFlux на компьютере. В коде ключ шифрования: показывайте только своим.",
                                style = AppTheme.typography.body,
                                color = AppTheme.colors.textSecondary,
                            )
                            Spacer(Modifier.height(AppTheme.spacing.m))
                            AppButton("Копировать ссылку", {
                                model.copy(link)
                                toaster.show("Ссылка скопирована")
                            }, style = ButtonStyle.Secondary, leading = Icons.Rounded.ContentCopy)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, icon: ImageVector?, modifier: Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = AppTheme.colors.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(label, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, style = AppTheme.typography.metric, color = AppTheme.colors.text, maxLines = 1)
    }
}
