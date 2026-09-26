package io.openflux.desktop.model

/** Paths the core gets for one run. */
data class CorePaths(
    val keyFile: String?,
    val confFile: String,
    val cookieStore: String,
    val ipcSocket: String?,
)

/** How to start the core for a profile: the .conf body (Session) and flags. */
data class CoreLaunch(
    val arguments: List<String>,
    val conf: String?,
    val socksAddress: String?,
    val httpProxyAddress: String?,
    val usesIpc: Boolean,
)

/**
 * Turns a profile and the settings into the core's command line. Sessions
 * go through a `--config` file (the only way to name several transports of
 * one type); classic profiles use the single-transport flags.
 */
object CoreConfig {
    const val LOOPBACK = "127.0.0.1"

    fun build(profile: Profile, settings: AppSettings, paths: CorePaths): CoreLaunch {
        val problems = profile.problems()
        require(problems.isEmpty()) { problems.first() }
        val exit = settings.mode == ConnectionMode.Exit
        require(!exit || profile.session) { "Выходной ноде нужен профиль в режиме Session" }
        val socks = "$LOOPBACK:${settings.socksPort}"
        val http = "$LOOPBACK:${settings.socksPort + 1}"
        return if (profile.session) session(profile, settings, paths, exit, socks, http) else classic(profile, settings, paths, socks, http)
    }

    private fun session(profile: Profile, settings: AppSettings, paths: CorePaths, exit: Boolean, socks: String, http: String): CoreLaunch {
        val conf = buildString {
            appendLine("# OpenFlux Desktop: ${profile.name}")
            appendLine("[Interface]")
            if (exit) {
                appendLine("Role = exit")
                appendLine("Mode = l4")
            } else if (settings.fullTunnel) {
                appendLine("Role = client")
                appendLine("Inbound = tun")
            } else {
                appendLine("Role = client")
                appendLine("Inbound = socks5")
                appendLine("Socks5 = $socks")
            }
            appendLine("EncryptionKeyFile = ${confValue(paths.keyFile ?: error("Session requires a key"))}")
            appendLine("CookieStore = ${confValue(paths.cookieStore)}")
            val specs = profile.sessionSpecs().filterNot { exit && it.type == TransportType.DIRECT }
            for (spec in specs) {
                appendLine()
                appendLine("[Transport ${spec.name}]")
                appendLine("Type = ${spec.type.cliName}")
                appendLine("Priority = ${spec.priority}")
                when (spec.type.kind) {
                    ValueKind.DocumentUrl -> appendLine("URL = ${confValue(spec.value)}")
                    ValueKind.Address -> appendLine("Dial = ${confValue(spec.value)}")
                    ValueKind.Token -> {
                        appendLine("Token = ${confValue(spec.value)}")
                        appendLine("UID = ${confValue(spec.uid)}")
                    }
                }
            }
            if (exit) {
                val directPriority = profile.carriers.firstOrNull { it.type == TransportType.DIRECT }?.priority ?: 50
                appendLine()
                appendLine("[Transport direct]")
                appendLine("Type = direct")
                appendLine("Priority = $directPriority")
                appendLine("Listen = 0.0.0.0:${settings.exitDirectPort}")
            }
        }
        val args = buildList {
            add("--config"); add(paths.confFile)
            // The context goes on the command line: .conf values end at '#'
            // and the default context is "http://#".
            add("--url=${profile.effectiveContext()}")
            if (paths.ipcSocket != null) add("--ipc-socket=${paths.ipcSocket}")
            if (exit) {
                add("--share")
                if (settings.exitShareHost.isNotBlank()) add("--share-host=${settings.exitShareHost.trim()}")
            } else if (!settings.fullTunnel) {
                add("--http-proxy=$http")
            }
            if (settings.verboseCoreLog) add("--debug")
        }
        val proxies = !exit && !settings.fullTunnel
        return CoreLaunch(args, conf, if (proxies) socks else null, if (proxies) http else null, usesIpc = paths.ipcSocket != null)
    }

    private fun classic(profile: Profile, settings: AppSettings, paths: CorePaths, socks: String, http: String): CoreLaunch {
        val args = buildList {
            add("--role=client")
            if (settings.fullTunnel) {
                add("--inbound=tun")
            } else {
                add("--inbound=socks5")
                add("--socks5=$socks")
                add("--http-proxy=$http")
            }
            add("--transport=${profile.transport.cliName}")
            add("--codec=${profile.codec.cliName}")
            if (profile.transport == TransportType.ONEME) {
                add("--maxToken=${profile.value.trim()}")
                add("--maxUid=${profile.uid.trim()}")
            } else {
                add("--url=${profile.value.trim()}")
            }
            if (paths.keyFile != null) add("--encryption-key-file=${paths.keyFile}")
            add("--cookie-store=${paths.cookieStore}")
            if (settings.verboseCoreLog) add("--debug")
        }
        return if (settings.fullTunnel) CoreLaunch(args, null, null, null, usesIpc = false)
        else CoreLaunch(args, null, socks, http, usesIpc = false)
    }

    /** .conf values end at '#' or ';' (comments); refuse ones that would be cut. */
    private fun confValue(value: String): String {
        require(value.none { it == '#' || it == ';' || it == '\n' || it == '\r' }) {
            "Значение «${value.take(40)}» нельзя передать ядру: в нём есть # или ;"
        }
        return value
    }
}
