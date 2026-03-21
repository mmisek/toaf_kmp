package pl.misek.toafmobile.tools

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.snackbar.ToafSnackBar
import toafmobile.composeapp.generated.resources.AD_save_to_file_failed
import toafmobile.composeapp.generated.resources.AD_save_to_file_success
import toafmobile.composeapp.generated.resources.Res
import java.io.File
import java.io.IOException

class AndroidFileSupport(private val context : Context) : IFileSupport {

    override suspend fun saveToFile(
        sourcePath: String,
        targetIdentifier: String
    ): Boolean =
        withContext(Dispatchers.IO)
        {
            return@withContext runCatching {
                val sourceFile = File(sourcePath)
                val targetUri = targetIdentifier.toUri()

                val contentResolver = context.contentResolver
                contentResolver.openOutputStream(targetUri)?.use { output ->
                    sourceFile.inputStream().use {input ->
                        input.copyTo(output)
                    }
                } ?: throw IOException("saveToFile Error")
            }
        }.isSuccess
    @Composable
    override fun saveDataLauncher(
        data: ByteArray,
        onResult : (Boolean) -> Unit
    ): (String) -> Unit {

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val currentData by rememberUpdatedState(data)
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/octet-stream")
        ) { uri: Uri? ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    val success = try {
                        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(currentData)
                        }
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                    withContext(Dispatchers.Main)
                    {
                        onResult(success)
                    }
                }

            }
        }

        return { fileName ->
            launcher.launch(fileName)
        }
    }

    @Composable
    override fun readDataLauncher(
        onResult: (ByteArray) -> Unit
    ): () -> Unit {

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                scope.launch(Dispatchers.IO) {
                    val bytes = try {
                        context.contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.readBytes()
                        }
                    }
                    catch (e: Exception) {
                        e.printStackTrace()
                        onResult(ByteArray(0))
                    }
                    withContext(Dispatchers.IO)
                    {
                        onResult(bytes as ByteArray)
                    }
                }
            }
        }

        return {
            launcher.launch(arrayOf("*/*"))
        }
    }

    @Composable
    override fun SaveFileHandler(file: File?, onSaveComplete: () -> Unit)
    {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val saveToFileSuccessText = stringResource(Res.string.AD_save_to_file_success)
        val saveToFileFailText = stringResource(Res.string.AD_save_to_file_failed)
        val launcher = rememberLauncherForActivityResult(
            contract  = ActivityResultContracts.CreateDocument("text/cvs")
        ) { targetUri ->
            targetUri?.let {
                scope.launch {
                    val result = saveToFile(file?.absolutePath ?: "", it.toString())
                    ToafSnackBar().showResult(
                        scope = scope,
                        snackBarHostState = snackbarHostState,
                        result = result,
                        messageSuccess = String.format(saveToFileSuccessText, it),
                        messageError = saveToFileFailText
                    )
                }
            }
            onSaveComplete()
        }
        LaunchedEffect(file) {
            file?.let {
                launcher.launch(it.name)
            }
        }
    }
}