package io.openflux.desktop.model

/**
 * The connection as the UI sees it. One value at a time instead of a set of
 * booleans, so "connecting and failed" cannot happen.
 */
sealed interface ConnectionState {
    data object Idle : ConnectionState

    /** The core is starting or has not reached the exit yet. */
    data class Connecting(val profile: Profile, val mode: ConnectionMode, val since: Long) : ConnectionState

    /** A carrier reaches the exit (client) or the exit serves (exit mode). */
    data class Connected(val profile: Profile, val mode: ConnectionMode, val since: Long) : ConnectionState

    /** Was connected; no carrier reaches the peer right now, the core retries. */
    data class Reconnecting(val profile: Profile, val mode: ConnectionMode, val since: Long) : ConnectionState

    data class Disconnecting(val profile: Profile) : ConnectionState

    data class Failed(val profile: Profile?, val message: String) : ConnectionState
}

val ConnectionState.profile: Profile?
    get() = when (this) {
        ConnectionState.Idle -> null
        is ConnectionState.Connecting -> profile
        is ConnectionState.Connected -> profile
        is ConnectionState.Reconnecting -> profile
        is ConnectionState.Disconnecting -> profile
        is ConnectionState.Failed -> profile
    }

/** The core is running (or about to); the connect action becomes "disconnect". */
val ConnectionState.isActive: Boolean
    get() = this is ConnectionState.Connecting || this is ConnectionState.Connected ||
        this is ConnectionState.Reconnecting

/** Live numbers for the home screen; zero while idle. */
data class TrafficStats(
    val upBytesPerSec: Long = 0,
    val downBytesPerSec: Long = 0,
    val totalUp: Long = 0,
    val totalDown: Long = 0,
    /** Carrier data currently goes through ("" when unknown). */
    val activeTransport: String = "",
    /** Whether the core reports its status (sessions over IPC). */
    val live: Boolean = false,
)

/** What the client knows about where its traffic leaves. */
sealed interface ExitAddress {
    data object Unknown : ExitAddress
    data object Checking : ExitAddress
    data class Known(val ip: String) : ExitAddress
    data class Unavailable(val reason: String) : ExitAddress
}

/** A Yandex check the core asks the user to pass in a browser. */
data class CaptchaPrompt(
    val url: String,
    val reason: String,
    /** The check belongs to the exit node: the browser goes through its address. */
    val remote: Boolean,
    val error: String = "",
    val busy: Boolean = false,
    /** The built-in browser getting ready (first-run download). */
    val progress: String = "",
)

enum class LogLevel { Info, Success, Warning, Error, Debug }

data class LogLine(val id: Long, val time: Long, val text: String, val level: LogLevel)
