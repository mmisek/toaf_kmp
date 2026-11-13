package pl.misek.toafmobile.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private const val tag = "DesktopFileSupport"

/**
 * Natywne okno wyboru pliku musi zostac pokazane na watku zdarzen AWT (EDT).
 *
 * Na Windows wywolanie z dowolnego watku zwykle przechodzi, ale na macOS AWT przekazuje
 * natywny panel na watek AppKit przez LWCToolkit.invokeAndWait - wywolanie spoza EDT
 * potrafi zakleszczyc cala aplikacje. Dispatchers.Swing to dokladnie EDT.
 *
 * Modalne okno blokuje EDT tylko pozornie: FileDialog.show() uruchamia zagniezdzona
 * petle zdarzen, wiec reszta UI pozostaje responsywna.
 */
private suspend fun chooseFile(title: String, mode: Int, fileName: String?): File? =
    withContext(Dispatchers.Swing) {
        val dialog = FileDialog(ownerFrame(), title, mode)
        if (fileName != null) dialog.file = fileName
        dialog.isVisible = true

        val selectedFile = dialog.file
        val selectedDirectory = dialog.directory
        // null oznacza, ze uzytkownik anulowal
        if (selectedFile != null && selectedDirectory != null) {
            File(selectedDirectory, selectedFile)
        } else {
            null
        }
    }

/**
 * Okno bez rodzica na macOS gubi focus i nie przykleja sie do okna aplikacji.
 * ComposeWindow dziedziczy po JFrame, wiec znajdziemy je wsrod ramek AWT.
 */
private fun ownerFrame(): Frame? = Frame.getFrames().firstOrNull { it.isVisible }

class DesktopFileSupport: IFileSupport {
    override suspend fun saveToFile(sourcePath: String, targetIdentifier: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val sourceFile = File(sourcePath)
                val targetFile = File(targetIdentifier)
                sourceFile.copyTo(targetFile, overwrite = true)
            }.isSuccess
        }

    @Composable
    override fun saveDataLauncher(
        data: ByteArray,
        onResult: (Boolean) -> Unit
    ): (String) -> Unit {
        val scope = rememberCoroutineScope()
        val currentData by rememberUpdatedState(data)
        return { fileName ->
            // Scope kompozycji dziala na EDT, wiec onResult trafia na watek UI bez
            // dodatkowego przelaczania; sam zapis schodzi na Dispatchers.IO.
            scope.launch {
                val success = try {
                    val target = chooseFile("Zapisz plik", FileDialog.SAVE, fileName)
                    if (target != null) {
                        withContext(Dispatchers.IO) { target.writeBytes(currentData) }
                        true
                    } else {
                        false // Uzytkownik anulowal
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Logger.e { "$tag [saveDataLauncher], $ex" }
                    false
                }
                onResult(success)
            }
        }
    }

    @Composable
    override fun readDataLauncher(
        onResult: (ByteArray) -> Unit
    ): () -> Unit {
        val scope = rememberCoroutineScope()

        return {
            scope.launch {
                val bytes = try {
                    val source = chooseFile("Wybierz plik do odczytu", FileDialog.LOAD, null)
                    if (source != null) {
                        withContext(Dispatchers.IO) { source.readBytes() }
                    } else {
                        ByteArray(0) // Uzytkownik anulowal
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Logger.e { "$tag [readDataLauncher], $ex" }
                    ByteArray(0)
                }
                onResult(bytes)
            }
        }
    }

    @Composable
    override fun SaveFileHandler(file: File?, onSaveComplete: () -> Unit) {
        LaunchedEffect(file) {
            if (file == null) return@LaunchedEffect
            try {
                val target = chooseFile("Zapisz plik", FileDialog.SAVE, file.name)
                if (target != null) {
                    saveToFile(file.absolutePath, target.absolutePath)
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [SaveFileHandler], $ex" }
            } finally {
                // Wracamy do stanu spoczynku nawet po bledzie - inaczej UI zostaje
                // zablokowane w trybie "zapisywanie".
                onSaveComplete()
            }
        }
    }
}
