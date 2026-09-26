package io.openflux.desktop.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.ConnectionMode
import io.openflux.desktop.model.CoreSource
import io.openflux.desktop.model.ThemeMode
import io.openflux.desktop.model.isActive
import io.openflux.desktop.service.AppContainer
import io.openflux.desktop.service.LocalAppContainer
import io.openflux.desktop.ui.LocalScrollbars
import io.openflux.desktop.ui.components.AppButton
import io.openflux.desktop.ui.components.AppCard
import io.openflux.desktop.ui.components.AppIcons
import io.openflux.desktop.ui.components.AppTextField
import io.openflux.desktop.ui.components.Banner
import io.openflux.desktop.ui.components.ButtonStyle
import io.openflux.desktop.ui.components.HorizontalRule
import io.openflux.desktop.ui.components.IconBubble
import io.openflux.desktop.ui.components.KeyValueRow
import io.openflux.desktop.ui.components.PageHeader
import io.openflux.desktop.ui.components.SectionLabel
import io.openflux.desktop.ui.components.Segmented
import io.openflux.desktop.ui.components.SwitchRow
import io.openflux.desktop.ui.components.Tone
import io.openflux.desktop.ui.components.appClickable
import io.openflux.desktop.ui.shell.LocalShell
import io.openflux.desktop.ui.shell.WidthClass
import io.openflux.desktop.ui.theme.AppTheme
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class SettingsCategory(val title: String, val subtitle: String, val icon: DrawableResource) {
    Connection("Подключение", "Режим, порты, автозапуск", AppIcons.Swap),
    SystemProxy("Системный прокси", "Весь трафик Windows через OpenFlux", AppIcons.Routing),
    Core("Ядро OpenFlux", "Файл ядра и подробный журнал", AppIcons.Code),
    Interface("Интерфейс", "Тема, трей, журнал", AppIcons.DarkMode),
    About("О программе", "Версии и репозитории", AppIcons.Info),
}

class SettingsScreenModel(val container: AppContainer) : ScreenModel {
    var category by mutableStateOf(SettingsCategory.Connection)
    var mobileDetailOpen by mutableStateOf(false)
    var latestRelease by mutableStateOf<String?>(null)
    var checkingRelease by mutableStateOf(false)

    fun update(transform: (AppSettings) -> AppSettings) = container.settings.update(transform)
}

object SettingsTab : Tab {
    override val options: TabOptions
        @Composable get() = TabOptions(index = 3u, title = "Настройки", icon = painterResource(AppIcons.Settings))

    @Composable
    override fun Content() {
        val container = LocalAppContainer.current
        val model = rememberScreenModel { SettingsScreenModel(container) }
        SettingsScreen(model)
    }
}

