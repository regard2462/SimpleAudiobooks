package app.simpleaudiobooks

import android.app.Application
import android.os.SystemClock
import app.simpleaudiobooks.data.AndroidLibraryPlatform
import app.simpleaudiobooks.data.Library
import app.simpleaudiobooks.data.Settings
import app.simpleaudiobooks.data.Stats
import app.simpleaudiobooks.online.OnlineCovers
import app.simpleaudiobooks.playback.PlaybackConnection
import app.simpleaudiobooks.playback.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class SimpleAudiobooksApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var settings: Settings
        private set
    lateinit var library: Library
        private set
    lateinit var stats: Stats
        private set
    lateinit var playback: PlaybackConnection
        private set
    lateinit var onlineCovers: OnlineCovers
        private set

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        SleepTimer.clock = { SystemClock.elapsedRealtime() }
        stats = Stats(filesDir, scope)
        library = Library(filesDir, settings, scope, AndroidLibraryPlatform(this))
        playback = PlaybackConnection(this)
        onlineCovers = OnlineCovers(library, settings, scope, "SimpleAudiobooks/${BuildConfig.VERSION_NAME} (Android audiobook player)").also { it.start() }
    }
}
