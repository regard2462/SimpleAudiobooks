package app.simpleaudiobooks.desktop

import app.simpleaudiobooks.data.CoverStore
import app.simpleaudiobooks.data.SavedCover
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.awt.Color
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Speichert verkleinerte Titelbilder als JPEG und bestimmt ihre Leitfarbe, mit Skia statt Android-Bitmaps. */
class SkiaCovers(override val dir: File) : CoverStore {

    init {
        dir.mkdirs()
    }

    override fun saveBytes(name: String, bytes: ByteArray): SavedCover? {
        val source = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        if (source.width <= 0 || source.height <= 0) return null
        val f = min(1f, MAX_SIDE.toFloat() / max(source.width, source.height))
        val w = (source.width * f).roundToInt().coerceAtLeast(1)
        val h = (source.height * f).roundToInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(
            source,
            Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
            Rect.makeWH(w.toFloat(), h.toFloat()),
            SamplingMode.MITCHELL,
            null,
            true,
        )
        val scaled = surface.makeImageSnapshot()
        val data = scaled.encodeToData(EncodedImageFormat.JPEG, 90)?.bytes ?: return null
        val file = File(dir, "$name.jpg")
        val tmp = File(dir, "$name.tmp")
        tmp.writeBytes(data)
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        return SavedCover(file.absolutePath, dominantColor(scaled))
    }

    companion object {
        private const val MAX_SIDE = 1024

        /** Leuchtende, häufige Farbe eines Bildes; bei Graustufen der Durchschnitt (wie auf Android). */
        fun dominantColor(image: Image): Int? {
            val surface = Surface.makeRasterN32Premul(32, 32)
            surface.canvas.drawImageRect(image, Rect.makeWH(image.width.toFloat(), image.height.toFloat()), Rect.makeWH(32f, 32f), SamplingMode.LINEAR, null, true)
            val bmp = Bitmap()
            bmp.allocN32Pixels(32, 32)
            if (!surface.readPixels(bmp, 0, 0)) return null
            val bins = Array(12) { DoubleArray(4) }
            var ar = 0.0
            var ag = 0.0
            var ab = 0.0
            var n = 0
            val hsb = FloatArray(3)
            for (y in 0 until 32) for (x in 0 until 32) {
                val c = bmp.getColor(x, y)
                if ((c ushr 24) and 0xFF < 128) continue
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                ar += r; ag += g; ab += b; n++
                Color.RGBtoHSB(r, g, b, hsb)
                val s = hsb[1]
                val v = hsb[2]
                if (s < 0.22f || v < 0.18f) continue
                val w = s * (1f - abs(v - 0.65f))
                val bin = bins[((hsb[0] * 360f / 30f).toInt()).coerceIn(0, 11)]
                bin[0] += w.toDouble(); bin[1] += r * w.toDouble(); bin[2] += g * w.toDouble(); bin[3] += b * w.toDouble()
            }
            if (n == 0) return null
            val best = bins.maxByOrNull { it[0] }!!
            return if (best[0] > 0.6) {
                rgb((best[1] / best[0]).toInt(), (best[2] / best[0]).toInt(), (best[3] / best[0]).toInt())
            } else {
                rgb((ar / n).toInt(), (ag / n).toInt(), (ab / n).toInt())
            }
        }

        private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }
}
