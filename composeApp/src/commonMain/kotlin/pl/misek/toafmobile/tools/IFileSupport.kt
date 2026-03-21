package pl.misek.toafmobile.tools

import androidx.compose.runtime.Composable
import java.io.File

interface IFileSupport {
    suspend fun saveToFile(sourcePath: String, targetIdentifier: String): Boolean
    @Composable
    fun saveDataLauncher(data: ByteArray,onResult : (Boolean) -> Unit): (String) -> Unit
    @Composable
    fun readDataLauncher(onResult: (ByteArray) -> Unit): () -> Unit
    @Composable
    fun SaveFileHandler(file : File?, onSaveComplete: () -> Unit)
}