package io.openflux.desktop.ui

import io.openflux.desktop.model.ConnectionMode
import io.openflux.desktop.model.ConnectionState
import io.openflux.desktop.ui.components.Tone
import kotlin.math.roundToLong

object Format {
    fun bytes(value: Long): String = when {
        value < 1024 -> "$value Б"
        value < 1024 * 1024 -> "${oneDecimal(value / 1024.0)} КБ"
        value < 1024L * 1024 * 1024 -> "${oneDecimal(value / (1024.0 * 1024))} МБ"
        else -> "${oneDecimal(value / (1024.0 * 1024 * 1024))} ГБ"
    }

    fun speed(bytesPerSecond: Long): String = bytes(bytesPerSecond) + "/с"

    fun duration(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "$h:${two(m)}:${two(s)}" else "${two(m)}:${two(s)}"
    }

    private fun two(n: Long) = n.toString().padStart(2, '0')

    private fun oneDecimal(v: Double): String {
        val tenths = (v * 10).roundToLong()
        return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10},${tenths % 10}"
    }
}

/** How the UI names and colors a connection state. */
data class StateLook(val title: String, val tone: Tone)

fun ConnectionState.look(): StateLook = when (this) {
    ConnectionState.Idle -> StateLook("Отключено", Tone.Neutral)
    is ConnectionState.Connecting -> StateLook(if (mode == ConnectionMode.Exit) "Запуск ноды…" else "Подключение…", Tone.Warning)
    is ConnectionState.Connected -> StateLook(if (mode == ConnectionMode.Exit) "Нода работает" else "Подключено", Tone.Accent)
    is ConnectionState.Reconnecting -> StateLook("Переподключение…", Tone.Warning)
    is ConnectionState.Disconnecting -> StateLook("Отключение…", Tone.Warning)
    is ConnectionState.Failed -> StateLook("Ошибка", Tone.Danger)
}

/** Hides document addresses and keys, as the Android log does. */
object Redact {
    private val url = Regex("""https?://(docs|disk|cloud)\.[^\s"')]+""")
    private val hex = Regex("""\b[0-9a-f]{32,}\b""")
    private val link = Regex("""openflux://v1/[A-Za-z0-9_-]+""")

    fun apply(text: String): String = text
        .replace(link, "openflux://v1/[скрыто]")
        .replace(url, "[адрес скрыт]")
        .replace(hex, "[ключ скрыт]")
}
