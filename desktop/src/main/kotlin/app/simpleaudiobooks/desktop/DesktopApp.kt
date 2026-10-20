package app.simpleaudiobooks.desktop

import app.simpleaudiobooks.data.Library
import app.simpleaudiobooks.data.Stats
import app.simpleaudiobooks.desktop.player.AudioSink
import app.simpleaudiobooks.desktop.player.DesktopPlayer
import app.simpleaudiobooks.online.OnlineCovers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/** Alle Bauteile der Desktop-Fassung, einmal je Programmstart. */
class DesktopApp(
    val dataDir: File,
    main: CoroutineDispatcher = Dispatchers.Main,
    sinkFactory: () -> AudioSink = AudioSink::create,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = DesktopSettings(dataDir, scope)
    val stats = Stats(dataDir, scope)
    val platform = DesktopPlatform(dataDir)
    val library = Library(dataDir, settings, scope, platform)
    val player = DesktopPlayer(library, settings, stats, scope, sinkFactory, main)
    val onlineCovers = OnlineCovers(library, settings, scope, "SimpleAudiobooks/$VERSION (desktop audiobook player)").also { it.start() }

    init {
        player.volume = settings.prefs.value.volume
        scope.launch(Dispatchers.IO) { runCatching { Ffmpeg.warmUp() } }
    }

    fun shutdown() {
        player.shutdown()
        library.flush()
        stats.flush()
        settings.flush()
        scope.cancel()
    }

    companion object {
        /** Fassung aus den Programmressourcen (beim Bauen eingetragen). */
        val VERSION: String by lazy {
            DesktopApp::class.java.getResourceAsStream("/app-version.txt")?.use { it.readBytes().decodeToString().trim() } ?: "dev"
        }
    }
}
