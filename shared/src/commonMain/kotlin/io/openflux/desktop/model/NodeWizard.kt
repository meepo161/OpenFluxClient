package io.openflux.desktop.model

import kotlinx.serialization.Serializable

/** How to reach the VDS. Passwords and the key live only in memory. */
data class SshTarget(
    val host: String,
    val port: Int,
    val user: String,
    val password: String = "",
    val privateKey: String = "",
    val passphrase: String = "",
    /** The trusted SHA256 fingerprint, "" for a server seen the first time. */
    val hostKey: String = "",
) {
    override fun toString() = "SshTarget($user@$host:$port)"
}

/** What node-install.sh found on the server. */
@Serializable
data class ServerProbe(
    val arch: String = "",
    val os: String = "",
    val systemd: Boolean = false,
    /** root, nopasswd, password or none. */
    val sudo: String = "",
    val core: String = "",
    val channels: List<String> = emptyList(),
)

/** What installing a channel will change, for the confirmation step. */
@Serializable
data class NodePlan(
    val channel: String = "",
    val port: Int = 0,
    val arch: String = "",
    val core: String = "",
    val actions: List<String> = emptyList(),
    val untouched: List<String> = emptyList(),
)

/** A new channel's name on the server and its encryption key. */
data class NewChannel(val id: String, val key: String) {
    override fun toString() = "NewChannel($id)"
}

/** The channel's Yandex document and the sign-in the node may open it with. */
data class YandexDocument(val url: String, val cookieHeader: String) {
    override fun toString() = "YandexDocument($url)"
}

/** A server the wizard has installed on before, to fill the form again. */
@Serializable
data class KnownServer(val host: String, val port: Int, val user: String)

/**
 * A failed wizard call. [hostKey] with [trust] asks to trust a new server,
 * with [mismatch] reports a changed key; [sudo] means the sudo password was
 * wrong; [captcha] that Yandex wanted a person to pass a check.
 */
class NodeWizardException(
    message: String,
    val hostKey: String? = null,
    val trust: Boolean = false,
    val mismatch: Boolean = false,
    val sudo: Boolean = false,
    val captcha: Boolean = false,
) : Exception(message)

object NodeDocuments {
    private val DOC_URL = Regex("""^https://(docs|disk)\.yandex\.[a-z]{2,3}/edit/d/[A-Za-z0-9_-]{16,200}$""")

    /** The document link without query or fragment, null if it is not a Yandex document. */
    fun clean(url: String): String? = url.trim().replace(Regex("[?#].*$"), "").takeIf(DOC_URL::matches)

    /** Whether a Cookie header holds a Yandex login (the core's provision.CookieStore check). */
    fun signedIn(cookieHeader: String): Boolean =
        cookieHeader.split(';').any { it.trim().substringBefore('=') == "Session_id" && it.contains('=') }
}

object NodeServers {
    const val MAX_KNOWN = 5

    /** The key [AppSettings.knownHostKeys] keeps a server's fingerprint under. */
    fun hostKeyId(host: String, port: Int) = "${host.trim().lowercase()}:$port"

    /** [server] first, then the others without it, at most [MAX_KNOWN]. */
    fun remember(known: List<KnownServer>, server: KnownServer): List<KnownServer> =
        (listOf(server) + known.filterNot { it.host.equals(server.host, ignoreCase = true) && it.port == server.port })
            .take(MAX_KNOWN)

    /** A TCP port, null when [text] is not one. */
    fun port(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..65535 }
}
