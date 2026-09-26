package io.openflux.desktop.platform

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.openflux.desktop.service.PlatformServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.SecureRandom
import java.time.Duration
import javax.imageio.ImageIO

class JvmPlatformServices(
    override val appVersion: String,
    private val coreVersionProvider: () -> String,
) : PlatformServices {
    private val os = System.getProperty("os.name").lowercase()
    private val random = SecureRandom()

    override val coreVersion: String get() = coreVersionProvider()
    override val systemProxySupported: Boolean = os.contains("win")
    override val fullTunnelSupported: Boolean = os.contains("win")
    override val elevated: Boolean get() = WindowsElevation.elevated

    override fun restartElevated(): Boolean {
        if (!WindowsElevation.restartElevated(RELAUNCHED_ARG)) return false
        // The shutdown hook stops the core and puts the system proxy back.
        kotlin.system.exitProcess(0)
    }

    private val clipboard get() = Toolkit.getDefaultToolkit().systemClipboard

    override fun clipboardText(): String? = runCatching {
        if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) clipboard.getData(DataFlavor.stringFlavor) as String else null
    }.getOrNull()

    override fun setClipboardText(text: String) {
        clipboard.setContents(StringSelection(text), null)
    }

    override fun qrFromClipboardImage(): String? = runCatching {
        if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) return null
        decodeQr(toBuffered(clipboard.getData(DataFlavor.imageFlavor) as Image))
    }.getOrNull()

    override fun qrFromFile(path: String): String? = runCatching {
        ImageIO.read(File(path))?.let(::decodeQr)
    }.getOrNull()

    override fun pickFile(title: String, extensions: List<String>): String? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        if (extensions.isNotEmpty()) {
            dialog.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
            if (os.contains("win")) dialog.file = extensions.joinToString(";") { "*.$it" }
        }
        dialog.isVisible = true
        val file = dialog.file ?: return null
        return File(dialog.directory, file).absolutePath
    }

    override fun readTextFile(path: String, maxBytes: Int): String? = runCatching {
        val file = File(path)
        if (!file.isFile || file.length() > maxBytes) return null
        file.readText()
    }.getOrNull()

    override fun qrMatrix(text: String): List<BooleanArray> {
        val matrix = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
        )
        return List(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix[x, y] } }
    }

    override fun openUrl(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }
    }

    override fun newSecret(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun now(): Long = System.currentTimeMillis()

    override suspend fun latestRelease(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
            val request = HttpRequest.newBuilder(URI("https://api.github.com/repos/$RELEASE_REPO/releases?per_page=20"))
                .header("User-Agent", "OpenFlux-Desktop").timeout(Duration.ofSeconds(10)).build()
            val body = http.send(request, HttpResponse.BodyHandlers.ofString()).body()
            Json.parseToJsonElement(body).jsonArray
                .map { it.jsonObject["tag_name"]?.jsonPrimitive?.content.orEmpty() }
                .firstOrNull { it.startsWith(DESKTOP_TAG_PREFIX) }
                ?.removePrefix(DESKTOP_TAG_PREFIX)
        }.getOrNull()
    }

    private fun decodeQr(image: BufferedImage): String? {
        val pixels = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, pixels)))
        return try {
            MultiFormatReader().decode(
                bitmap,
                mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true),
            ).text
        } catch (_: NotFoundException) {
            null
        }
    }

    private fun toBuffered(image: Image): BufferedImage {
        if (image is BufferedImage) return image
        val buffered = BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_ARGB)
        buffered.createGraphics().apply { drawImage(image, 0, 0, null); dispose() }
        return buffered
    }

    companion object {
        const val RELEASE_REPO = "meepo161/openfluxandroidfork"
        const val DESKTOP_TAG_PREFIX = "desktop-v"
    }
}

/** Passed to a copy started by restartElevated: it waits for this one to exit. */
const val RELAUNCHED_ARG = "--relaunched"
