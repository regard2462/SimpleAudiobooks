package app.simpleaudiobooks.desktop

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.Path
import org.jetbrains.skia.Point
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Shader
import org.jetbrains.skia.Surface

/**
 * Das App-Symbol wie auf Android: Kopfhörer um ein aufgeschlagenes Buch, der Bügel zeigt als goldener Bogen den
 * Hörfortschritt, auf nachtblauem Grund. Die Pfade stammen aus den Android-Vektoren (108er Raster).
 */
object IconArt {

    private const val BAND_OPEN = "M66.3,28.9 A27,27 0 0,1 81,53 L81,63"
    private const val BAND_DONE = "M27,63 L27,53 A27,27 0 0,1 66.3,28.9"
    private const val CUP_LEFT = "M26,56 h6 a3,3 0 0,1 3,3 v14 a3,3 0 0,1 -3,3 h-6 a4,4 0 0,1 -4,-4 v-12 a4,4 0 0,1 4,-4 z"
    private const val CUP_RIGHT = "M82,56 h-6 a3,3 0 0,0 -3,3 v14 a3,3 0 0,0 3,3 h6 a4,4 0 0,0 4,-4 v-12 a4,4 0 0,0 -4,-4 z"
    private const val PAGE_LEFT = "M53,50 C47,46 41,45 38,45.5 L38,74 C42,73.5 47,74.5 53,78 Z"
    private const val PAGE_RIGHT = "M55,50 C61,46 67,45 70,45.5 L70,74 C66,73.5 61,74.5 55,78 Z"
    private const val LINES = "M42,53 C45,52.6 47.5,53 50,54.2 M42,59 C45,58.6 47.5,59 50,60.2 M42,65 C45,64.6 47.5,65 50,66.2 " +
        "M66,53 C63,52.6 60.5,53 58,54.2 M66,59 C63,58.6 60.5,59 58,60.2 M66,65 C63,64.6 60.5,65 58,66.2"

    private fun path(d: String) = Path.makeFromSVGString(d)

    /**
     * Symbol mit [size] Pixeln Kantenlänge. [margin] ist der freie Rand als Anteil (macOS erwartet etwa 10 %),
     * [radius] der Eckenradius der Fläche als Anteil ihrer Breite.
     */
    fun render(size: Int, margin: Float = 0.04f, radius: Float = 0.22f): Image {
        val surface = Surface.makeRasterN32Premul(size, size)
        draw(surface.canvas, size.toFloat(), margin, radius)
        return surface.makeImageSnapshot()
    }

    fun draw(canvas: Canvas, size: Float, margin: Float, radius: Float) {
        val inset = size * margin
        val side = size - 2 * inset
        // Fläche: Nachtblau, oben links heller
        val bg = Paint().apply {
            isAntiAlias = true
            shader = Shader.makeLinearGradient(Point(inset, inset), Point(inset + side, inset + side), intArrayOf(0xFF4567B5.toInt(), 0xFF14254D.toInt()))
        }
        canvas.drawRRect(RRect.makeXYWH(inset, inset, side, side, side * radius), bg)

        // Vordergrund im 108er Raster: sichtbarer Ausschnitt 12..96 auf die Fläche legen
        canvas.save()
        canvas.translate(inset, inset)
        val k = side / 84f
        canvas.scale(k, k)
        canvas.translate(-12f, -12f)
        // Gruppe aus dem Android-Vektor: um (54,58) auf 92 % verkleinert und 3 nach oben
        canvas.translate(0f, -3f)
        canvas.translate(54f, 58f)
        canvas.scale(0.92f, 0.92f)
        canvas.translate(-54f, -58f)

        val gold = Shader.makeLinearGradient(Point(54f, 26f), Point(54f, 78f), intArrayOf(0xFFFFE08A.toInt(), 0xFFD99A22.toInt()))
        val stroke = Paint().apply {
            isAntiAlias = true
            mode = PaintMode.STROKE
            strokeWidth = 5.5f
            strokeCap = PaintStrokeCap.ROUND
        }
        canvas.drawPath(path(BAND_OPEN), stroke.apply { color = 0xFF5D79B5.toInt() })
        canvas.drawPath(path(BAND_DONE), Paint().apply {
            isAntiAlias = true
            mode = PaintMode.STROKE
            strokeWidth = 5.5f
            strokeCap = PaintStrokeCap.ROUND
            shader = gold
        })
        val cups = Paint().apply { isAntiAlias = true; shader = gold }
        canvas.drawPath(path(CUP_LEFT), cups)
        canvas.drawPath(path(CUP_RIGHT), cups)
        val pages = Paint().apply {
            isAntiAlias = true
            shader = Shader.makeLinearGradient(
                Point(38f, 0f), Point(70f, 0f),
                intArrayOf(Color.WHITE, 0xFFD8E2F6.toInt(), Color.WHITE),
                floatArrayOf(0f, 0.5f, 1f),
            )
        }
        canvas.drawPath(path(PAGE_LEFT), pages)
        canvas.drawPath(path(PAGE_RIGHT), pages)
        canvas.drawPath(path(LINES), Paint().apply {
            isAntiAlias = true
            mode = PaintMode.STROKE
            strokeWidth = 1.6f
            strokeCap = PaintStrokeCap.ROUND
            color = 0xFF9FB3DD.toInt()
        })
        canvas.restore()
    }
}
