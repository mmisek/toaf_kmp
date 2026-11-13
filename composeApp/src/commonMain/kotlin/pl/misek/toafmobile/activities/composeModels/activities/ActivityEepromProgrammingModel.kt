package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.modules.interfaces.IModule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.snackbar.ToafSnackBarHost
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.r40.ModuleIPK
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.tools.BatteryReadResult
import pl.misek.toafmobile.tools.IFileSupport
import pl.misek.toafmobile.tools.readBatteryInfo
import pl.misek.toafmobile.viewModels.UserInfoViewModel
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun ActivityEepromProgrammingModel(moduleKoinName: KoinNames, onBackClick : () -> Unit) {
    val toafApi : ToafApi = koinInject()
    val fileSupport : IFileSupport = koinInject()
    val modulesManage : ModulesManager = koinInject()
    val currentModule = modulesManage.getCurrentModules().firstOrNull {it.getKoinName() == moduleKoinName}
    val moduleIPK = modulesManage.getCurrentModules().firstOrNull {it.getKoinName() == KoinNames.ModuleR40Ipk }
    val userInfoViewModel : UserInfoViewModel = koinViewModel()
    val userInfo by userInfoViewModel.userInfo.collectAsState()
    var showFullVersionDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackBarHostState = remember { SnackbarHostState() }
    val notSupportedText = stringResource(Res.string.activity_main_function_not_supported_yet)
    var eepromReadData  by remember {mutableStateOf(ByteArray(0))}
    var moduleIdentification by remember {mutableStateOf<ModuleInfo?>(null)}
    var statusText by remember { mutableStateOf("") }
    var fileName by remember {mutableStateOf("")}
    val eepromReadStartText = stringResource(Res.string.activity_eeprom_programming_start)
    val eepromReadGetIdentificationText = stringResource(Res.string.activity_eeprom_read_identification)
    val eepromReadProgressText = stringResource(Res.string.activity_eeprom_programming_eeprom_read)
    val eepromReadErrorText = stringResource(Res.string.activity_eeprom_programming_read_error)
    val eepromReadEndText = stringResource(Res.string.activity_eeprom_programming_end)
    val eepromReadEepromSizeText = stringResource(Res.string.activity_eeprom_programming_read_size)
    val eepromWriteToFileText = stringResource(Res.string.activity_eeprom_programming_written_to)
    val eepromWriteToFileFailText = stringResource(Res.string.activity_eeprom_programming_write_data_to_file_error)
    val eepromBackupResultText = stringResource(Res.string.activity_eeprom_programming_backup_eeprom)
    val eepromResultOkText = stringResource(Res.string.activity_eeprom_programming_ok)
    val eepromResultFailedText = stringResource(Res.string.activity_eeprom_programming_failed)
    val eepromReadSizeMismatchErrorText = stringResource(Res.string.activity_eeprom_programming_read_data_size_error)
    val eepromProgrammingEndText = stringResource(Res.string.activity_eeprom_programming_end)
    val eepromProgrammingWrongFileSizeText = stringResource(Res.string.activity_eeprom_programming_wrong_file_size)
    val eepromWriteProgressText = stringResource(Res.string.activity_eeprom_programming_eeprom_write)
    val eepromWriteErrorText = stringResource(Res.string.activity_eeprom_programming_write_error)
    val eepromWriteSkipText = stringResource(Res.string.activity_eeprom_programming_write_skip)
    val moduleResetText = stringResource(Res.string.activity_eeprom_programming_ecu_reset)
    val voltageCheckText = stringResource(Res.string.activity_eeprom_programming_voltage_check)
    val voltageCheckFailedText = stringResource(Res.string.activity_eeprom_programming_voltage_check_failed)
    val voltageText = stringResource(Res.string.activity_eeprom_programming_voltage)
    val voltageTooLowText = stringResource(Res.string.activity_eeprom_programming_voltage_too_low)

    val programmingProcess = { fileData : ByteArray ->
        scope.launch {
            val eepromModule = currentModule as IModuleEepromProgramming
            var liveDataModule = currentModule as? IModuleLiveData
            var liveDataModuleAddress = currentModule.getModuleAddress()
            val eepromSize = eepromModule.getEepromSize()
            val bytesToWrite = eepromModule.getEepromWriteSize()
            if (fileData.size == eepromSize) {
                statusText = eepromReadStartText
                statusText += "\n" + eepromReadGetIdentificationText
                moduleIdentification = withContext(Dispatchers.IO) {
                    currentModule.getIdentification()
                }
                if (moduleIdentification != null) {
                    eepromModule.beforeWrite()
                    statusText += "\nID: " + moduleIdentification!!.id1
                    when (val batteryResult = readBatteryInfo()) {
                        is BatteryReadResult.Ok -> {
                            val battery = batteryResult.info
                            val chargingOrPluggedText = if (battery.isCharging || battery.isPlugged) "Yes" else "No"
                            statusText += "\nBattery Level : ${battery.level} %"
                            statusText += "\nCharging or plugged : $chargingOrPluggedText"
                            if (battery.level < 20 && !battery.isCharging && !battery.isPlugged) {
                                statusText += "\nBattery level is too low for EEPROM Programming! Charge battery or connect charger!"
                                return@launch
                            }
                        }
                        BatteryReadResult.NoBattery -> statusText += "\nNo battery - mains powered"
                        BatteryReadResult.Failed -> statusText += "\nFailed to read battery state!"
                    }
                    if (liveDataModule == null && moduleIPK != null) {
                        liveDataModule = moduleIPK as? IModuleLiveData
                        liveDataModuleAddress = moduleIPK.getModuleAddress()
                    }
                    if (liveDataModule != null)
                    {
                        statusText += "\n" + voltageCheckText
                        var voltage = liveDataModule.getModuleVoltage(liveDataModuleAddress, 2)
                        if (voltage.isNaN() && moduleIPK != null)
                        {
                            liveDataModule = moduleIPK as IModuleLiveData
                            liveDataModuleAddress = moduleIPK.getModuleAddress()
                            voltage = liveDataModule.getModuleVoltage(liveDataModuleAddress, 2)
                        }
                        if (voltage.isNaN())
                        {
                            statusText += "\n" + voltageCheckFailedText
                        }
                        else
                        {
                            statusText += "\n" + String.format(voltageText, "%.2f".format(voltage))
                            if (voltage < 12.5){
                                statusText += "\n" + voltageTooLowText
                                return@launch
                            }
                        }
                    }
                    var currentTry = 0
                    val maxTry = 3
                    var i = 0
                    while (i < eepromSize) {
                        // Ostatnia paczka przyciety do granicy EEPROM (rozmiar moze nie byc
                        // wielokrotnoscia kroku, np. BMBT: 255 B).
                        val chunk = minOf(bytesToWrite, eepromSize - i)
                        val data = fileData.copyOfRange(i, i + chunk)
                        val success =
                            withContext(Dispatchers.IO) { eepromModule.writeEeprom(i, data) }
                        val progress = ((i + chunk).toFloat() / eepromSize * 100).toInt()
                        statusText += "\n" + String.format(
                            eepromWriteProgressText,
                            String.format(Locale.US, "%2d%%", progress)
                        )

                        if (success) {
                            currentTry = 0
                        } else {
                            statusText += "\n" + eepromWriteErrorText
                            currentTry++
                            if (currentTry == maxTry) {
                                statusText += "\n" + String.format(
                                    eepromWriteSkipText,
                                    String.format("%2X", i * bytesToWrite)
                                )
                                currentTry = 0
                            } else {
                                i -= chunk
                                currentTry++
                            }
                        }
                        i += chunk
                    }
                    statusText += "\n" + moduleResetText
                    currentModule.reset()
                }
            } else {
                statusText += "\n" + eepromProgrammingWrongFileSizeText
            }
            statusText += "\n" + eepromReadEndText
        }
    }

    val saveFileLauncher = fileSupport.saveDataLauncher(
        data = eepromReadData,
        onResult = {result ->
            statusText += "\n" + if (result)
                String.format(eepromWriteToFileText,fileName)
            else
                eepromWriteToFileFailText
        })
    val readFileAndProgram = fileSupport.readDataLauncher(
        onResult = { result -> programmingProcess(result)
        }
    )

    Scaffold(
        modifier = Modifier
            .padding(all = 0.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
        snackbarHost = { ToafSnackBarHost(hostState = snackBarHostState) },
        containerColor = ToafColors.toafBackground,
        topBar = {
            TopBarModel(
                titleResource = Res.string.activity_eeprom_programming_tittle,
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
                    buttonText = Res.string.activity_eeprom_programming_read_eeprom,
                    onClick = {
                        scope.launch {
                            eepromReadData = ByteArray(0)
                            val eepromModule = currentModule as IModuleEepromProgramming
                            val eepromSize = eepromModule.getEepromSize()
                            val bytesToRead = eepromModule.getEepromReadSize()
                            statusText = eepromReadStartText
                            statusText += "\n" + eepromReadGetIdentificationText
                            moduleIdentification = withContext(Dispatchers.IO){
                                currentModule.getIdentification()}
                            if (moduleIdentification != null) {
                                eepromModule.beforeRead()
                                statusText += "\nID: " + moduleIdentification!!.id1
                                var i = 0
                                do
                                {
                                    // Ostatnia paczka przyciety do granicy EEPROM - rozmiar moze nie byc
                                    // wielokrotnoscia kroku (np. BMBT: 255 B, bajt 0xFF nieodczytywalny).
                                    val chunk = minOf(bytesToRead, eepromSize - i)
                                    val progress = ((i + chunk).toFloat() / eepromSize * 100).toInt()
                                    statusText += "\n" +String.format(eepromReadProgressText, String.format(Locale.US,"%2d%%", progress))
                                    val data = withContext(Dispatchers.IO) {
                                        eepromModule.readEeprom(i,chunk)}
                                    if (data != null)
                                        eepromReadData +=data
                                    else
                                    {
                                        statusText += "\n" +eepromReadErrorText
                                        statusText += "\n" + eepromReadEndText
                                        break
                                    }
                                    i += chunk
                                } while (i < eepromSize)

                                if (eepromReadData.size == eepromSize)
                                {
                                    val vin = currentModule.getVIN()
                                    val moduleName = currentModule.getModuleName()
                                    statusText +="\n" + String.format(eepromReadEepromSizeText, eepromReadData.size)
                                    val simpleDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
                                    fileName = String.format(
                                        "EEPROM_%s_%s_%s.bin",
                                        moduleName,
                                        moduleIdentification?.id1 ?: "",
                                        simpleDateFormat.format(Calendar.getInstance().time))
                                    saveFileLauncher(fileName)
                                    val jsonObject = buildJsonObject {
                                        put("vin", vin ?: moduleName)
                                        put("module", currentModule.getModuleName())
                                        put("id1", moduleIdentification?.id1)
                                        put("id2", moduleIdentification?.id2)
                                    }
                                    withContext(Dispatchers.IO) {
                                        val backupResult = toafApi.uploadFlash( jsonObject, eepromReadData)
                                        statusText += "\n" + String.format(eepromBackupResultText, if (backupResult) eepromResultOkText else eepromResultFailedText)
                                    }
                                }
                                else
                                {
                                    statusText += "\n" + eepromReadErrorText
                                    statusText += "\n" + eepromReadSizeMismatchErrorText
                                }
                            }
                            else
                            {
                                statusText += "\n" + eepromReadErrorText
                            }
                            statusText += "\n" + eepromProgrammingEndText
                        }
                    })
                ToafButtons.GreenButton(
                    modifier = Modifier.padding(5.dp).fillMaxWidth(),
                    buttonText = Res.string.activity_eeprom_programming_write_eeprom,
                    onClick = {
                        if (!(currentModule as IModuleEepromProgramming).eepromWriteAvailable)
                            showResult(scope, snackBarHostState, false, null, notSupportedText)
                        else if (userInfo.isFullVersion())
                            readFileAndProgram()
                        else
                            showFullVersionDialog = true
                    })

                StatusLogWindow(
                    statusText = statusText.ifEmpty { stringResource(Res.string.activity_eeprom_programing_warning)},
                    modifier = Modifier.fillMaxWidth().padding(start = 5.dp, top = 20.dp, end = 5.dp, bottom = 20.dp)
                )
            }
        }
    )
}

@Composable
fun StatusLogWindow(
    statusText: String,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(statusText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color(0xFFEEEEEE))
            .border(1.dp, Color.Gray)
            .padding(8.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            modifier = Modifier.fillMaxHeight(),
            text = statusText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.Black
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0x0FF00000)
@Composable
fun ActivityEepromProgrammingPreview()
{
    ActivityEepromProgrammingModel(
        moduleKoinName = KoinNames.ModuleR40Bcu,
        onBackClick = {})
}