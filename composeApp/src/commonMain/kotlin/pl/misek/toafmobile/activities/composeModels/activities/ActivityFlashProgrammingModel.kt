package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.modules.interfaces.IModule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.snackbar.ToafSnackBarHost
import pl.misek.toafmobile.interfaces.ISerialDevice
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.enums.FlashProgrammingState
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleFlashProgramming
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.protocols.ProtocolFactory
import pl.misek.toafmobile.tools.IFileSupport
import pl.misek.toafmobile.tools.isDebug
import pl.misek.toafmobile.viewModels.UserInfoViewModel
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private const val tag = "ActivityFlashProgrammingModel"
@Composable
fun ActivityFlashProgrammingModel(moduleKoinName: KoinNames, onBackClick : () -> Unit) {
    val snackBarHostState = remember { SnackbarHostState() }
    val toafApi : ToafApi = koinInject()
    val protocolFactory : ProtocolFactory = koinInject()
    val modulesManage : ModulesManager = koinInject()
    val fileSupport : IFileSupport = koinInject()
    val currentModule = modulesManage.getCurrentModules().firstOrNull {it.getKoinName() == moduleKoinName} as IModule
    val flashModule = currentModule as IModuleFlashProgramming
    val userInfoViewModel : UserInfoViewModel = koinViewModel()
    val userInfo by userInfoViewModel.userInfo.collectAsState()
    var showFullVersionDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var lastProgress by remember { mutableIntStateOf(0) }
    var flashReadData  by remember {mutableStateOf(ByteArray(0))}
    var statusText by remember { mutableStateOf("") }
    var fileName by remember {mutableStateOf("")}
    val flashDataSaveToFileSuccess = stringResource(Res.string.activity_flash_programming_written_to)
    val flashDataSaveToFileFail = Res.string.activity_flash_programming_write_data_to_file_error
    val backupFlashText = stringResource(Res.string.activity_flash_programming_backup_flash)
    val successText = stringResource(Res.string.activity_flash_programming_ok)
    val failText = stringResource(Res.string.activity_flash_programming_failed)
    val notSupportedText = stringResource(Res.string.activity_main_function_not_supported_yet)
    val badInterfaceText = stringResource(Res.string.activity_main_function_need_usb_device)
    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)

    val programmingProcess = { fileData: ByteArray ->
        scope.launch {
            flashModule.writeFlash(fileData)
        }
    }

    val saveFileLauncher : (String) -> Unit? = fileSupport.saveDataLauncher(
        data = flashReadData,
        onResult = {result ->
            statusText += "\n" + if (result)
                String.format(flashDataSaveToFileSuccess,fileName)
            else
                flashDataSaveToFileFail
            fileName = ""
        })
    val readFileAndProgram = fileSupport.readDataLauncher(
        onResult = { result ->
            programmingProcess(result)
        }
    )
    LaunchedEffect(flashModule) {
        flashModule.programmingState.collect { state ->
        when(state)
        {
            is FlashProgrammingState.Message -> {
                if (state.message is String)
                    statusText += state.message + "\n"
            }
            is FlashProgrammingState.MessageWithParameter -> {
                val param = when (val v = state.parameter)
                {
                    is String -> v
                    is Int -> v.toString()
                    is Float -> "%.2f".format(v)
                    is StringResource -> getString(state.parameter)
                    else -> {
                        Logger.e {"$tag : no format for data type ${v::class.simpleName}"}
                        ""
                    }
                }
                statusText += if (param.isEmpty())
                    "${getString(state.message)}\n"
                else
                    "${getString(state.message)}: ${param}\n"
            }
            is FlashProgrammingState.Progress -> {
                if (lastProgress != state.progress) {
                    lastProgress = state.progress
                    statusText += if (state.progress < 100)
                        String.format(Locale.getDefault(), "%d%%..", state.progress)
                    else
                        String.format(Locale.getDefault(), "%d%%\n", state.progress)
                }
            }
            is FlashProgrammingState.FlashData ->{
                val jsonObject = buildJsonObject{
                    put("vin", state.vin)
                    put("module", "DDE")
                    put("id1", state.moduleInfo?.id1 ?: "")
                    put("id2", state.moduleInfo?.id2 ?: "")
                }
                withContext(Dispatchers.IO) {
                    val backupSuccess =
                        toafApi.uploadFlash(jsonObject, state.flashData.toByteArray())
                    statusText += String.format(backupFlashText, if (backupSuccess) successText else failText)
                }
                flashReadData = state.flashData.toByteArray()
                fileName = String.format(
                    "%s_%s_%s.bin",
                    currentModule.getModuleName(),
                    state.vin ?: state.moduleInfo?.id1 ?: "",
                    simpleDateFormat.format(Calendar.getInstance().time))

                saveFileLauncher(fileName)
            }
            else -> {}
        }
            }
    }

    Scaffold(
        modifier = Modifier
            .padding(all = 0.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
        containerColor = ToafColors.toafBackground,
        snackbarHost = { ToafSnackBarHost(hostState = snackBarHostState) },
        topBar = {
            TopBarModel(
                titleResource = Res.string.activity_flash_programming_tittle,
                onBackClicked = { onBackClick() })
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .background(ToafColors.toafBackground)
            )
            {
                if (showFullVersionDialog)
                {
                    DialogNotFullVersion(
                        onWatchAdResult = { result ->
                            if (result)
                                readFileAndProgram()
                        },
                        onCloseClick = {
                            showFullVersionDialog = false
                        }
                    )
                }
                ToafButtons.GreenButton(
                    modifier = Modifier.padding(5.dp).fillMaxWidth(),
                    buttonText = Res.string.activity_flash_programming_flash_reading,
                    onClick = {
                        if (protocolFactory.currentDevice !is ISerialDevice)
                        {
                            showResult(scope,snackBarHostState,false,null,badInterfaceText)
                        }
                        else {
                            statusText = ""
                            scope.launch {
                                withContext(Dispatchers.IO) { flashModule.readFlash() }
                            }
                        }
                    }
                )
                ToafButtons.GreenButton(
                    modifier = Modifier.padding(5.dp).fillMaxWidth(),
                    buttonText = Res.string.activity_flash_programming_write_flash,
                    onClick = {
                        if (isDebug()) {
                            if (protocolFactory.currentDevice !is ISerialDevice) {
                                showResult(scope, snackBarHostState, false, null, badInterfaceText)
                            } else {
                                statusText = ""
                                if (userInfo.isFullVersion())
                                    readFileAndProgram()
                                else
                                    showFullVersionDialog = true
                                }
                            }
                        else {
                            showResult(scope, snackBarHostState, false, null, notSupportedText)
                        }
                    })

                StatusLogWindow(
                    statusText = statusText.ifEmpty { stringResource(Res.string.activity_flash_programing_warning)},
                    modifier = Modifier.fillMaxWidth().padding(start = 5.dp, top = 20.dp, end = 5.dp, bottom = 20.dp)
                )
            }
        }
    )
}


@Preview(showBackground = true, backgroundColor = 0x0FF00000)
@Composable
fun ActivityFlashProgrammingPreview()
{
    ActivityEepromProgrammingModel(
        moduleKoinName = KoinNames.ModuleR40Dde,
        onBackClick = {})
}