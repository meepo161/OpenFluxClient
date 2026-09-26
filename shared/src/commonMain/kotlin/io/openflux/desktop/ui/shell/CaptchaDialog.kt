package io.openflux.desktop.ui.shell

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.openflux.desktop.service.LocalAppContainer
import io.openflux.desktop.ui.components.AppDialog
import io.openflux.desktop.ui.components.Banner
import io.openflux.desktop.ui.components.TextAction
import io.openflux.desktop.ui.components.Tone
import io.openflux.desktop.ui.theme.AppTheme

/**
 * A Yandex check the core cannot pass by itself. The page opens in an
 * isolated Edge window (through the node's address when the node asked);
 * "Готово" hands the resulting cookies to the core.
 */
@Composable
fun CaptchaDialog() {
    val connection = LocalAppContainer.current.connection
    val prompt by connection.captcha.collectAsState()
    val current = prompt ?: return
    AppDialog(
        title = if (current.remote) "Нода просит пройти проверку Яндекса" else "Яндекс просит пройти проверку",
        onDismiss = connection::dismissCaptcha,
        primary = if (current.busy) "Передаю…" else "Готово, проверка пройдена",
        onPrimary = connection::submitCaptcha,
        primaryEnabled = !current.busy,
        secondary = "Позже",
    ) {
        Text(
            if (current.remote) {
                "Страница открыта в отдельном окне Edge с адреса ноды. Пройдите проверку, затем нажмите «Готово»: " +
                    "cookies уйдут ноде, и канал через Яндекс поднимется."
            } else {
                "Страница открыта в отдельном окне Edge. Пройдите проверку, затем нажмите «Готово»."
            },
            style = AppTheme.typography.body,
            color = AppTheme.colors.text,
        )
        Spacer(Modifier.height(AppTheme.spacing.s))
        TextAction("Открыть окно проверки ещё раз", connection::openCaptcha)
        if (current.error.isNotBlank()) {
            Spacer(Modifier.height(AppTheme.spacing.s))
            Banner(current.error, Tone.Danger, icon = Icons.Rounded.ErrorOutline)
        }
    }
}
