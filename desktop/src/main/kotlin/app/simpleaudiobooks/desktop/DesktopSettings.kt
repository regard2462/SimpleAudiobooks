package app.simpleaudiobooks.desktop

import app.simpleaudiobooks.data.AppSettings
import app.simpleaudiobooks.data.JsonFile
import app.simpleaudiobooks.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

/** Einstellungen, die es nur am Desktop gibt. */
@Serializable
data class DesktopPrefs(
    /** Sprache der Oberfläche, leer heißt wie das System. */
    val language: String = "",
    /** Lautstärke 0 bis 1. */
    val volume: Float = 1f,
    val windowWidth: Int = 1280,
    val windowHeight: Int = 820,
    val windowX: Int? = null,
    val windowY: Int? = null,
    val maximized: Boolean = false,
)

/** Einstellungen der App als JSON im Datenordner, Schreiben leicht verzögert. */
class DesktopSettings(dir: File, private val scope: CoroutineScope) : SettingsStore {

    private val file = JsonFile(File(dir, "settings.json"), AppSettings.serializer())
    private val prefsFile = JsonFile(File(dir, "desktop.json"), DesktopPrefs.serializer())
    private val _flow = MutableStateFlow(file.read() ?: AppSettings())
    private val _prefs = MutableStateFlow(prefsFile.read() ?: DesktopPrefs())
    private var saveJob: Job? = null
    private var prefsJob: Job? = null

    override val flow: StateFlow<AppSettings> = _flow.asStateFlow()
    val prefs: StateFlow<DesktopPrefs> = _prefs.asStateFlow()

    override fun update(change: (AppSettings) -> AppSettings) {
        _flow.update(change)
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(300)
            file.write(_flow.value)
        }
    }

    fun updatePrefs(change: (DesktopPrefs) -> DesktopPrefs) {
        _prefs.update(change)
        prefsJob?.cancel()
        prefsJob = scope.launch(Dispatchers.IO) {
            delay(500)
            prefsFile.write(_prefs.value)
        }
    }

    fun flush() {
        saveJob?.cancel()
        prefsJob?.cancel()
        runCatching { file.write(_flow.value) }
        runCatching { prefsFile.write(_prefs.value) }
    }
}
