package io.openflux.desktop.web

import org.cef.browser.CefBrowser
import org.cef.callback.CefDragData
import org.cef.handler.CefRenderHandler
import org.cef.handler.CefScreenInfo
import java.awt.Color
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.event.MouseMotionListener
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

/**
 * A lightweight Swing view of an off-screen browser: CEF paints frames into
 * memory, this draws them and sends input back. No native child window, so
 * nothing depends on how Windows embeds one in the Compose window (a
 * windowed browser there stayed white), and the page shows the same on
 * every platform. IntelliJ renders its JCEF views the same way.
 */
internal class OsrView : JComponent(), MouseListener, MouseMotionListener, KeyListener, FocusListener {
    @Volatile var browser: CefBrowser? = null

    private val lock = Any()
    private var frame: BufferedImage? = null
    private var popup: BufferedImage? = null
    private var popupRect: Rectangle? = null

    init {
        isFocusable = true
        // Tab and Shift+Tab belong to the page's own fields.
        focusTraversalKeysEnabled = false
        isOpaque = true
        background = Color.WHITE
        addMouseListener(this)
        addMouseMotionListener(this)
        addMouseWheelListener(::wheel)
        addKeyListener(this)
        addFocusListener(this)
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                browser?.wasResized(width.coerceAtLeast(1), height.coerceAtLeast(1))
            }
        })
    }

    /** Pixels per logical pixel (Windows display scaling). */
    val scale: Double
        get() = graphicsConfiguration?.defaultTransform?.scaleX?.takeIf { it > 0 } ?: 1.0

    val renderHandler: CefRenderHandler = object : CefRenderHandler {
        override fun getViewRect(browser: CefBrowser?) = Rectangle(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))

        override fun getScreenInfo(browser: CefBrowser?, info: CefScreenInfo): Boolean {
            val bounds = graphicsConfiguration?.bounds ?: Rectangle(0, 0, width, height)
            info.Set(scale, 32, 8, false, bounds, bounds)
            return true
        }

        override fun getScreenPoint(browser: CefBrowser?, viewPoint: Point): Point {
            val origin = runCatching { locationOnScreen }.getOrDefault(Point(0, 0))
            return Point(origin.x + viewPoint.x, origin.y + viewPoint.y)
        }

        override fun getDeviceScaleFactor(browser: CefBrowser?) = scale

        override fun onPopupShow(browser: CefBrowser?, show: Boolean) {
            if (!show) synchronized(lock) { popup = null; popupRect = null }
            repaint()
        }

        override fun onPopupSize(browser: CefBrowser?, size: Rectangle) {
            synchronized(lock) { popupRect = Rectangle(size) }
        }

        override fun onPaint(browser: CefBrowser?, popup: Boolean, dirtyRects: Array<out Rectangle>?, buffer: ByteBuffer, width: Int, height: Int) {
            if (width <= 0 || height <= 0) return
            synchronized(lock) {
                val current = if (popup) this@OsrView.popup else frame
                val image = current?.takeIf { it.width == width && it.height == height }
                    ?: BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE)
                // CEF hands premultiplied BGRA bytes: little-endian ints are ARGB.
                val pixels = (image.raster.dataBuffer as DataBufferInt).data
                buffer.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(pixels, 0, minOf(pixels.size, width * height))
                if (popup) this@OsrView.popup = image else frame = image
            }
            SwingUtilities.invokeLater { repaint() }
        }

        override fun onCursorChange(browser: CefBrowser?, cursorType: Int): Boolean {
            SwingUtilities.invokeLater { cursor = runCatching { Cursor.getPredefinedCursor(cursorType) }.getOrDefault(Cursor.getDefaultCursor()) }
            return true
        }

        override fun startDragging(browser: CefBrowser?, dragData: CefDragData?, mask: Int, x: Int, y: Int) = false

        override fun updateDragCursor(browser: CefBrowser?, operation: Int) = Unit
    }

    override fun paintComponent(g: Graphics) {
        g.color = background
        g.fillRect(0, 0, width, height)
        synchronized(lock) {
            val s = scale
            frame?.let { g.drawImage(it, 0, 0, (it.width / s).toInt(), (it.height / s).toInt(), null) }
            val p = popup
            val r = popupRect
            if (p != null && r != null) g.drawImage(p, r.x, r.y, (p.width / s).toInt(), (p.height / s).toInt(), null)
        }
    }

    // ---- input ----

    override fun mousePressed(e: MouseEvent) {
        if (!hasFocus()) requestFocusInWindow()
        browser?.sendMouseEvent(e)
    }

    override fun mouseReleased(e: MouseEvent) { browser?.sendMouseEvent(e) }
    override fun mouseClicked(e: MouseEvent) = Unit
    override fun mouseEntered(e: MouseEvent) { browser?.sendMouseEvent(e) }
    override fun mouseExited(e: MouseEvent) { browser?.sendMouseEvent(e) }
    override fun mouseMoved(e: MouseEvent) { browser?.sendMouseEvent(e) }
    override fun mouseDragged(e: MouseEvent) { browser?.sendMouseEvent(e) }

    /**
     * JCEF's native side scrolls by -rotation * amount pixels, so AWT's "one
     * notch down" would move the page 3 px up. Send what Chrome does: 100 px
     * per notch in the wheel's direction (fractions from touchpads too).
     */
    private fun wheel(e: MouseWheelEvent) {
        val pixels = (e.preciseWheelRotation * WHEEL_PIXELS).roundToInt()
        if (pixels == 0) return
        browser?.sendMouseWheelEvent(
            MouseWheelEvent(
                this, e.id, e.`when`, e.modifiersEx, e.x, e.y, e.xOnScreen, e.yOnScreen, 0, false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -pixels, -pixels.toDouble(),
            ),
        )
        e.consume()
    }

    override fun keyPressed(e: KeyEvent) { browser?.sendKeyEvent(e); e.consume() }
    override fun keyReleased(e: KeyEvent) { browser?.sendKeyEvent(e); e.consume() }
    override fun keyTyped(e: KeyEvent) { browser?.sendKeyEvent(e); e.consume() }

    override fun focusGained(e: FocusEvent) { browser?.setFocus(true) }
    override fun focusLost(e: FocusEvent) { browser?.setFocus(false) }

    private companion object {
        const val WHEEL_PIXELS = 100
    }
}
