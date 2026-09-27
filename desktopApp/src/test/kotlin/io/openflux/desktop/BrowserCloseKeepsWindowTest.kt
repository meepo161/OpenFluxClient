package io.openflux.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.openflux.desktop.web.BuiltInBrowser
import io.openflux.desktop.web.KcefBrowserViews
import io.openflux.desktop.web.KcefPage
import java.awt.GraphicsEnvironment
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.delay

/**
 * Closing a built-in browser page must leave the window alone: JCEF sends
 * WINDOW_CLOSING to the window a closed browser was in, and the window went
 * to the tray after the wizard's document step and after a Yandex check.
 *
 * Needs a screen, the downloaded browser and the network, so it runs only
 * with OPENFLUX_BROWSER_TEST=1.
 */
class BrowserCloseKeepsWindowTest {
    @Test
    fun closingPagesDoesNotCloseTheWindow() {
        if (GraphicsEnvironment.isHeadless() || System.getenv("OPENFLUX_BROWSER_TEST") == null) return
        val events = Collections.synchronizedList(mutableListOf<String>())
        application(exitProcessOnExit = false) {
            var inWindow by remember { mutableStateOf<KcefPage?>(null) }
            var inDialog by remember { mutableStateOf<KcefPage?>(null) }
            Window(onCloseRequest = { events += "close request"; exitApplication() }, title = "OpenFlux test") {
                BasicText("OpenFlux")
                inWindow?.let { KcefBrowserViews.Page(it, Modifier.size(500.dp)) }
                inDialog?.let { page ->
                    Dialog(onDismissRequest = {}) {
                        Box(Modifier.size(500.dp)) { KcefBrowserViews.Page(page, Modifier.size(480.dp)) }
                    }
                }
                LaunchedEffect(Unit) {
                    // The document step: the page in the window, closed when done.
                    val document = BuiltInBrowser.open("https://ya.ru/")
                    inWindow = document
                    delay(5000)
                    // Closed while its view is still in the window: the worst case.
                    document.close()
                    delay(3000)
                    inWindow = null
                    events += "document closed"
                    // A Yandex check in the dialog, closed as submitCaptcha does.
                    val check = BuiltInBrowser.open("https://ya.ru/")
                    inDialog = check
                    delay(5000)
                    check.close()
                    BuiltInBrowser.release()
                    BuiltInBrowser.clearCookies()
                    delay(3000)
                    inDialog = null
                    events += "check closed"
                    exitApplication()
                }
            }
        }
        assertEquals(listOf("document closed", "check closed"), events.toList())
    }
}
