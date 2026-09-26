package io.openflux.desktop

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import io.openflux.desktop.model.WindowBounds
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit

/** The smallest window every screen of the app is laid out for. */
val MIN_WINDOW = DpSize(720.dp, 520.dp)

/** How the main window opens: its size, place and whether it is maximized. */
data class InitialWindow(val size: DpSize, val position: WindowPosition, val placement: WindowPlacement)

/**
 * The window as it was last time when that still fits one of the screens,
 * else a size that suits the main screen: roomy on a big monitor, within
 * the edges of a small laptop's (a fixed 1200×780 did not fit 1366×768 at 125%).
 * AWT measures screens in the same units as Compose's dp.
 */
fun initialWindow(saved: WindowBounds?): InitialWindow = initialWindow(saved, usableScreens(), mainScreen())

/** [initialWindow] for given screens' usable areas; [main] is where a first window opens. */
fun initialWindow(saved: WindowBounds?, screens: List<Rectangle>, main: Rectangle?): InitialWindow {
    val placement = if (saved?.maximized == true) WindowPlacement.Maximized else WindowPlacement.Floating
    if (saved != null) {
        val rect = Rectangle(saved.x.toInt(), saved.y.toInt(), saved.width.toInt(), saved.height.toInt())
        // Enough of the title bar on a screen to grab it; a monitor may be gone since.
        val home = screens.firstOrNull { screen -> screen.intersection(rect).let { !it.isEmpty && it.width >= 120 && it.height >= 40 } }
        if (home != null) {
            val width = rect.width.coerceIn(MIN_WINDOW.width.value.toInt(), maxOf(home.width, MIN_WINDOW.width.value.toInt()))
            val height = rect.height.coerceIn(MIN_WINDOW.height.value.toInt(), maxOf(home.height, MIN_WINDOW.height.value.toInt()))
            val x = rect.x.coerceIn(home.x, maxOf(home.x, home.x + home.width - width))
            val y = rect.y.coerceIn(home.y, maxOf(home.y, home.y + home.height - height))
            return InitialWindow(DpSize(width.dp, height.dp), WindowPosition(x.dp, y.dp), placement)
        }
    }
    if (main == null) return InitialWindow(DpSize(1200.dp, 780.dp), WindowPosition.PlatformDefault, placement)
    val width = (main.width * 0.72f).coerceIn(minOf(960f, main.width.toFloat()), 1480f).coerceAtMost(main.width.toFloat())
    val height = (main.height * 0.82f).coerceIn(minOf(640f, main.height.toFloat()), 1000f).coerceAtMost(main.height.toFloat())
    return InitialWindow(DpSize(width.dp, height.dp), WindowPosition.Aligned(Alignment.Center), placement)
}

/** What to save of [state]; null while it has no normal bounds worth keeping. */
fun WindowState.boundsToSave(previous: WindowBounds?): WindowBounds? = when (placement) {
    // Maximized: keep the normal bounds from before, only note the flag.
    WindowPlacement.Maximized -> previous?.copy(maximized = true) ?: currentBounds()?.copy(maximized = true)
    WindowPlacement.Fullscreen -> previous
    WindowPlacement.Floating -> if (isMinimized) previous else currentBounds() ?: previous
}

private fun WindowState.currentBounds(): WindowBounds? {
    val at = position as? WindowPosition.Absolute ?: return null
    return WindowBounds(at.x.value, at.y.value, size.width.value, size.height.value)
}

/** Each screen's area without the taskbar or dock. */
private fun usableScreens(): List<Rectangle> = runCatching {
    val toolkit = Toolkit.getDefaultToolkit()
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { device ->
        val config = device.defaultConfiguration
        val bounds = config.bounds
        val insets = toolkit.getScreenInsets(config)
        Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom,
        )
    }
}.getOrDefault(emptyList())

private fun mainScreen(): Rectangle? = runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().maximumWindowBounds }.getOrNull()
