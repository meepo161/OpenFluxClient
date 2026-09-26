package io.openflux.desktop.platform

import io.openflux.desktop.model.SavedSystemProxy
import java.util.concurrent.TimeUnit

/**
 * The per-user WinINet proxy (Settings → Network → Proxy), which browsers
 * and most Windows programs follow. OpenFlux points it at the core's HTTP
 * proxy while connected and puts the previous values back afterwards.
 */
object WindowsSystemProxy {
    private const val KEY = """HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings"""
    private const val BYPASS = "<local>;localhost;127.*;10.*;192.168.*"

    fun read(): SavedSystemProxy = SavedSystemProxy(
        enabled = query("ProxyEnable")?.let { it.removePrefix("0x").toIntOrNull(16) == 1 } ?: false,
        server = query("ProxyServer").orEmpty(),
        override = query("ProxyOverride").orEmpty(),
    )

    fun enable(address: String) {
        set("ProxyServer", "REG_SZ", address)
        set("ProxyOverride", "REG_SZ", BYPASS)
        set("ProxyEnable", "REG_DWORD", "1")
        notifyChanged()
    }

    fun restore(saved: SavedSystemProxy) {
        if (saved.server.isEmpty()) delete("ProxyServer") else set("ProxyServer", "REG_SZ", saved.server)
        if (saved.override.isEmpty()) delete("ProxyOverride") else set("ProxyOverride", "REG_SZ", saved.override)
        set("ProxyEnable", "REG_DWORD", if (saved.enabled) "1" else "0")
        notifyChanged()
    }

    /** Whether the system proxy currently points at [address]. */
    fun pointsAt(address: String): Boolean {
        val current = read()
        return current.enabled && current.server == address
    }

    private fun query(name: String): String? {
        val out = run("reg", "query", KEY, "/v", name) ?: return null
        val line = out.lines().firstOrNull { it.trim().startsWith(name) } ?: return null
        val parts = line.trim().split(Regex("\\s{2,}|\\t"), limit = 3)
        return parts.getOrNull(2)?.trim()
    }

    private fun set(name: String, type: String, value: String) {
        run("reg", "add", KEY, "/v", name, "/t", type, "/d", value, "/f")
            ?: throw IllegalStateException("Не удалось изменить системный прокси ($name)")
    }

    private fun delete(name: String) {
        run("reg", "delete", KEY, "/v", name, "/f")
    }

    /** Tells running programs to reread the proxy settings (WinINet refresh). */
    private fun notifyChanged() {
        val script = "\$s='[DllImport(\"wininet.dll\")] public static extern bool InternetSetOption(IntPtr h,int o,IntPtr b,int l);';" +
            "\$t=Add-Type -MemberDefinition \$s -Name WinInet -Namespace OpenFlux -PassThru;" +
            "[void]\$t::InternetSetOption([IntPtr]::Zero,39,[IntPtr]::Zero,0);" +
            "[void]\$t::InternetSetOption([IntPtr]::Zero,37,[IntPtr]::Zero,0)"
        run("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script)
    }

    private fun run(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        if (process.exitValue() == 0) out else null
    }.getOrNull()
}
