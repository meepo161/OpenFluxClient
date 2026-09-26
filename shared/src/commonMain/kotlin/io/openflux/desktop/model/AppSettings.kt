package io.openflux.desktop.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode(val label: String) { System("Как в системе"), Light("Светлая"), Dark("Тёмная") }

/** How this computer takes part: a client of an exit, or an exit itself. */
@Serializable
enum class ConnectionMode(val label: String, val description: String) {
    Client("Клиент (SOCKS5)", "Трафик программ идёт через ноду"),
    Exit("Выходная нода", "Этот компьютер выпускает в интернет других"),
}

/** Which core binary runs the connection. */
@Serializable
enum class CoreSource(val label: String) { Bundled("Встроенное ядро"), Custom("Свой файл") }

/** The Windows proxy settings found before OpenFlux changed them. */
@Serializable
data class SavedSystemProxy(val enabled: Boolean, val server: String, val override: String)

@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.System,
    val mode: ConnectionMode = ConnectionMode.Client,
    val socksPort: Int = 1080,
    /** Point Windows (browsers and most programs) at the core's HTTP proxy while connected. */
    val systemProxy: Boolean = true,
    /**
     * The system proxy used to be off by default, so a connected client did
     * not carry the computer's traffic; set once [systemProxy] was turned on
     * for settings saved before that. See [migrated].
     */
    val systemProxyDefaultOn: Boolean = false,
    /**
     * Client: all of the computer's traffic through a Wintun adapter (the
     * core's --inbound=tun), like the Android VPN, instead of the proxies.
     * Needs administrator rights.
     */
    val fullTunnel: Boolean = false,
    val autoConnect: Boolean = false,
    val selectedProfileId: String? = null,
    val coreSource: CoreSource = CoreSource.Bundled,
    val customCorePath: String = "",
    /** The core's --debug: every packet in the log. */
    val verboseCoreLog: Boolean = false,
    val logAutoScroll: Boolean = true,
    /** Hide document URLs and keys in the log view. */
    val maskSensitive: Boolean = true,
    val closeToTray: Boolean = true,
    /** Exit mode: address clients dial for direct ("" = the core's guess). */
    val exitShareHost: String = "",
    /** Exit mode: TCP port for the direct transport. */
    val exitDirectPort: Int = 8445,
    /** Set while OpenFlux has changed the Windows proxy; restored on exit or next start. */
    val savedSystemProxy: SavedSystemProxy? = null,
    val sidebarCollapsed: Boolean = false,
    /** Node wizard: trusted SSH host keys by "host:port". */
    val knownHostKeys: Map<String, String> = emptyMap(),
    /** Node wizard: servers used before, newest first. No passwords. */
    val knownServers: List<KnownServer> = emptyList(),
)

/** Settings from an older version, brought up to date once on load. */
fun AppSettings.migrated(): AppSettings =
    if (systemProxyDefaultOn) this else copy(systemProxy = true, systemProxyDefaultOn = true)
