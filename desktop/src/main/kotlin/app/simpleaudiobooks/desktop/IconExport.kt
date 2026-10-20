package app.simpleaudiobooks.desktop

import org.jetbrains.skia.EncodedImageFormat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Schreibt die Symbol-Dateien der Pakete: app.png (Linux), app.ico (Windows) und app.icns (macOS).
 * Aufruf über die Gradle-Aufgabe renderIcons; die Dateien liegen danach in desktop/icons.
 */
fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "icons").apply { mkdirs() }
    fun png(size: Int, margin: Float = 0.04f): ByteArray =
        IconArt.render(size, margin).encodeToData(EncodedImageFormat.PNG)!!.bytes

    File(dir, "app.png").writeBytes(png(512))

    // ICO mit PNG-Einträgen (ab Windows Vista)
    val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
    val images = sizes.map { png(it, if (it <= 32) 0f else 0.04f) }
    val ico = ByteArrayOutputStream()
    fun le16(v: Int) { ico.write(v and 0xFF); ico.write((v shr 8) and 0xFF) }
    fun le32(v: Int) { le16(v and 0xFFFF); le16((v shr 16) and 0xFFFF) }
    le16(0); le16(1); le16(sizes.size)
    var offset = 6 + 16 * sizes.size
    sizes.forEachIndexed { i, s ->
        ico.write(if (s >= 256) 0 else s)
        ico.write(if (s >= 256) 0 else s)
        ico.write(0); ico.write(0)
        le16(1); le16(32)
        le32(images[i].size)
        le32(offset)
        offset += images[i].size
    }
    images.forEach { ico.write(it) }
    File(dir, "app.ico").writeBytes(ico.toByteArray())

    // ICNS: PNG-Einträge, macOS-Symbole mit etwa 10 % Rand
    val entries = listOf("icp4" to 16, "icp5" to 32, "icp6" to 64, "ic07" to 128, "ic08" to 256, "ic09" to 512, "ic10" to 1024)
    val body = ByteArrayOutputStream()
    val out = DataOutputStream(body)
    for ((type, s) in entries) {
        val data = png(s, 0.1f)
        out.writeBytes(type)
        out.writeInt(data.size + 8)
        out.write(data)
    }
    val icns = ByteArrayOutputStream()
    DataOutputStream(icns).apply {
        writeBytes("icns")
        writeInt(body.size() + 8)
        write(body.toByteArray())
    }
    File(dir, "app.icns").writeBytes(icns.toByteArray())
    println("Symbole in ${dir.absolutePath}")
}
