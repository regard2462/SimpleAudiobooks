package app.simpleaudiobooks.desktop.ui

import app.simpleaudiobooks.desktop.DesktopPaths
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/** Dialoge des Systems zum Wählen von Ordnern und Dateien. */
object FileDialogs {

    init {
        // Ordnerwahl in der Optik des Systems statt im Swing-Standard
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    }

    fun chooseFolder(parent: Frame?, title: String): File? {
        if (DesktopPaths.isMac) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val d = FileDialog(parent, title, FileDialog.LOAD)
                d.isVisible = true
                val dir = d.directory ?: return null
                val name = d.file ?: return null
                return File(dir, name)
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        }
        val chooser = JFileChooser().apply {
            dialogTitle = title
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            isAcceptAllFileFilterUsed = false
        }
        return if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun openFile(parent: Frame?, title: String, extensions: List<String>): File? {
        val d = FileDialog(parent, title, FileDialog.LOAD)
        if (extensions.isNotEmpty()) d.setFilenameFilter { _, name -> extensions.any { name.lowercase().endsWith(".$it") } }
        d.isVisible = true
        val dir = d.directory ?: return null
        val name = d.file ?: return null
        return File(dir, name)
    }

    fun saveFile(parent: Frame?, title: String, suggested: String): File? {
        val d = FileDialog(parent, title, FileDialog.SAVE)
        d.file = suggested
        d.isVisible = true
        val dir = d.directory ?: return null
        val name = d.file ?: return null
        return File(dir, name)
    }
}
