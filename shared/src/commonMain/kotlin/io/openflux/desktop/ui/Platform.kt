package io.openflux.desktop.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent

/**
 * Desktop scrollbars live in the desktop part of Compose; the platform
 * supplies them so screens can stay in common code.
 */
interface Scrollbars {
    @Composable
    fun Vertical(state: ScrollState, modifier: Modifier)

    @Composable
    fun Vertical(state: LazyListState, modifier: Modifier)
}

object NoScrollbars : Scrollbars {
    @Composable
    override fun Vertical(state: ScrollState, modifier: Modifier) = Unit

    @Composable
    override fun Vertical(state: LazyListState, modifier: Modifier) = Unit
}

val LocalScrollbars = staticCompositionLocalOf<Scrollbars> { NoScrollbars }

/**
 * The system back gesture or button (Android): the innermost enabled
 * handler gets it. Does nothing on the desktop.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

/**
 * A touch screen (Android): no pointer hover, no keyboard shortcuts, bigger
 * controls and text.
 */
val LocalTouchUi = staticCompositionLocalOf { false }

/**
 * Window-wide keyboard shortcuts. The window forwards its key events here
 * and the shell registers what the keys do.
 */
class Shortcuts {
    private val handlers = mutableListOf<(KeyEvent) -> Boolean>()

    fun register(handler: (KeyEvent) -> Boolean): () -> Unit {
        handlers += handler
        return { handlers -= handler }
    }

    fun handle(event: KeyEvent): Boolean = handlers.asReversed().any { it(event) }
}

val LocalShortcuts = staticCompositionLocalOf { Shortcuts() }

/** A page of the built-in browser (Yandex sign-in, checks); [BrowserViews] shows it. */
interface BrowserPage

/** Shows a [BrowserPage] inside the window; the platform supplies it like [Scrollbars]. */
interface BrowserViews {
    @Composable
    fun Page(page: BrowserPage, modifier: Modifier)
}

object NoBrowserViews : BrowserViews {
    @Composable
    override fun Page(page: BrowserPage, modifier: Modifier) = Unit
}

val LocalBrowserViews = staticCompositionLocalOf<BrowserViews> { NoBrowserViews }
