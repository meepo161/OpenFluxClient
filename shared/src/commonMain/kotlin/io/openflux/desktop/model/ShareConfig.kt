package io.openflux.desktop.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * The JSON inside an `openflux://v1/` link, field for field as the core's
 * share.Config writes it.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ShareConfig(
    val name: String = "",
    val negotiate: Boolean = false,
    val codec: String = "",
    val secret: String = "",
    val context: String = "",
    @EncodeDefault val transports: List<ShareTransport> = emptyList(),
) {
    /** Mirrors share.Config.Validate in the core. */
    fun validate() {
        require(transports.isNotEmpty()) { "В ссылке нет транспортов" }
        require(transports.size == 1 || negotiate) { "Несколько транспортов требуют режима Session" }
        require(!negotiate || secret.length >= Profile.MIN_SECRET) { "Для Session нужен ключ не короче ${Profile.MIN_SECRET} символов" }
        require(secret.isEmpty() || secret.length >= Profile.MIN_SECRET) { "Ключ короче ${Profile.MIN_SECRET} символов" }
        require(codec.isEmpty() || codec == "batched" || codec == "legacy") { "Неизвестный кодек $codec" }
        for (t in transports) {
            val type = TransportType.fromCli(t.type)
            require(type != null && type.shareable) { "Неизвестный транспорт ${t.type}" }
            if (type == TransportType.DIRECT) {
                require(t.dial.isNotBlank()) { "У direct нет адреса ноды" }
                require(negotiate) { "Direct работает только в режиме Session" }
            }
        }
    }
}

@Serializable
data class ShareTransport(
    val type: String,
    val name: String = "",
    val url: String = "",
    val priority: Int = 0,
    val dial: String = "",
)

/** Encodes and decodes `openflux://v1/` links (deflate + base64url). */
interface ShareLinkCodec {
    fun encode(config: ShareConfig): String
    fun decode(link: String): ShareConfig

    companion object {
        const val PREFIX = "openflux://v1/"
    }
}
