package io.openflux.desktop.ui.shell

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.openflux.desktop.ui.LocalBrowserViews
import io.openflux.desktop.service.LocalAppContainer
import io.openflux.desktop.ui.components.AppDialog
import io.openflux.desktop.ui.components.Banner
import io.openflux.desktop.ui.components.TextAction
import io.openflux.desktop.ui.components.Tone
import io.openflux.desktop.ui.theme.AppTheme

/**
 * A Yandex check the core cannot pass by itself. The page opens in the
 * built-in browser (through the node's address when the node asked);
 * "Готово" hands the resulting cookies to the core.
 */
@Composable
fun CaptchaDialog() {
    val connection = LocalAppContainer.current.connection
    val prompt by connection.captcha.collectAsState()
    val page by connection.captchaPage.collectAsState()
    val browsers = LocalBrowserViews.current
    val current = prompt ?: return
    AppDialog(
        modifier = Modifier.width(760.dp),
        title = if (current.remote) "Нода просит пройти проверку Яндекса" else "Яндекс просит пройти проверку",
        onDismiss = connection::dismissCaptcha,
        primary = if (current.busy) "Передаю…" else "Готово, проверка пройдена",
        onPrimary = connection::submitCaptcha,
        primaryEnabled = !current.busy && page != null,
        secondary = "Позже",
    ) {
        Text(
            if (current.remote) {
                "Страница открыта с адреса ноды. Пройдите проверку, затем нажмите «Готово»: " +
                    "cookies уйдут ноде, и канал через Яндекс поднимется."
            } else {
                "Пройдите проверку, затем нажмите «Готово». Если страница откроется без проверки, OpenFlux передаст cookies сам."
            },
            style = AppTheme.typography.body,
            color = AppTheme.colors.text,
        )
        Spacer(Modifier.height(AppTheme.spacing.m))
        Box(
            Modifier.fillMaxWidth().height(460.dp).clip(AppTheme.shapes.card)
                .border(1.dp, AppTheme.colors.border, AppTheme.shapes.card),
            contentAlignment = Alignment.Center,
        ) {
            val shown = page
            if (shown != null) browsers.Page(shown, Modifier.fillMaxSize())
            else Text(current.progress.ifEmpty { "Открываю страницу проверки…" }, style = AppTheme.typography.body, color = AppTheme.colors.textSecondary)
        }
        Spacer(Modifier.height(AppTheme.spacing.s))
        TextAction("Открыть страницу проверки заново", connection::openCaptcha, enabled = !current.busy)
        if (current.error.isNotBlank()) {
            Spacer(Modifier.height(AppTheme.spacing.s))
            Banner(current.error, Tone.Danger, icon = Icons.Rounded.ErrorOutline)
        }
    }
}
