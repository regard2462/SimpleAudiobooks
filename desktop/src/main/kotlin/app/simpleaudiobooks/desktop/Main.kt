package app.simpleaudiobooks.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.simpleaudiobooks.desktop.ui.AppRoot
import app.simpleaudiobooks.desktop.ui.DesktopTheme
import app.simpleaudiobooks.desktop.ui.Page
import app.simpleaudiobooks.desktop.ui.TextFocus
import app.simpleaudiobooks.desktop.ui.UiState
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JOptionPane
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    DesktopPaths.useOwnNativeCache()
    val i = args.indexOf("--selbsttest")
    if (i >= 0) exitProcess(SelfTest.run(File(args.getOrNull(i + 1) ?: "selbsttest.txt")))

    val dataDir = DesktopPaths.dataDir
    val lock = acquireLock(dataDir)
    if (lock == null) {
        Tr.load("")
        JOptionPane.showMessageDialog(null, Tr.get("d_already_running"), Tr.get("app_name"), JOptionPane.INFORMATION_MESSAGE)
        return
    }

    val app = DesktopApp(dataDir)
    Tr.load(app.settings.prefs.value.language)
    val ui = UiState()
    val stopped = AtomicBoolean(false)
    fun stop() {
        if (stopped.compareAndSet(false, true)) {
            app.shutdown()
            runCatching { lock.release() }
        }
    }
    // Auch beim Beenden über das System (macOS-Menü, Abmelden) den Hörstand sichern
    Runtime.getRuntime().addShutdownHook(Thread { stop() })

    application {
        val prefs = remember { app.settings.prefs.value }
        val state = rememberWindowState(
            placement = if (prefs.maximized) WindowPlacement.Maximized else WindowPlacement.Floating,
            position = savedPosition(prefs) ?: WindowPosition(Alignment.Center),
            size = DpSize(prefs.windowWidth.coerceAtLeast(MIN_W).dp, prefs.windowHeight.coerceAtLeast(MIN_H).dp),
        )
        val icon = remember { BitmapPainter(IconArt.render(256).toComposeImageBitmap()) }
        Window(
            onCloseRequest = {
                saveWindow(app, state)
                stop()
                exitApplication()
            },
            state = state,
            title = Tr.get("app_name"),
            icon = icon,
            onPreviewKeyEvent = { handleKey(it, app, ui) },
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(MIN_W, MIN_H) }
            val settings by app.settings.flow.collectAsState()
            Tr.revision.intValue
            SideEffect { window.title = Tr.get("app_name") }
            DesktopTheme(settings) { AppRoot(app, ui, window) }
        }
    }
}

private const val MIN_W = 960
private const val MIN_H = 640

/** Nur eine laufende Fassung je Datenordner, sonst überschreiben sich die Hörstände. */
private fun acquireLock(dir: File): FileLock? = runCatching {
    val raf = RandomAccessFile(File(dir, "instance.lock"), "rw")
    raf.channel.tryLock().also { if (it == null) raf.close() }
}.getOrNull()

/** Gemerkte Fensterposition, wenn sie noch auf einem der Bildschirme liegt. */
private fun savedPosition(p: DesktopPrefs): WindowPosition? {
    val x = p.windowX ?: return null
    val y = p.windowY ?: return null
    val visible = runCatching {
        GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.any { d ->
            d.defaultConfiguration.bounds.intersects(Rectangle(x, y, 200, 120))
        }
    }.getOrDefault(false)
    return if (visible) WindowPosition(x.dp, y.dp) else null
}

private fun saveWindow(app: DesktopApp, state: WindowState) {
    val maximized = state.placement == WindowPlacement.Maximized
    val pos = state.position as? WindowPosition.Absolute
    app.settings.updatePrefs {
        if (maximized) {
            it.copy(maximized = true)
        } else {
            it.copy(
                maximized = false,
                windowWidth = state.size.width.value.toInt(),
                windowHeight = state.size.height.value.toInt(),
                windowX = pos?.x?.value?.toInt() ?: it.windowX,
                windowY = pos?.y?.value?.toInt() ?: it.windowY,
            )
        }
    }
}

/** Tastenkürzel des Fensters; Leertaste und Pfeile nur, wenn kein Eingabefeld den Fokus hat. */
private fun handleKey(e: KeyEvent, app: DesktopApp, ui: UiState): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val command = if (DesktopPaths.isMac) e.isMetaPressed else e.isCtrlPressed
    if (command) {
        return when (e.key) {
            Key.F -> {
                ui.page = Page.LIBRARY
                ui.focusSearch++
                true
            }
            Key.B -> {
                ui.bookmark()
                true
            }
            else -> false
        }
    }
    if (TextFocus.active || ui.dialog != null) return false
    val s = app.settings.value
    return when (e.key) {
        Key.Spacebar -> {
            app.player.togglePlay()
            true
        }
        Key.DirectionLeft -> {
            if (e.isShiftPressed) app.player.previousChapter() else app.player.skip(-s.skipBackSec * 1000L)
            true
        }
        Key.DirectionRight -> {
            if (e.isShiftPressed) app.player.nextChapter() else app.player.skip(s.skipForwardSec * 1000L)
            true
        }
        Key.Escape -> {
            if (ui.selected != null) {
                ui.selected = null
                true
            } else {
                false
            }
        }
        else -> false
    }
}