@Composable
private fun SettingsScreen(model: SettingsScreenModel) {
    val compact = LocalShell.current.widthClass == WidthClass.Compact
    Row(Modifier.fillMaxSize()) {
        if (!compact || !model.mobileDetailOpen) {
            Column(
                (if (compact) Modifier.fillMaxSize() else Modifier.width(AppTheme.dimens.settingsNavWidth + AppTheme.spacing.xl * 2).fillMaxHeight())
                    .padding(horizontal = AppTheme.spacing.xl, vertical = AppTheme.spacing.xl),
            ) {
                PageHeader("Настройки", "Применяются сразу")
                Spacer(Modifier.height(AppTheme.spacing.xl))
                SettingsCategory.entries.forEach { category ->
                    CategoryRow(category, selected = !compact && category == model.category) {
                        model.category = category
                        model.mobileDetailOpen = true
                    }
                    Spacer(Modifier.height(AppTheme.spacing.xs))
                }
            }
        }
        if (!compact || model.mobileDetailOpen) {
            if (!compact) Box(Modifier.width(1.dp).fillMaxHeight().background(AppTheme.colors.border))
            val scroll = rememberScrollState()
            val scrollbars = LocalScrollbars.current
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.xl)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (compact) {
                            io.openflux.desktop.ui.components.AppIconButton("Назад", { model.mobileDetailOpen = false }, resource = AppIcons.Back)
                            Spacer(Modifier.width(AppTheme.spacing.s))
                        }
                        Text(model.category.title, style = AppTheme.typography.pageTitle, color = AppTheme.colors.text)
                    }
                    Spacer(Modifier.height(AppTheme.spacing.xl))
                    Column(Modifier.widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.l)) {
                        when (model.category) {
                            SettingsCategory.Connection -> ConnectionSettings(model)
                            SettingsCategory.SystemProxy -> SystemProxySettings(model)
                            SettingsCategory.Core -> CoreSettings(model)
                            SettingsCategory.Interface -> InterfaceSettings(model)
                            SettingsCategory.About -> AboutSettings(model)
                        }
                    }
                }
                scrollbars.Vertical(scroll, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
private fun CategoryRow(category: SettingsCategory, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val fill by animateColorAsState(
        when {
            selected -> colors.accentSoft
            hovered -> colors.surfaceTonal
            else -> Color.Transparent
        },
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.card)
            .background(fill)
            .appClickable(interaction, onClick = onClick)
            .padding(AppTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(category.icon)
        Spacer(Modifier.width(AppTheme.spacing.m))
        Column(Modifier.weight(1f)) {
            Text(category.title, style = AppTheme.typography.bodyStrong, color = colors.text)
            Text(category.subtitle, style = AppTheme.typography.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActiveNotice(model: SettingsScreenModel) {
    val state by model.container.connection.state.collectAsState()
    if (state.isActive) Banner("OpenFlux подключён: эти параметры вступят в силу при следующем подключении.", Tone.Warning)
}

@Composable
private fun ConnectionSettings(model: SettingsScreenModel) {
    val settings by model.container.settings.settings.collectAsState()
    ActiveNotice(model)
    AppCard {
        SectionLabel("Режим работы")
        Spacer(Modifier.height(AppTheme.spacing.s))
        Segmented(ConnectionMode.entries, settings.mode, { it.label }, { m -> model.update { it.copy(mode = m) } }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(AppTheme.spacing.xs))
        Text(settings.mode.description, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
    }
    AppCard {
        SectionLabel("Локальный прокси")
        Spacer(Modifier.height(AppTheme.spacing.m))
        var port by remember(settings.socksPort) { mutableStateOf(settings.socksPort.toString()) }
        val parsed = port.toIntOrNull()
        val valid = parsed != null && parsed in 1024..65534
        AppTextField(
            value = port,
            onValueChange = { v ->
                port = v.filter(Char::isDigit).take(5)
                port.toIntOrNull()?.takeIf { it in 1024..65534 }?.let { p -> model.update { it.copy(socksPort = p) } }
            },
            label = "Порт SOCKS5",
            keyboardType = KeyboardType.Number,
            error = if (!valid) "Порт от 1024 до 65534" else null,
            helper = if (valid) "SOCKS5: 127.0.0.1:$parsed · HTTP-прокси: 127.0.0.1:${parsed!! + 1}" else null,
            modifier = Modifier.width(260.dp),
        )
        Spacer(Modifier.height(AppTheme.spacing.s))
        Text(
            "Прокси слушает только этот компьютер. Укажите его в браузере или программе либо включите системный прокси Windows.",
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
    }
    AppCard(padding = AppTheme.spacing.s) {
        SwitchRow(
            "Подключаться при запуске",
            "Сразу подключить выбранный профиль, когда OpenFlux открывается",
            settings.autoConnect,
            { v -> model.update { it.copy(autoConnect = v) } },
        )
    }
    AppCard {
        SectionLabel("Выходная нода")
        Spacer(Modifier.height(AppTheme.spacing.m))
        var directPort by remember(settings.exitDirectPort) { mutableStateOf(settings.exitDirectPort.toString()) }
        Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.m)) {
            AppTextField(
                value = directPort,
                onValueChange = { v ->
                    directPort = v.filter(Char::isDigit).take(5)
                    directPort.toIntOrNull()?.takeIf { it in 1..65535 }?.let { p -> model.update { it.copy(exitDirectPort = p) } }
                },
                label = "Порт direct",
                keyboardType = KeyboardType.Number,
                modifier = Modifier.width(160.dp),
            )
            AppTextField(
                value = settings.exitShareHost,
                onValueChange = { v -> model.update { it.copy(exitShareHost = v.trim()) } },
                label = "Адрес для клиентов",
                placeholder = "Определить автоматически",
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(AppTheme.spacing.s))
        Text(
            "Нода принимает клиентов через документ профиля и напрямую по TCP на этот порт. Адрес попадает в QR-код; оставьте пустым, чтобы ядро подставило внешний IP.",
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun SystemProxySettings(model: SettingsScreenModel) {
    val settings by model.container.settings.settings.collectAsState()
    if (!model.container.platform.systemProxySupported) {
        Banner("Системный прокси пока поддерживается только в Windows. Укажите SOCKS5 127.0.0.1:${settings.socksPort} в настройках нужных программ.", Tone.Neutral)
        return
    }
    AppCard(padding = AppTheme.spacing.s) {
        SwitchRow(
            "Использовать системный прокси",
            "Пока OpenFlux подключён, Windows направляет браузеры и программы на его HTTP-прокси",
            settings.systemProxy,
            { v -> model.update { it.copy(systemProxy = v) } },
        )
    }
    AppCard(padding = 0.dp) {
        KeyValueRow("Адрес", "127.0.0.1:${settings.socksPort + 1}")
        HorizontalRule()
        KeyValueRow("Исключения", "localhost, 127.*, 10.*, 192.168.*")
    }
    Text(
        "Прежние настройки прокси Windows сохраняются и возвращаются при отключении, при выходе и после сбоя при следующем запуске. " +
            "Программы, которые не смотрят на системный прокси (игры, некоторые мессенджеры), можно направить на SOCKS5 127.0.0.1:${settings.socksPort} вручную.",
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
    )
}

@Composable
private fun CoreSettings(model: SettingsScreenModel) {
    val settings by model.container.settings.settings.collectAsState()
    val platform = model.container.platform
    ActiveNotice(model)
    AppCard {
        SectionLabel("Файл ядра")
        Spacer(Modifier.height(AppTheme.spacing.s))
        Segmented(CoreSource.entries, settings.coreSource, { it.label }, { s -> model.update { it.copy(coreSource = s) } }, Modifier.width(360.dp))
        Spacer(Modifier.height(AppTheme.spacing.m))
        if (settings.coreSource == CoreSource.Bundled) {
            KeyValueRow("Встроенное ядро", platform.coreVersion)
            Text(
                "Сборка ядра OpenFlux из форка с исправлениями Volga и поддержкой статуса для этого приложения.",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                AppTextField(
                    value = settings.customCorePath,
                    onValueChange = { v -> model.update { it.copy(customCorePath = v) } },
                    label = "Путь к файлу ядра",
                    placeholder = "C:\\OpenFlux\\openflux-windows-amd64.exe",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(AppTheme.spacing.s))
                AppButton("Выбрать…", {
                    platform.pickFile("Файл ядра OpenFlux", listOf("exe"))?.let { path -> model.update { it.copy(customCorePath = path) } }
                }, style = ButtonStyle.Secondary, leading = Icons.Rounded.FolderOpen)
            }
            Spacer(Modifier.height(AppTheme.spacing.s))
            Text(
                "Для статуса, статистики и проверок Яндекса нужно ядро с поддержкой IPC (эта версия приложения поставляется с таким).",
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
    AppCard(padding = AppTheme.spacing.s) {
        SwitchRow(
            "Подробный журнал ядра",
            "Флаг --debug: каждое действие транспорта попадает в журнал. Нужен для диагностики, замедляет работу",
            settings.verboseCoreLog,
            { v -> model.update { it.copy(verboseCoreLog = v) } },
        )
    }
}

@Composable
private fun InterfaceSettings(model: SettingsScreenModel) {
    val settings by model.container.settings.settings.collectAsState()
    AppCard {
        SectionLabel("Тема")
        Spacer(Modifier.height(AppTheme.spacing.s))
        Segmented(ThemeMode.entries, settings.theme, { it.label }, { t -> model.update { it.copy(theme = t) } }, Modifier.width(420.dp))
    }
    AppCard(padding = AppTheme.spacing.s) {
        SwitchRow(
            "Сворачивать в трей при закрытии",
            "Окно прячется в область уведомлений, соединение остаётся. Выйти можно из меню значка",
            settings.closeToTray,
            { v -> model.update { it.copy(closeToTray = v) } },
        )
        SwitchRow(
            "Автопрокрутка журнала",
            "Показывать новые строки журнала сразу",
            settings.logAutoScroll,
            { v -> model.update { it.copy(logAutoScroll = v) } },
        )
        SwitchRow(
            "Скрывать адреса и ключи в журнале",
            "Ссылки на документы, ключи и openflux:// заменяются на пометки — удобно для скриншотов",
            settings.maskSensitive,
            { v -> model.update { it.copy(maskSensitive = v) } },
        )
    }
}

@Composable
private fun AboutSettings(model: SettingsScreenModel) {
    val platform = model.container.platform
    val scope = rememberCoroutineScope()
    AppCard(padding = 0.dp) {
        KeyValueRow("Версия приложения", platform.appVersion)
        HorizontalRule()
        KeyValueRow("Ядро OpenFlux", platform.coreVersion)
        HorizontalRule()
        KeyValueRow(
            "Последний выпуск",
            when {
                model.checkingRelease -> "проверяю…"
                model.latestRelease != null -> model.latestRelease!!
                else -> "не проверялось"
            },
        )
    }
    AppButton("Проверить обновления", {
        model.checkingRelease = true
        scope.launch {
            model.latestRelease = platform.latestRelease() ?: "не найден"
            model.checkingRelease = false
        }
    }, style = ButtonStyle.Secondary)
    SectionLabel("Репозитории")
    AppCard(padding = AppTheme.spacing.s) {
        LinkRow("OpenFlux (ядро)", "github.com/p1neappleXpress/OpenFlux", "https://github.com/p1neappleXpress/OpenFlux", platform::openUrl)
        LinkRow("OpenFlux Android", "github.com/damnurmum/OpenFlux-Android", "https://github.com/damnurmum/OpenFlux-Android", platform::openUrl)
        LinkRow("Форк с мастером нод", "github.com/meepo161/openfluxandroidfork", "https://github.com/meepo161/openfluxandroidfork", platform::openUrl)
    }
    Text(
        "OpenFlux — экспериментальный проект без независимого аудита безопасности. Используйте свои ноды и не публикуйте ключи и ссылки openflux://.",
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colors.textSecondary,
    )
}

@Composable
private fun LinkRow(title: String, subtitle: String, url: String, open: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.field)
            .background(if (hovered) AppTheme.colors.surfaceTonal else Color.Transparent)
            .appClickable(interaction) { open(url) }
            .padding(AppTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(AppIcons.GitHub)
        Spacer(Modifier.width(AppTheme.spacing.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.typography.bodyStrong, color = AppTheme.colors.text)
            Text(subtitle, style = AppTheme.typography.caption, color = AppTheme.colors.textSecondary)
        }
        androidx.compose.material3.Icon(Icons.Rounded.OpenInNew, "Открыть", tint = AppTheme.colors.textHint)
    }
}
