package io.openflux.desktop.ui.node

import io.openflux.desktop.ui.PlatformBackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.openflux.desktop.service.LocalAppContainer
import io.openflux.desktop.ui.LocalBrowserViews
import io.openflux.desktop.ui.LocalScrollbars
import io.openflux.desktop.ui.components.AppButton
import io.openflux.desktop.ui.components.AppCard
import io.openflux.desktop.ui.components.AppDialog
import io.openflux.desktop.ui.components.AppIconButton
import io.openflux.desktop.ui.components.AppIcons
import io.openflux.desktop.ui.components.AppTextField
import io.openflux.desktop.ui.components.Banner
import io.openflux.desktop.ui.components.ButtonStyle
import io.openflux.desktop.ui.components.HorizontalRule
import io.openflux.desktop.ui.components.KeyValueRow
import io.openflux.desktop.ui.components.LocalToaster
import io.openflux.desktop.ui.components.QrCode
import io.openflux.desktop.ui.components.SectionLabel
import io.openflux.desktop.ui.components.Segmented
import io.openflux.desktop.ui.components.TextAction
import io.openflux.desktop.ui.components.Tone
import io.openflux.desktop.ui.components.windowSize
import io.openflux.desktop.ui.LocalTouchUi
import io.openflux.desktop.ui.theme.AppTheme

/**
 * The "Своя нода" wizard in the Profiles section. [onClose] leaves it;
 * [onSaved] opens the saved profile.
 */
