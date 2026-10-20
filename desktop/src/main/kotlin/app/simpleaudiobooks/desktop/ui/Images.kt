package app.simpleaudiobooks.desktop.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

/** Lädt Titelbilder verkleinert und hält die zuletzt gebrauchten im Speicher. */
object Images {

    private val cache = object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 160
    }

    fun cached(key: String): ImageBitmap? = synchronized(cache) { cache[key] }

    private fun put(key: String, img: ImageBitmap) = synchronized(cache) { cache[key] = img }

    /** Bild verkleinert auf höchstens [maxPx] an der längeren Seite. */
    fun decode(bytes: ByteArray, maxPx: Int): ImageBitmap? {
        val src = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        val longest = max(src.width, src.height)
        if (longest <= maxPx) return src.toComposeImageBitmap()
        val f = maxPx.toFloat() / longest
        val w = (src.width * f).roundToInt().coerceAtLeast(1)
        val h = (src.height * f).roundToInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(src, Rect.makeWH(src.width.toFloat(), src.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()), SamplingMode.MITCHELL, null, true)
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }

    suspend fun file(path: String, maxPx: Int): ImageBitmap? {
        val key = "$path#$maxPx#${File(path).lastModified()}"
        cached(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bytes = runCatching { File(path).readBytes() }.getOrNull() ?: return@withContext null
            decode(bytes, maxPx)?.also { put(key, it) }
        }
    }

    suspend fun url(url: String, maxPx: Int): ImageBitmap? {
        val key = "$url#$maxPx"
        cached(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bytes = runCatching {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000
                c.readTimeout = 15_000
                c.setRequestProperty("User-Agent", "SimpleAudiobooks (desktop audiobook player)")
                try {
                    if (c.responseCode in 200..299) c.inputStream.use { it.readBytes() } else null
                } finally {
                    c.disconnect()
                }
            }.getOrNull() ?: return@withContext null
            decode(bytes, maxPx)?.also { put(key, it) }
        }
    }
}

@Composable
fun rememberFileImage(path: String?, maxPx: Int): ImageBitmap? {
    if (path == null) return null
    var img by remember(path, maxPx) { mutableStateOf(Images.cached("$path#$maxPx#${File(path).lastModified()}")) }
    LaunchedEffect(path, maxPx) { if (img == null) img = Images.file(path, maxPx) }
    return img
}

@Composable
fun rememberUrlImage(url: String, maxPx: Int): ImageBitmap? {
    var img by remember(url, maxPx) { mutableStateOf(Images.cached("$url#$maxPx")) }
    LaunchedEffect(url, maxPx) { if (img == null) img = Images.url(url, maxPx) }
    return img
}
