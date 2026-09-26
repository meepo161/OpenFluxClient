package io.openflux.desktop.service

import androidx.compose.runtime.staticCompositionLocalOf
import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.CaptchaPrompt
import io.openflux.desktop.model.ConnectionState
import io.openflux.desktop.model.ExitAddress
import io.openflux.desktop.model.LogLine
import io.openflux.desktop.model.Profile
import io.openflux.desktop.model.ShareLinkCodec
import io.openflux.desktop.model.TrafficStats
import kotlinx.coroutines.flow.StateFlow

/** Saved profiles. Writes are persisted before the flow updates. */
interface ProfileRepository {
    val profiles: StateFlow<List<Profile>>
    fun upsert(profile: Profile)
    fun delete(id: String)
    fun newId(): String
}

interface SettingsRepository {
    val settings: StateFlow<AppSettings>
    fun update(transform: (AppSettings) -> AppSettings)
}

/** Runs the OpenFlux core for one profile at a time. */
interface ConnectionService {
    val state: StateFlow<ConnectionState>
    val traffic: StateFlow<TrafficStats>
    val exitAddress: StateFlow<ExitAddress>
    val logs: StateFlow<List<LogLine>>
    val captcha: StateFlow<CaptchaPrompt?>
    /** Exit mode: the `openflux://` link the core printed for clients. */
    val exitShareLink: StateFlow<String?>
    /** The local SOCKS5 address while connected as a client. */
    val socksAddress: StateFlow<String?>

    fun connect(profile: Profile)
    fun disconnect()
    fun refreshExitAddress()
    fun clearLogs()

    fun openCaptcha()
    fun submitCaptcha()
    fun dismissCaptcha()

    /** Stops the core and undoes system changes; called once on app exit. */
    fun shutdown()
}

/** Desktop facilities the UI needs without touching the platform itself. */
interface PlatformServices {
    val appVersion: String
    val coreVersion: String
    /** Whether this OS can point its system proxy at OpenFlux. */
    val systemProxySupported: Boolean

    fun clipboardText(): String?
    fun setClipboardText(text: String)
    /** Text of a QR code in the image on the clipboard, null when none. */
    fun qrFromClipboardImage(): String?
    /** Text of a QR code in an image file. */
    fun qrFromFile(path: String): String?
    /** A file the user picks; null if they cancel. */
    fun pickFile(title: String, extensions: List<String>): String?
    /** The QR modules of [text], rows of dark (true) cells. */
    fun qrMatrix(text: String): List<BooleanArray>
    fun openUrl(url: String)
    /** A new random channel key (64 hex characters). */
    fun newSecret(): String
    fun now(): Long
    /** Newest app release tag on GitHub, null when unknown. */
    suspend fun latestRelease(): String?
}

/** Everything the UI depends on, built once in main. */
class AppContainer(
    val profiles: ProfileRepository,
    val settings: SettingsRepository,
    val connection: ConnectionService,
    val platform: PlatformServices,
    val shareCodec: ShareLinkCodec,
)

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer is not provided") }
