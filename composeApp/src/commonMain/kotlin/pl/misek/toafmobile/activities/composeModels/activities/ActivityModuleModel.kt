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
import pl.misek.toafmobile.modules.interfaces.IModule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.PlatformType
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogShowErrors
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogShowIdentification.ShowIdentification
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.snackbar.ToafSnackBarHost
import pl.misek.toafmobile.getPlatformType
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceCoding
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleFlashProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.tools.ShareHandler
import pl.misek.toafmobile.tools.rememberShareHandler
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun ActivityModuleModel(
    moduleKoinName: KoinNames,
    onCodingClick : () -> Unit,
    onCodingAdvanceClick: () -> Unit,
    onLiveDataClick : () -> Unit,
    onControlClick : () ->Unit,
    onEepromProgrammingClick : () ->Unit,
    onFlashProgrammingClick : () ->Unit,
    onBackClick : () ->Unit
    ) {
    val modulesManage : ModulesManager = koinInject()
    val currentModule = modulesManage.getCurrentModules().firstOrNull {it.getKoinName() == moduleKoinName} as IModule
    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showModuleErrors by remember { mutableStateOf(false) }
    var moduleErrors by remember { mutableStateOf<List<ModuleError>?>(null) }
    var showModuleInfo by remember { mutableStateOf(false) }
    var moduleIdentification by remember { mutableStateOf<ModuleInfo?>(null) }
    var moduleVin by remember { mutableStateOf<String?>(null) }
    var moduleOdometer by remember { mutableStateOf<String?>(null) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var actionType by remember {mutableStateOf (ActionTypeEnum.None)}
    val shareHandler = rememberShareHandler()
    val moduleIdentificationFailText = stringResource(Res.string.alert_module_info_read_error)
    val moduleReadErrorFailText = stringResource(Res.string.message_errors_read_failure)
    val moduleHasNoErrorsText = stringResource(Res.string.message_errors_no_errors)
    val moduleErrorClearSuccessText = stringResource(Res.string.message_errors_cleared)
    val moduleErrorClearFailText = stringResource(Res.string.message_errors_clear_failure)
    val dataCopiedToClipboard = stringResource(Res.string.data_copied_to_clipboard)
    Scaffold(
        modifier = Modifier
            .padding(all = 0.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
        containerColor = ToafColors.toafBackground,
        snackbarHost = { ToafSnackBarHost(hostState = snackBarHostState) },
        topBar = {
            TopBarModel(
                titleResource = Res.string.activity_diagnostic_title,
                subTitle = currentModule.getModuleDescription(),
                onBackClicked = { onBackClick() })
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .background(ToafColors.toafBackground)
            ) {
                if (showProgressDialog)
                {
                    ToafProgressDialog(
                        action = action,
                        onSuccess = {result ->
                            when(actionType)
                            {
                                ActionTypeEnum.ReadErrors ->
                                {
                                    moduleErrors = (result as? List<*>)?.filterIsInstance<ModuleError>()
                                    if (moduleErrors == null) {
                                        showResult(scope,  snackBarHostState, false,null,moduleReadErrorFailText)
                                    }else {
                                        if (moduleErrors?.isEmpty() == true)
                                            showResult(scope,  snackBarHostState, true,moduleHasNoErrorsText,null)
                                        else
                                            showModuleErrors = true
                                    }
                                }
                                ActionTypeEnum.ReadIdentification ->
                                {
                                    val triple = result as? Triple<*, *, *>
                                    triple?.let {
                                        moduleIdentification = it.first as ModuleInfo?
                                        moduleVin = it.second as String?
                                        moduleOdometer = it.third as String?
                                        if (moduleIdentification == null && moduleVin == null && moduleOdometer == null)
                                            showResult(scope,  snackBarHostState, false,null, moduleIdentificationFailText)
                                        else
                                            showModuleInfo = true
                                    }
                                }
                                ActionTypeEnum.ClearErrors -> {
                                    val isSuccess = result as? Boolean ?: false
                                    showResult(
                                        scope,
                                        snackBarHostState,
                                        isSuccess,
                                        moduleErrorClearSuccessText,
                                        moduleErrorClearFailText
                                    )
                                    if (isSuccess)
                                        showModuleErrors = false
                                }
                                ActionTypeEnum.DataCopiedToClipboard -> {
                                    if (getPlatformType() == PlatformType.Desktop) {
                                        showResult(
                                            scope,
                                            snackBarHostState,
                                            true,
                                            dataCopiedToClipboard,
                                            null
                                        )
                                    }
                                }
                                else -> {}
                            }
                            showProgressDialog = false
                        },
                        onDismissRequest = {showProgressDialog = false}
                    )
                }
                ToafButtons.GreenButton(
                    modifier = Modifier
                        .padding(5.dp)
                        .fillMaxWidth(),
                    buttonText = Res.string.module_ident_description,
                    onClick = {
                        actionType = ActionTypeEnum.ReadIdentification
                        action = {
                            Triple(
                                first = currentModule.getIdentification(),
                                second = currentModule.getVIN(),
                                third = currentModule.getOdometer()
                            )
                        }
                        showProgressDialog = true
                    }
                )
                ToafButtons.GreenButton(
                    modifier = Modifier.padding(5.dp).fillMaxWidth(),
                    buttonText = Res.string.module_error_read,
                    onClick = {
                        actionType = ActionTypeEnum.ReadErrors
                        action = { currentModule.getAllErrors()}
                        showProgressDialog = true
                    }
                )
                if (currentModule is IModuleCoding) {
                    ToafButtons.GreenButton(
                        modifier = Modifier.padding(5.dp).fillMaxWidth(),
                        buttonText = Res.string.module_coding,
                        onClick = { onCodingClick() }
                    )
                }
                if (currentModule is IModuleAdvanceCoding)
                {
                    ToafButtons.GreenButton(
                        modifier = Modifier.padding(5.dp).fillMaxWidth(),
                        buttonText = Res.string.activity_advance_coding,
                        onClick = { onCodingAdvanceClick() }
                    )
                }
                if (currentModule is IModuleLiveData) {
                    ToafButtons.GreenButton(
                        modifier = Modifier.padding(5.dp).fillMaxWidth(),
                        buttonText = Res.string.module_live_data,
                        onClick = { onLiveDataClick()}
                    )
                }
                if (currentModule is IModuleEepromProgramming)
                {
                    ToafButtons.GreenButton(
                        modifier = Modifier.padding(5.dp).fillMaxWidth(),
                        buttonText = Res.string.activity_eeprom_programing,
                        onClick = { onEepromProgrammingClick() }
                    )
                }
                if (currentModule is IModuleFlashProgramming)
                {
                    ToafButtons.GreenButton(
                        modifier = Modifier.padding(5.dp).fillMaxWidth(),
                        buttonText = Res.string.activity_flash_programing,
                        onClick = { onFlashProgrammingClick() }
                    )
                }
                if (currentModule is IModuleControl) {
                    ToafButtons.GreenButton(
                        modifier = Modifier.padding(5.dp).fillMaxWidth(),
                        buttonText = Res.string.module_control,
                        onClick = { onControlClick()}
                    )
                }
                if (showModuleInfo)
                    ShowIdentification(
                        info = moduleIdentification,
                        vin = moduleVin,
                        odometer = moduleOdometer,
                        onConfirm = {
                            moduleIdentification = null
                            moduleVin = null
                            moduleOdometer = null
                            showModuleInfo = false
                        })
                if (showModuleErrors) {
                    DialogShowErrors.ShowErrors(
                        errors = moduleErrors,
                        onClose = {
                            showModuleErrors = false
                            moduleErrors = null
                        },
                        onDeleteErrors = {
                            actionType = ActionTypeEnum.ClearErrors
                            action = { currentModule.clearAllErrors() }
                            showProgressDialog = true
                        },
                        onShare = { data ->
                            actionType = ActionTypeEnum.DataCopiedToClipboard
                            action = { }
                            shareHandler.shareMessage(data)
                            showProgressDialog = true
                        }
                    )
                }
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0x0FF00000)
@Composable
fun ActivityModuleModelPreview()
{
    ActivityModuleModel(
        moduleKoinName = KoinNames.ModuleR40Bcu,
        onCodingClick = {},
        onCodingAdvanceClick = {},
        onLiveDataClick = {},
        onControlClick = {},
        onEepromProgrammingClick = {},
        onFlashProgrammingClick = {},
        onBackClick = {}
    )
}