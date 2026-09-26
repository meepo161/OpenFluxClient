package io.openflux.desktop.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import io.openflux.desktop.ui.BrowserPage
import io.openflux.desktop.ui.BrowserViews
import java.awt.BorderLayout
import javax.swing.JPanel

/** Built-in browser pages as a native view in the Compose window. */
object KcefBrowserViews : BrowserViews {
    @Composable
    override fun Page(page: BrowserPage, modifier: Modifier) {
        val kcef = page as? KcefPage ?: return
        key(kcef) {
            SwingPanel(
                factory = { JPanel(BorderLayout()).apply { add(kcef.component, BorderLayout.CENTER) } },
                modifier = modifier,
            )
        }
    }
}
