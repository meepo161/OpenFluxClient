package io.openflux.desktop.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.ProfileSource
import io.openflux.desktop.model.TransportType
import io.openflux.desktop.ui.components.AppButton
import io.openflux.desktop.ui.components.AppCard
import io.openflux.desktop.ui.components.AppDialog
import io.openflux.desktop.ui.components.AppTextField
import io.openflux.desktop.ui.components.Banner
import io.openflux.desktop.ui.components.ButtonRow
import io.openflux.desktop.ui.components.ButtonStyle
import io.openflux.desktop.ui.components.KeyValueRow
import io.openflux.desktop.ui.components.LocalToaster
import io.openflux.desktop.ui.components.QrCode
import io.openflux.desktop.ui.components.Tone
import io.openflux.desktop.ui.theme.AppTheme

/**
 * Adds a profile from an `openflux://` link: typed or pasted, from the
 * clipboard, or read from a QR code (image file or image on the clipboard).
 */
@Composable
fun ImportDialog(model: ProfilesScreenModel) {
    val toaster = LocalToaster.current
    var text by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(ProfileSource.Link) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val clip = model.clipboardText()
        if (clip.startsWith("openflux://")) text = clip
    }
    val preview = model.preview(text, source)
    val ready = preview as? ImportPreview.Ready
    AppDialog(
        title = "Импорт профиля",
        onDismiss = { model.importOpen = false },
        primary = "Добавить профиль",
        primaryEnabled = ready != null,
        onPrimary = {
            ready?.let {
                val profile = model.import(it)
                toaster.show("Профиль «${profile.name}» добавлен", Tone.Success)
            }
        },
    ) {
        Text(
            "Ссылка openflux:// или QR-код приходят от владельца ноды: в Android-приложении это «QR и ссылка», в режиме ноды — карточка на главной.",
            style = AppTheme.typography.body,
            color = AppTheme.colors.textSecondary,
        )
        Spacer(Modifier.height(AppTheme.spacing.l))
        AppTextField(
            value = text,
            onValueChange = { text = it.trim(); source = ProfileSource.Link; notice = null },
            placeholder = "openflux://v1/…",
            monospace = true,
            singleLine = false,
            minLines = 3,
            error = (preview as? ImportPreview.Invalid)?.message,
        )
        Spacer(Modifier.height(AppTheme.spacing.m))
        ButtonRow {
            AppButton("Из буфера", {
                text = model.clipboardText(); source = ProfileSource.Link; notice = null
            }, style = ButtonStyle.Secondary, leading = Icons.Rounded.ContentPaste)
            AppButton("QR из файла…", {
                val (found, picked) = model.qrFromFile()
                if (found != null) { text = found; source = ProfileSource.Qr; notice = null }
                else if (picked) notice = "На картинке не найден QR-код"
            }, style = ButtonStyle.Secondary, leading = Icons.Rounded.Image)
            AppButton("QR из буфера", {
                val found = model.qrFromClipboard()
                if (found != null) { text = found; source = ProfileSource.Qr; notice = null }
                else notice = "В буфере нет картинки с QR-кодом"
            }, style = ButtonStyle.Secondary)
        }
        notice?.let {
            Spacer(Modifier.height(AppTheme.spacing.s))
            Banner(it, Tone.Warning, icon = Icons.Rounded.ErrorOutline)
        }
        if (ready != null) {
            Spacer(Modifier.height(AppTheme.spacing.l))
            val config = ready.config
            AppCard(padding = 0.dp) {
                KeyValueRow("Название", config.name.ifBlank { "OpenFlux" })
                KeyValueRow("Режим", if (config.negotiate) "Session" else "Обычный")
                KeyValueRow("Транспорты", config.transports.joinToString(" + ") { TransportType.fromCli(it.type)?.shortLabel ?: it.type })
                KeyValueRow("Ключ шифрования", if (config.secret.isNotEmpty()) "есть" else "нет")
            }
            Spacer(Modifier.height(AppTheme.spacing.s))
            Banner("В ссылке ключ шифрования ноды: добавляйте ссылки только от тех, кому доверяете.", Tone.Neutral, icon = Icons.Rounded.Lock)
        }
    }
}

/** The profile as a QR code and a link for another device. */
@Composable
fun ShareDialog(model: ProfilesScreenModel, profile: Profile) {
    val toaster = LocalToaster.current
    val link = remember(profile) { model.shareLink(profile) }
    AppDialog(
        title = "QR и ссылка: ${profile.name}",
        onDismiss = { model.shareFor = null },
        primary = "Копировать ссылку",
        primaryEnabled = link.isSuccess,
        onPrimary = {
            link.onSuccess {
                model.copy(it)
                toaster.show("Ссылка скопирована", Tone.Success)
            }
        },
        secondary = "Закрыть",
    ) {
        link.fold(
            onSuccess = { value ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QrCode(remember(value) { model.qr(value) }, 220.dp)
                    Spacer(Modifier.width(AppTheme.spacing.l))
                    Column(Modifier.fillMaxWidth()) {
                        Text("Отсканируйте в OpenFlux на телефоне (Профили → QR) или вставьте ссылку в OpenFlux на компьютере.",
                            style = AppTheme.typography.body, color = AppTheme.colors.textSecondary)
                        Spacer(Modifier.height(AppTheme.spacing.m))
                        Banner("В коде ключ шифрования: передавайте только тому, кто будет пользоваться каналом.", Tone.Warning, icon = Icons.Rounded.Lock)
                    }
                }
                Spacer(Modifier.height(AppTheme.spacing.m))
                AppTextField(value, {}, monospace = true, singleLine = false, minLines = 2, enabled = true, trailing = {
                    io.openflux.desktop.ui.components.AppIconButton("Копировать", {
                        model.copy(value)
                        toaster.show("Ссылка скопирована", Tone.Success)
                    }, icon = Icons.Rounded.ContentCopy)
                })
            },
            onFailure = { Banner(it.message ?: "Профиль нельзя передать ссылкой", Tone.Danger, icon = Icons.Rounded.ErrorOutline) },
        )
    }
}

@Composable
fun DeleteDialog(model: ProfilesScreenModel, profile: Profile) {
    val toaster = LocalToaster.current
    AppDialog(
        title = "Удалить профиль?",
        onDismiss = { model.deleteFor = null },
        primary = "Удалить",
        primaryStyle = ButtonStyle.Danger,
        onPrimary = {
            model.delete(profile)
            model.deleteFor = null
            toaster.show("Профиль «${profile.name}» удалён")
        },
    ) {
        Text(
            "«${profile.name}» пропадёт из списка вместе с ключом. Нода и её документ останутся как есть.",
            style = AppTheme.typography.body,
            color = AppTheme.colors.text,
        )
    }
}
