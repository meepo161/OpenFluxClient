package io.openflux.desktop.service

import androidx.compose.runtime.staticCompositionLocalOf
import io.openflux.desktop.model.AppSettings
import io.openflux.desktop.model.CaptchaPrompt
import io.openflux.desktop.model.ConnectionState
import io.openflux.desktop.model.ExitAddress
import io.openflux.desktop.model.LogLine
import io.openflux.desktop.model.NewChannel
import io.openflux.desktop.model.NodePlan
import io.openflux.desktop.model.ServerProbe
import io.openflux.desktop.model.SshTarget
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
    /** A small text file's contents (an SSH key), null if it cannot be read. */
    fun readTextFile(path: String, maxBytes: Int = 64 * 1024): String?
    /** The QR modules of [text], rows of dark (true) cells. */
    fun qrMatrix(text: String): List<BooleanArray>
    fun openUrl(url: String)
    /** A new random channel key (64 hex characters). */
    fun newSecret(): String
    fun now(): Long
    /** Newest app release tag on GitHub, null when unknown. */
    suspend fun latestRelease(): String?
}

/**
 * The "Своя нода" wizard's server side: installs an independent exit
 * channel on the user's VDS over SSH (the core's --node-wizard) and checks
 * the channel's Yandex document, which the user creates in their own
 * browser. Calls block until done and fail with NodeWizardException.
 */
interface NodeWizardService {
    /** SSH in, download the pinned installer and look at the server. */
    suspend fun connect(target: SshTarget): ServerProbe
    suspend fun newChannel(): NewChannel
    /** What installing [channel] would change; port 0 lets the server pick. */
    suspend fun plan(channel: String, withCookies: Boolean): NodePlan
    /** Install and start the channel. [cookieHeader] "" leaves the node signed out. */
    suspend fun apply(channel: NewChannel, documentUrl: String, port: Int, sudoPassword: String, cookieHeader: String)
    suspend fun remove(channel: String, sudoPassword: String)
    /** Whether the node can use the document (edit by link), as an anonymous visitor. */
    suspend fun checkDocument(documentUrl: String)
    /** The channel's `openflux://` link: the document, direct to host:port as backup. */
    suspend fun shareLink(name: String, documentUrl: String, key: String, host: String, port: Int): String
    /** The addresses [host] resolves to, to compare with the tunnel's exit. */
    suspend fun resolve(host: String): Set<String>

    /** Ends the SSH session and the helper process. */
    fun close()
}

/** Everything the UI depends on, built once in main. */
class AppContainer(
    val profiles: ProfileRepository,
    val settings: SettingsRepository,
    val connection: ConnectionService,
    val platform: PlatformServices,
    val shareCodec: ShareLinkCodec,
    val nodeWizard: NodeWizardService,
)

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer is not provided") }
