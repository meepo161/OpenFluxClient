package io.openflux.desktop.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

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

    /**
     * The name of a new channel's document on Disk: the node's name in Latin
     * letters, the time (UTC) and a few random letters and digits, like
     * "moya-noda-20260927-0251-k3f9". Nothing says OpenFlux, and the default
     * name ("Нода <server>") is left out so the server's address does not end
     * up on Disk. Fits the [a-z0-9-]{1,64} the document browsers accept.
     */
    fun fileName(nodeName: String, host: String, nowMillis: Long, random: Random = Random.Default): String {
        val name = nodeName.trim().takeUnless { it.isEmpty() || it == "Нода ${host.trim()}" }.orEmpty()
        val slug = name.lowercase().map { TRANSLIT[it] ?: it.toString() }.joinToString("")
            .replace("openflux", "")
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(24).trim('-')
        val suffix = (1..4).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        return listOf(slug, utcStamp(nowMillis), suffix).filter { it.isNotEmpty() }.joinToString("-")
    }

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

    private val TRANSLIT = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e", 'ж' to "zh",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts",
        'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu",
        'я' to "ya",
    )

    /** yyyyMMdd-HHmm in UTC, without a date library in common code. */
    private fun utcStamp(millis: Long): String {
        val minutes = millis / 60_000
        val days = minutes.floorDiv(1440L)
        val minuteOfDay = minutes.mod(1440L)
        // Civil date from days since 1970-01-01 (Howard Hinnant's algorithm).
        val z = days + 719468
        val era = z.floorDiv(146097L)
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yoe + era * 400 + if (month <= 2) 1 else 0
        fun two(n: Long) = n.toString().padStart(2, '0')
        return "$year${two(month)}${two(day)}-${two(minuteOfDay / 60)}${two(minuteOfDay % 60)}"
    }
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
