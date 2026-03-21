package pl.misek.toafmobile.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

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
            scope.launch(Dispatchers.IO) {
                val success = try {
                    // Otwieramy okno dialogowe wyboru pliku (AWT)
                    val dialog = FileDialog(null as Frame?, "Zapisz plik", FileDialog.SAVE)
                    dialog.file = fileName
                    dialog.isVisible = true

                    val selectedFile = dialog.file
                    val selectedDirectory = dialog.directory

                    if (selectedFile != null && selectedDirectory != null) {
                        val file = File(selectedDirectory, selectedFile)
                        file.writeBytes(currentData)
                        true
                    } else {
                        false // Użytkownik anulował
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }

                withContext(Dispatchers.Main) {
                    onResult(success)
                }
            }
        }
    }
    @Composable
    override fun readDataLauncher(
        onResult: (ByteArray) -> Unit
    ): () -> Unit {
        val scope = rememberCoroutineScope()

        return {
            scope.launch(Dispatchers.IO) {
                val bytes = try {
                    // null jako Frame jest akceptowalny w Compose Desktop
                    val dialog = FileDialog(null as Frame?, "Wybierz plik do odczytu", FileDialog.LOAD)
                    dialog.isVisible = true

                    val file = dialog.file
                    val directory = dialog.directory

                    if (file != null && directory != null) {
                        File(directory, file).readBytes()
                    } else {
                        ByteArray(0) // Użytkownik anulował
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    ByteArray(0)
                }

                withContext(Dispatchers.Main) {
                    onResult(bytes)
                }
            }
        }
    }

    @Composable
    override fun SaveFileHandler(file: File?, onSaveComplete: () -> Unit) {
        val scope = rememberCoroutineScope()

        LaunchedEffect(file) {
            if (file != null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        // Tworzymy okno zapisu (AWT)
                        val dialog = FileDialog(null as Frame?, "Zapisz plik", FileDialog.SAVE)
                        dialog.file = file.name
                        dialog.isVisible = true

                        val selectedFile = dialog.file
                        val selectedDir = dialog.directory

                        if (selectedFile != null && selectedDir != null) {
                            val targetPath = java.io.File(selectedDir, selectedFile).absolutePath
                            // Wywołujemy Twoją funkcję zapisu
                            saveToFile(file.absolutePath, targetPath)
                        }
                    } finally {
                        // Wracamy do wątku głównego, aby zakończyć proces
                        withContext(Dispatchers.Main) {
                            onSaveComplete()
                        }
                    }
                }
            }
        }
    }
}