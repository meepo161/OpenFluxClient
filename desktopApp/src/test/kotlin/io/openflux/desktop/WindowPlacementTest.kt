package io.openflux.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import io.openflux.desktop.model.WindowBounds
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowPlacementTest {
    private val laptop = Rectangle(0, 0, 1093, 574) // 1366×768 at 125%, taskbar off
    private val monitor = Rectangle(0, 0, 2560, 1400)
    private val second = Rectangle(2560, 0, 1920, 1040)

    @Test
    fun firstStartFitsASmallScreen() {
        val window = initialWindow(null, listOf(laptop), laptop)
        assertEquals(DpSize(960.dp, 574.dp), window.size)
        assertEquals(WindowPlacement.Floating, window.placement)
    }

    @Test
    fun firstStartIsRoomyButCappedOnABigScreen() {
        assertEquals(DpSize(1480.dp, 1000.dp), initialWindow(null, listOf(monitor), monitor).size)
    }

    @Test
    fun savedBoundsComeBack() {
        val window = initialWindow(WindowBounds(2700f, 100f, 1300f, 800f), listOf(monitor, second), monitor)
        assertEquals(DpSize(1300.dp, 800.dp), window.size)
        assertEquals(WindowPosition(2700.dp, 100.dp), window.position)
    }

    @Test
    fun aWindowOnAMissingMonitorOpensOnTheMainOne() {
        val window = initialWindow(WindowBounds(2700f, 100f, 1300f, 800f), listOf(monitor), monitor)
        assertEquals(WindowPosition.Aligned(androidx.compose.ui.Alignment.Center), window.position)
    }

    @Test
    fun aWindowBiggerThanItsScreenIsPulledIn() {
        val window = initialWindow(WindowBounds(900f, 300f, 1600f, 900f), listOf(laptop), laptop)
        assertEquals(DpSize(1093.dp, 574.dp), window.size)
        assertEquals(WindowPosition(0.dp, 0.dp), window.position)
    }

    @Test
    fun maximizedStaysMaximized() {
        val window = initialWindow(WindowBounds(10f, 10f, 1000f, 700f, maximized = true), listOf(monitor), monitor)
        assertEquals(WindowPlacement.Maximized, window.placement)
    }
}
