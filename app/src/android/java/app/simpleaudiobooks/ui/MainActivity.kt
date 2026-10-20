package app.simpleaudiobooks.ui

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.simpleaudiobooks.SimpleAudiobooksApp
import app.simpleaudiobooks.ui.components.LocalApp
import app.simpleaudiobooks.ui.theme.SimpleAudiobooksTheme
import app.simpleaudiobooks.ui.theme.isDark
import app.simpleaudiobooks.util.AppLanguage

class MainActivity : ComponentActivity() {

    private val openPlayer = mutableIntStateOf(0)
    private val app get() = application as SimpleAudiobooksApp

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            val settings by app.settings.flow.collectAsStateWithLifecycle()
            val dark = isDark(settings.theme)
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            SimpleAudiobooksTheme(settings.theme, settings.palette, settings.pureBlack) {
                CompositionLocalProvider(LocalApp provides app) {
                    AppRoot(openPlayer.intValue)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayer.intValue++
        if (intent.action == MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
            lifecycleScope.launch {
                app.library.ready.first { it }
                val entry = app.library.search(query) ?: app.library.lastPlayed() ?: return@launch
                app.playback.play(entry.id)
                openPlayer.intValue++
            }
        }
    }

    override fun onStart() {
        super.onStart()
        app.playback.connect()
    }

    override fun onStop() {
        super.onStop()
        app.library.flush()
        app.playback.release()
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "app.simpleaudiobooks.OPEN_PLAYER"
    }
}
