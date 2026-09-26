package io.openflux.desktop.web

import io.openflux.desktop.data.AppDirs
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * What the built-in browser does, for the Logs section and a file next to
 * the browser (browser.log; Chromium's own log is browser-cef.log). Only
 * addresses and states: no cookies, page contents or typed text.
 */
object BrowserLog {
    /** The app's log; set by the connection service. */
    @Volatile var listener: ((String, Boolean) -> Unit)? = null

    val dir: File by lazy {
        (System.getenv("LOCALAPPDATA")?.let { File(it, "OpenFlux") } ?: AppDirs.config).apply { mkdirs() }
    }

    val cefLogFile: File get() = File(dir, "browser-cef.log")

    private val file: File by lazy {
        File(dir, "browser.log").apply { runCatching { writeText("") } }
    }
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    fun info(text: String) = write(text, problem = false)

    fun problem(text: String) = write(text, problem = true)

    private fun write(text: String, problem: Boolean) {
        val line = "${LocalDateTime.now().format(time)} ${if (problem) "!" else " "} $text"
        synchronized(this) { runCatching { file.appendText(line + "\n") } }
        runCatching { listener?.invoke("[BROWSER] $text", problem) }
    }

    /** "https://host/path" without query or fragment: those can carry tokens. */
    fun short(url: String?): String = url.orEmpty().substringBefore('?').substringBefore('#').take(120)
}
