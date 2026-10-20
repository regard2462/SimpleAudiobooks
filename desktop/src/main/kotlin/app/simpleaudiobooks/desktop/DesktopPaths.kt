package app.simpleaudiobooks.desktop

import java.io.File

/** Wo die Desktop-Fassung ihre Daten ablegt; für Tests über -Dsimpleaudiobooks.daten umlenkbar. */
object DesktopPaths {

    val os: String = System.getProperty("os.name").lowercase()
    val isWindows = os.contains("win")
    val isMac = os.contains("mac")

    val dataDir: File by lazy {
        val override = System.getProperty("simpleaudiobooks.daten")?.takeIf { it.isNotBlank() }
        val home = System.getProperty("user.home")
        val dir = when {
            override != null -> File(override)
            isWindows -> File(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: "$home/AppData/Roaming", "SimpleAudiobooks")
            isMac -> File(home, "Library/Application Support/SimpleAudiobooks")
            else -> File(System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "simple-audiobooks")
        }
        dir.apply { mkdirs() }
    }

    /** Zwischenspeicher, etwa für die beim ersten Start ausgepackten FFmpeg-Bibliotheken. */
    val cacheDir: File by lazy {
        val home = System.getProperty("user.home")
        when {
            isWindows -> File(System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() } ?: "$home/AppData/Local", "SimpleAudiobooks/cache")
            isMac -> File(home, "Library/Caches/SimpleAudiobooks")
            else -> File(System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() } ?: "$home/.cache", "simple-audiobooks")
        }
    }

    /** JavaCPP packt die FFmpeg-Bibliotheken sonst nach ~/.javacpp aus, so landen sie im Zwischenspeicher des Programms. */
    fun useOwnNativeCache() {
        if (System.getProperty("org.bytedeco.javacpp.cachedir") == null) {
            System.setProperty("org.bytedeco.javacpp.cachedir", File(cacheDir, "natives").absolutePath)
        }
    }
}
