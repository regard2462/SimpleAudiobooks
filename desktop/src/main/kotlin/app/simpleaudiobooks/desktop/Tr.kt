package app.simpleaudiobooks.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import org.w3c.dom.Element
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Texte der Oberfläche aus denselben strings.xml wie auf Android, ergänzt um desktop.xml. Englisch ist die
 * Grundlage, die gewählte Sprache überschreibt sie.
 */
object Tr {

    /** Ändert sich mit der Sprache, damit Compose neu zeichnet. */
    val revision = mutableIntStateOf(0)

    @Volatile
    var language: String = "en"
        private set

    @Volatile
    private var strings: Map<String, String> = emptyMap()

    @Volatile
    private var plurals: Map<String, Map<String, String>> = emptyMap()

    val locale: Locale get() = Locale.forLanguageTag(language)

    /** Sprache des Systems beim Start, bevor die App die Vorgabe-Locale umstellt. */
    private val systemLanguage: String = Locale.getDefault().language

    /** Sprache der Oberfläche; leer heißt wie das System (Deutsch oder sonst Englisch). */
    fun resolve(choice: String): String = choice.ifEmpty { if (systemLanguage == "de") "de" else "en" }

    fun load(choice: String) {
        val lang = resolve(choice)
        val s = HashMap<String, String>()
        val p = HashMap<String, Map<String, String>>()
        for (l in listOf("en", lang).distinct()) {
            for (file in listOf("strings.xml", "desktop.xml")) read("/strings/$l/$file", s, p)
        }
        strings = s
        plurals = p
        language = lang
        Locale.setDefault(Locale.forLanguageTag(lang))
        revision.intValue++
    }

    private fun read(resource: String, s: MutableMap<String, String>, p: MutableMap<String, Map<String, String>>) {
        val input = Tr::class.java.getResourceAsStream(resource) ?: return
        val doc = input.use { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it) }
        val root = doc.documentElement
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i) as? Element ?: continue
            val name = n.getAttribute("name")
            when (n.tagName) {
                "string" -> s[name] = unescape(n.textContent)
                "plurals" -> {
                    val items = HashMap<String, String>()
                    val children = n.getElementsByTagName("item")
                    for (j in 0 until children.length) {
                        val item = children.item(j) as Element
                        items[item.getAttribute("quantity")] = unescape(item.textContent)
                    }
                    p[name] = items
                }
            }
        }
    }

    /** Android-Schreibweise: \' \" \n und umschließende Anführungszeichen. */
    private fun unescape(raw: String): String {
        var t = raw.trim()
        if (t.length >= 2 && t.startsWith('"') && t.endsWith('"')) t = t.substring(1, t.length - 1)
        val out = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '\\' && i + 1 < t.length) {
                when (val n = t[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    else -> out.append(n)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    fun get(key: String, vararg args: Any): String {
        val text = strings[key] ?: return key
        return if (args.isEmpty()) text else String.format(locale, text, *args)
    }

    fun plural(key: String, count: Int, vararg args: Any): String {
        val items = plurals[key] ?: return key
        val text = (if (count == 1) items["one"] else null) ?: items["other"] ?: items.values.firstOrNull() ?: return key
        val a = if (args.isEmpty()) arrayOf<Any>(count) else args
        return String.format(locale, text, *a)
    }
}

/** Text in der aktuellen Sprache; zeichnet bei einem Sprachwechsel neu. */
@Composable
fun tr(key: String, vararg args: Any): String {
    Tr.revision.intValue
    return Tr.get(key, *args)
}

@Composable
fun trPlural(key: String, count: Int, vararg args: Any): String {
    Tr.revision.intValue
    return Tr.plural(key, count, *args)
}