@Composable
fun NodeWizardPane(model: NodeWizardModel, onClose: () -> Unit, onSaved: (String) -> Unit) {
    var confirmClose by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }
    val requestClose = {
        if (model.busy == null) {
            if (model.unsaved) confirmClose = true else onClose()
        }
    }
    // Back steps back through the wizard, then leaves it.
    PlatformBackHandler(enabled = LocalTouchUi.current && model.busy == null) {
        if (model.step == WizardStep.Document || model.step == WizardStep.Plan) model.back() else requestClose()
    }
    val scroll = rememberScrollState()
    val scrollbars = LocalScrollbars.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (model.busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp), color = AppTheme.colors.accent)
            else Spacer(Modifier.height(3.dp))
            Column(
                Modifier.fillMaxSize().verticalScroll(scroll)
                    .padding(horizontal = AppTheme.spacing.page, vertical = AppTheme.spacing.xl),
            ) {
                Column(Modifier.widthIn(max = 640.dp)) {
                    Header(model, requestClose)
                    Spacer(Modifier.height(AppTheme.spacing.xl))
                    when (model.step) {
                        WizardStep.Server -> ServerStep(model)
                        WizardStep.Document -> DocumentStep(model)
                        WizardStep.Plan -> PlanStep(model)
                        WizardStep.Verify -> VerifyStep(model)
                        WizardStep.Done -> DoneStep(model, onShowQr = { qrOpen = true }, onSaved = onSaved, onClose = requestClose)
                    }
                    Status(model)
                }
            }
        }
        scrollbars.Vertical(scroll, Modifier.align(Alignment.CenterEnd))
    }

    model.hostKeyPrompt?.let { prompt ->
        AppDialog(
            title = if (prompt.mismatch) "Ключ сервера изменился" else "Новый сервер",
            onDismiss = model::dismissHostKey,
            primary = if (prompt.mismatch) "Сервер переустановлен" else "Доверять",
            primaryStyle = if (prompt.mismatch) ButtonStyle.Danger else ButtonStyle.Primary,
            onPrimary = model::trustHostKey,
        ) {
            Text(if (prompt.mismatch) "Сервер представился другим ключом:" else "Отпечаток ключа сервера:",
                style = AppTheme.typography.body, color = AppTheme.colors.text)
            Spacer(Modifier.height(AppTheme.spacing.s))
            SelectionContainer { Text(prompt.fingerprint, style = AppTheme.typography.mono, color = AppTheme.colors.text) }
            Spacer(Modifier.height(AppTheme.spacing.m))
            Text(
                if (prompt.mismatch) {
                    "Так бывает после переустановки системы, но так же выглядит и подмена сервера. " +
                        "Продолжайте, только если вы сами переустанавливали сервер."
                } else {
                    "Если панель провайдера показывает отпечаток, сверьте его. Доверять этому серверу?"
                },
                style = AppTheme.typography.body,
                color = AppTheme.colors.textSecondary,
            )
        }
    }
    if (confirmClose) {
        AppDialog(
            title = "Выйти без сохранения?",
            onDismiss = { confirmClose = false },
            primary = "Сохранить профиль",
            onPrimary = {
                confirmClose = false
                model.save()?.let { onSaved(it.id) }
            },
            secondary = "Остаться",
        ) {
            Text("Канал уже установлен на сервере, но профиль не сохранён: без него к каналу не подключиться.",
                style = AppTheme.typography.body, color = AppTheme.colors.text)
            Spacer(Modifier.height(AppTheme.spacing.m))
            TextAction("Выйти, не сохраняя", { confirmClose = false; onClose() }, color = AppTheme.colors.danger)
        }
    }
    if (qrOpen) {
        AppDialog(title = "Отсканируйте в OpenFlux", onDismiss = { qrOpen = false }, secondary = "Закрыть") {
            val matrix = remember(model.shareLink) { model.qr() }
            val hint = "На другом устройстве: Профили → Импорт → Сканировать QR. В коде ключ канала: показывайте его только тому, кто будет им пользоваться."
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val width = maxWidth
                if (width >= 440.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QrCode(matrix, 220.dp)
                        Spacer(Modifier.width(AppTheme.spacing.l))
                        Text(hint, style = AppTheme.typography.body, color = AppTheme.colors.textSecondary)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        QrCode(matrix, minOf(width, 260.dp))
                        Spacer(Modifier.height(AppTheme.spacing.l))
                        Text(hint, style = AppTheme.typography.body, color = AppTheme.colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(model: NodeWizardModel, onClose: () -> Unit) {
    val (title, subtitle) = when (model.step) {
        WizardStep.Server -> "Своя нода" to "Сервер (VDS) с Linux и systemd: Debian, Ubuntu и похожие."
        WizardStep.Document -> "Документ канала" to "Через этот документ Яндекса устройство и нода обмениваются зашифрованным трафиком."
        WizardStep.Plan -> "Будут изменения" to "На сервере будет сделано только это."
        WizardStep.Verify -> "Проверка канала" to "Подключаюсь к новой ноде и открываю сайт через неё."
        WizardStep.Done -> "Нода готова" to if (model.verifiedIp.isEmpty()) "Канал установлен, но проверка не завершена."
            else "Трафик выходит в интернет с адреса ${model.verifiedIp}."
    }
    Row(verticalAlignment = Alignment.Top) {
        if (model.step == WizardStep.Document || model.step == WizardStep.Plan) {
            AppIconButton("Назад", model::back, resource = AppIcons.Back, enabled = model.busy == null)
            Spacer(Modifier.width(AppTheme.spacing.s))
        }
        Column(Modifier.weight(1f)) {
            Text("Шаг ${model.step.number} из 4", style = AppTheme.typography.label, color = AppTheme.colors.accent)
            Spacer(Modifier.height(AppTheme.spacing.xs))
            Text(title, style = AppTheme.typography.pageTitle, color = AppTheme.colors.text)
            Text(subtitle, style = AppTheme.typography.body, color = AppTheme.colors.textSecondary)
        }
        AppButton("Закрыть", onClose, style = ButtonStyle.Ghost, enabled = model.busy == null)
    }
}

@Composable
private fun Status(model: NodeWizardModel) {
    val busy = model.busy
    val error = model.error
    val notice = model.notice
    // The document step shows the browser's progress next to the browser.
    if ((busy == null || model.documentProgress != null) && error == null && notice == null) return
    Spacer(Modifier.height(AppTheme.spacing.l))
    when {
        busy != null && model.documentProgress == null -> Banner(busy, Tone.Accent, icon = Icons.Rounded.Info)
        error != null -> Banner(error, Tone.Danger, icon = Icons.Rounded.ErrorOutline)
        notice != null -> Banner(notice, Tone.Success, icon = Icons.Rounded.CheckCircle)
    }
}

@Composable
private fun Note(text: String) {
    Spacer(Modifier.height(AppTheme.spacing.s))
    Text(text, style = AppTheme.typography.bodySmall, color = AppTheme.colors.textSecondary)
}

@Composable
private fun Actions(content: @Composable () -> Unit) {
    Spacer(Modifier.height(AppTheme.spacing.xl))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
        content()
    }
}

// ---- step 1 ----

@Composable
private fun ColumnScope.ServerStep(model: NodeWizardModel) {
    val settings by LocalAppContainer.current.settings.settings.collectAsState()
    val idle = model.busy == null
    if (settings.knownServers.isNotEmpty()) {
        SectionLabel("Недавние серверы")
        Note("Новый канал встанет рядом с уже установленными: так добавляют ещё одного пользователя.")
        Spacer(Modifier.height(AppTheme.spacing.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)) {
            settings.knownServers.forEach { server ->
                AppButton("${server.user}@${server.host}:${server.port}", { model.useServer(server) }, style = ButtonStyle.Secondary, enabled = idle)
            }
        }
        Spacer(Modifier.height(AppTheme.spacing.xl))
    }
    SectionLabel("Сервер")
    Spacer(Modifier.height(AppTheme.spacing.s))
    Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.m)) {
        AppTextField(model.host, { model.host = it.trim() }, Modifier.weight(1f), label = "Адрес (IP или домен)", placeholder = "203.0.113.10", enabled = idle)
        AppTextField(model.port, { model.port = it.filter(Char::isDigit).take(5) }, Modifier.width(110.dp), label = "SSH-порт",
            keyboardType = KeyboardType.Number, enabled = idle)
    }
    Spacer(Modifier.height(AppTheme.spacing.m))
    AppTextField(model.user, { model.user = it.trim() }, label = "Логин", enabled = idle)
    Spacer(Modifier.height(AppTheme.spacing.l))
    SectionLabel("Вход")
    Spacer(Modifier.height(AppTheme.spacing.s))
    Segmented(listOf(false, true), model.useKey, { if (it) "Ключ" else "Пароль" }, { model.useKey = it }, enabled = idle)
    Spacer(Modifier.height(AppTheme.spacing.m))
    if (model.useKey) {
        AppTextField(model.privateKey, { model.privateKey = it }, label = "Приватный ключ (OpenSSH или PEM)",
            placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----", singleLine = false, minLines = 4, monospace = true, enabled = idle)
        Spacer(Modifier.height(AppTheme.spacing.s))
        AppButton("Выбрать файл ключа…", model::readKeyFile, style = ButtonStyle.Secondary, leading = Icons.Rounded.FolderOpen, enabled = idle)
        Spacer(Modifier.height(AppTheme.spacing.m))
        AppTextField(model.passphrase, { model.passphrase = it }, label = "Пароль ключа (если есть)", secret = true, enabled = idle)
    } else {
        AppTextField(model.password, { model.password = it }, label = "Пароль", secret = true, enabled = idle)
    }
    Note("Пароль и ключ нужны только на время установки: OpenFlux их не сохраняет.")
    Actions {
        AppButton(if (idle) "Подключиться" else "Подключаюсь…", { model.connect() }, enabled = idle)
    }
}

// ---- step 2 ----

@Composable
private fun ColumnScope.DocumentStep(model: NodeWizardModel) {
    val idle = model.busy == null
    val page by model.documentPage.collectAsState()
    val progress = model.documentProgress
    val creating = progress != null
    model.probe?.let { probe ->
        AppCard(padding = 0.dp) {
            KeyValueRow("Сервер", "${model.user.trim()}@${model.host.trim()}")
            HorizontalRule()
            KeyValueRow("Система", listOf(probe.os, probe.arch).filter(String::isNotBlank).joinToString(", "))
            if (probe.channels.isNotEmpty()) {
                HorizontalRule()
                KeyValueRow("Каналов OpenFlux", probe.channels.size.toString())
            }
        }
        Spacer(Modifier.height(AppTheme.spacing.l))
    }
    AppTextField(model.name, { model.name = it }, label = "Название профиля", enabled = idle)
    model.channel?.let { Note("Канал: ${it.id}. Для него создан отдельный ключ шифрования, его знают только это устройство и нода.") }
    Actions {
        if (creating) {
            AppButton("Отменить вход в Яндекс", model::cancelDocument, style = ButtonStyle.Secondary)
        } else {
            AppButton("Войти в Яндекс и создать документ", model::createDocument, leadingResource = AppIcons.Yandex, enabled = idle)
        }
    }
    if (progress != null) {
        Spacer(Modifier.height(AppTheme.spacing.m))
        Banner(progress, Tone.Accent, icon = Icons.Rounded.Info)
    }
    val shown = page
    if (shown != null) {
        Spacer(Modifier.height(AppTheme.spacing.m))
        Box(
            Modifier.fillMaxWidth().height((windowSize().height - 200.dp).coerceIn(360.dp, 760.dp)).clip(AppTheme.shapes.card)
                .border(1.dp, AppTheme.colors.border, AppTheme.shapes.card),
        ) {
            LocalBrowserViews.current.Page(shown, Modifier.fillMaxSize())
        }
    } else {
        Note(
            "Вход откроется прямо здесь, во встроенном браузере. Документ появится в папке openflux на вашем Яндекс Диске " +
                "с доступом «Редактирование» по ссылке. После этого браузер забывает вход, остаются только cookies для ноды.",
        )
    }
    Spacer(Modifier.height(AppTheme.spacing.xl))
    SectionLabel("Или свой пустой документ")
    Spacer(Modifier.height(AppTheme.spacing.s))
    AppTextField(model.documentInput, { model.documentInput = it.trim() }, placeholder = "https://disk.yandex.ru/edit/d/…",
        monospace = true, enabled = idle, helper = "Ссылка с доступом «Редактирование» из «Поделиться»")
    Actions {
        AppButton("Проверить ссылку", model::checkDocument, style = ButtonStyle.Secondary,
            enabled = idle && model.documentInput.isNotBlank())
    }
}

// ---- step 3 ----

@Composable
private fun ColumnScope.PlanStep(model: NodeWizardModel) {
    val plan = model.plan ?: return
    val idle = model.busy == null
    AppCard {
        plan.actions.forEach { action ->
            Row(Modifier.padding(vertical = AppTheme.spacing.xs)) {
                Text("•", style = AppTheme.typography.body, color = AppTheme.colors.accent)
                Spacer(Modifier.width(AppTheme.spacing.s))
                Text(action, style = AppTheme.typography.body, color = AppTheme.colors.text)
            }
        }
    }
    Spacer(Modifier.height(AppTheme.spacing.m))
    AppCard(padding = 0.dp) {
        KeyValueRow("Канал", plan.channel.ifEmpty { model.channel?.id.orEmpty() })
        HorizontalRule()
        KeyValueRow("Порт резервного канала", plan.port.toString())
        HorizontalRule()
        KeyValueRow("Документ", model.documentUrl)
    }
    val keep = if (plan.untouched.isNotEmpty()) "Каналы, которые уже есть на сервере, не изменятся: ${plan.untouched.joinToString()}. " else ""
    Note(keep + "Остальные программы на сервере (Docker, VPN, панели) мастер не трогает.")
    if (model.nodeSignedIn) {
        Spacer(Modifier.height(AppTheme.spacing.m))
        Banner(
            "Нода будет открывать документ под вашим аккаунтом Яндекса: так Яндекс не требует от сервера капчу. " +
                "Кто получит root на сервере, получит и доступ к этому аккаунту, поэтому лучше входить отдельным аккаунтом для документов.",
            Tone.Warning,
            icon = Icons.Rounded.Warning,
            action = { TextAction("Не передавать", model::forgetYandexSignIn, enabled = idle) },
        )
    }
    if (model.documentWarning.isNotEmpty()) {
        Spacer(Modifier.height(AppTheme.spacing.m))
        Banner(model.documentWarning, Tone.Neutral, icon = Icons.Rounded.Info)
    }
    if (model.needsSudoPassword) {
        Spacer(Modifier.height(AppTheme.spacing.l))
        AppTextField(model.sudoPassword, { model.sudoPassword = it }, label = "Пароль sudo пользователя ${model.user.trim()}",
            secret = true, enabled = idle, helper = "Для установки нужны права root")
    }
    Actions {
        AppButton(if (idle) "Установить ноду" else "Устанавливаю…", model::install, enabled = idle)
        AppButton("Назад", model::back, style = ButtonStyle.Secondary, enabled = idle)
    }
}

// ---- step 4 ----

@Composable
private fun ColumnScope.VerifyStep(model: NodeWizardModel) {
    val failed = model.verifyFailed
    if (failed == null) {
        Text(
            "Если Яндекс попросит ноду пройти проверку, откроется окно с капчей: она показывается с адреса сервера, пройдите её как обычно.",
            style = AppTheme.typography.body,
            color = AppTheme.colors.textSecondary,
        )
        Actions {
            AppButton("Хватит ждать Яндекс", model::stopWaiting, style = ButtonStyle.Secondary)
        }
        return
    }
    Banner(failed, Tone.Danger, icon = Icons.Rounded.ErrorOutline)
    val idle = model.busy == null
    model.channel?.let { channel ->
        Note(
            if (model.installed) "Нода установлена на сервере как openflux-node@${channel.id}. Можно повторить проверку или удалить канал с сервера."
            else "Канал ${channel.id} удалён с сервера.",
        )
    }
    Actions {
        AppButton("Повторить проверку", model::verify, leading = Icons.Rounded.Refresh, enabled = idle && model.installed)
        if (model.installed) {
            AppButton("Удалить канал с сервера", model::removeChannel, style = ButtonStyle.Secondary, leading = Icons.Rounded.Delete, enabled = idle)
            AppButton("Сохранить профиль всё равно", model::skipVerification, style = ButtonStyle.Ghost, enabled = idle)
        }
    }
}

// ---- done ----

@Composable
private fun ColumnScope.DoneStep(model: NodeWizardModel, onShowQr: () -> Unit, onSaved: (String) -> Unit, onClose: () -> Unit) {
    val toaster = LocalToaster.current
    if (model.verifiedIp.isNotEmpty() && !model.primaryUp) {
        Banner(
            "Сейчас работает резервный канал (прямое подключение к серверу). Канал через Яндекс ещё не поднялся: " +
                "когда нода попросит проверку, OpenFlux покажет её, пройдите её.",
            Tone.Warning,
            icon = Icons.Rounded.Warning,
        )
        Spacer(Modifier.height(AppTheme.spacing.m))
    }
    AppCard(padding = 0.dp) {
        KeyValueRow("Профиль", model.name.trim().ifEmpty { model.profile?.name.orEmpty() })
        HorizontalRule()
        KeyValueRow("Канал", "${model.channel?.id.orEmpty()}, порт ${model.plan?.port ?: 0}")
        if (model.verifiedIp.isNotEmpty()) {
            HorizontalRule()
            KeyValueRow("Внешний адрес", model.verifiedIp, valueColor = AppTheme.colors.success)
        }
    }
    Note("Чтобы добавить ещё одного пользователя, запустите мастер снова: появится отдельный канал со своим документом и ключом.")
    Actions {
        if (model.saved) {
            AppButton("Открыть профиль", { model.profile?.let { onSaved(it.id) } }, leading = Icons.Rounded.CheckCircle)
        } else {
            AppButton("Сохранить на этом устройстве", {
                model.save()?.let { toaster.show("Профиль «${it.name}» сохранён", Tone.Success) }
            })
        }
        AppButton("QR для другого устройства", onShowQr, style = ButtonStyle.Secondary, leading = Icons.Rounded.QrCode2)
        AppButton("Копировать ссылку", {
            model.copyLink()
            toaster.show("Ссылка скопирована", Tone.Success)
        }, style = ButtonStyle.Secondary, leading = Icons.Rounded.ContentCopy)
    }
    Spacer(Modifier.height(AppTheme.spacing.m))
    Banner("В QR и ссылке лежит ключ канала: передавайте их только тому, кто будет им пользоваться.", Tone.Neutral, icon = Icons.Rounded.Lock)
    Actions {
        AppButton("Готово", onClose, style = ButtonStyle.Ghost)
    }
}
