package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.BuildKonfig
import pl.misek.toafmobile.PlatformType
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogShowErrors
import pl.misek.toafmobile.activities.composeModels.dialogs.detailsToString
import pl.misek.toafmobile.activities.composeModels.menus.ToafVerticalScrollbar
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.snackbar.ToafSnackBarHost
import pl.misek.toafmobile.getPlatformName
import pl.misek.toafmobile.getPlatformType
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.l322.ModuleBCU
import pl.misek.toafmobile.modules.r40.ModuleIPK
import pl.misek.toafmobile.modules.r40.ModuleLSM
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.tools.rememberShareHandler
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private data class ScanResult(
        val module : IModule,
        val ident : ModuleInfo?,
        val vin : String?,
        val odometer : String?,
        val errors : List<ModuleError>?
        )
@Composable
fun ActivityScanModel(onBackClick :() -> Unit) {
    val toafApi : ToafApi = koinInject()
    val modulesManager : ModulesManager = koinInject()
    val carModules = modulesManager.getCurrentModules()
    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var actionType by remember { mutableStateOf(ActionTypeEnum.None) }
    var showProgressDialog by remember { mutableStateOf(false) }
    val scanResult = remember { mutableStateListOf<ScanResult>()  }
    val shareHandler = rememberShareHandler()
    val scanSuccessful = stringResource(Res.string.activity_scan_successful)
    val scanFailed = stringResource(Res.string.activity_scan_failed)
    val clearSuccessful = stringResource(Res.string.activity_scan_clear_successful)
    val clearFailed = stringResource(Res.string.activity_scan_clear_failed)
    val unexpectedError = stringResource(Res.string.activity_scan_unexpected_error)
    val dataCopiedToClipboard = stringResource(Res.string.data_copied_to_clipboard)
    val scanReport = scanResultToString(scanResult)
    var pendingReportUpload by remember { mutableStateOf(false) }
    var showDialogErrors by remember {mutableStateOf(false)}
    var indexOfErrorsToShow by remember { mutableIntStateOf(0) }
    ScanCarContent(
        onBackClick = { onBackClick() },
        onScanClick = {
            actionType = ActionTypeEnum.ScanCar
            action = {
                scanResult.clear()
                for(module in carModules)
                {
                    val ident = module.getIdentification()
                    scanResult.add(
                        ScanResult(
                            module = module,
                            ident = ident,
                            vin = module.getVIN(),
                            odometer =  module.getOdometer(),
                            errors = if (ident!= null) module.getAllErrors() else null))
                }
                scanResult
            }
            showProgressDialog = true
        },
        onClearAllClick = {
            actionType = ActionTypeEnum.ClearAllErrors
            action = {
                if (scanResult.isEmpty())
                    false
                else {
                    var allCleared = true
                    for ((index, item) in scanResult.withIndex()) {
                        if (item.ident != null  && !item.errors.isNullOrEmpty()) {
                            if (!item.module.clearAllErrors())
                                allCleared = false
                            scanResult[index] = item.copy(errors = item.module.getAllErrors())
                        }
                    }
                    allCleared
                }
            }
            showProgressDialog = true
        },
        onShareClick = {
            actionType = ActionTypeEnum.DataCopiedToClipboard
            action = { scanReport }
            showProgressDialog = true
        },
        onShowErrors = { index ->
            if (scanResult[index].errors != null && scanResult[index].errors?.isNotEmpty() == true)
            {
                indexOfErrorsToShow = index
                showDialogErrors = true
            }
        },
        snackBarHostState = snackBarHostState,
        onClearModuleClick = { index ->
            actionType = ActionTypeEnum.ClearErrors
            action = {
                val item = scanResult.getOrNull(index)
                if (item == null)
                    false
                else {
                    val cleared = item.module.clearAllErrors()
                    scanResult[index] = item.copy(errors = item.module.getAllErrors())
                    cleared
                }
            }
            showProgressDialog = true
        },
        scanResult = scanResult
    )
    if (showDialogErrors)
    {
        DialogShowErrors(
            errors = scanResult[indexOfErrorsToShow].errors,
            onClose = {showDialogErrors = false},
            onDeleteErrors = {
                actionType = ActionTypeEnum.ClearErrors
                action = {
                    scanResult[indexOfErrorsToShow].module.clearAllErrors()
                }
                showProgressDialog = true
            },
            onShare = { data ->
                actionType = ActionTypeEnum.DataCopiedToClipboard
                shareHandler.shareMessage(data)
            }
        )

    }

    if (pendingReportUpload) {
        LaunchedEffect(Unit) {
            val jsonObject = buildJsonObject {
                put("vin", "")
                put("module", "SCAN")
                put("id1", getPlatformName())
                put("id2", BuildKonfig.VERSION_NAME)
            }
            try {
                withContext(Dispatchers.IO) {
                    toafApi.uploadFlash(jsonObject, scanReport.toByteArray())
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "ActivityScanModel [uploadScanReport], $ex" }
            } finally {
                pendingReportUpload = false
            }
        }
    }
    if (showProgressDialog) {
        ToafProgressDialog(
            action = { action() },
            onSuccess = { actionResult ->
                when (actionType) {
                    ActionTypeEnum.ScanCar -> {
                        val result = actionResult as? List<*>
                        pendingReportUpload = true
                        showResult(
                            scope,
                            snackBarHostState,
                            !result.isNullOrEmpty(),
                            scanSuccessful,
                            scanFailed
                        )
                    }

                    ActionTypeEnum.ClearAllErrors,
                    ActionTypeEnum.ClearErrors -> {
                        val result = actionResult as? Boolean
                        showResult(
                            scope,
                            snackBarHostState,
                            result == true,
                            clearSuccessful,
                            clearFailed
                        )
                    }

                    ActionTypeEnum.DataCopiedToClipboard -> {
                        val result = actionResult as? String
                        if (result != null) {
                            shareHandler.shareMessage(result)

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
                    }

                    else -> {}
                }
            },
            onError = {
                showResult(
                    scope = scope,
                    snackBarHostState = snackBarHostState,
                    false,
                    null,
                    unexpectedError
                )
            },
            onDismissRequest = {
                actionType = ActionTypeEnum.None
                showProgressDialog = false })
    }
}

@Composable
private fun scanResultToString(data : List<ScanResult>) : String {
    val simpleDateFormat = remember { SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US) }
    val separator = "--------------------------------------------------------\n"
    val readError = stringResource(Res.string.activity_scan_report_read_error)
    val sb = StringBuilder()
    sb.append(separator)
    sb.append("${stringResource(Res.string.activity_scan_report_title)}\n")
    sb.append("${stringResource(Res.string.activity_scan_report_version)} : ${BuildKonfig.VERSION_NAME}\n")
    sb.append("${stringResource(Res.string.activity_scan_report_platform)} : ${getPlatformName()}\n")
    sb.append("${stringResource(Res.string.activity_scan_report_date)} : ${simpleDateFormat.format(Calendar.getInstance().time)}\n")
    sb.append(separator)
    sb.append("${stringResource(Res.string.activity_scan_report_modules_scanned)} : ${data.size}\n")
    sb.append("${stringResource(Res.string.activity_scan_report_modules_found)} : ${data.count { x -> x.ident != null }}\n")
    sb.append(separator)
    val moduleNameLabel = stringResource(Res.string.activity_scan_report_module_name)
    val moduleVinLabel = stringResource(Res.string.activity_scan_report_module_vin)
    val moduleOdometerLabel = stringResource(Res.string.activity_scan_report_module_odometer)
    val errorsCountLabel = stringResource(Res.string.activity_scan_report_errors_count)
    for ((module, ident, vin, odometer, errors) in data.filter { x -> x.ident != null }) {
        sb.append("$moduleNameLabel : ${module.getModuleName()}\n")
        sb.append("${ident?.getFormatedInfo()}\n")
        if (!vin.isNullOrEmpty())
            sb.append("$moduleVinLabel : $vin\n")
        if (!odometer.isNullOrEmpty())
            sb.append("$moduleOdometerLabel : $odometer\n")
        sb.append("$errorsCountLabel : ${errors?.count() ?: readError}\n")
        for (error in errors ?: emptyList()) {
            sb.append(error.code.uppercase() + " - " + error.description?.let { stringResource(it) } + "\n")
            sb.append(detailsToString(error))
        }
        sb.append(separator)
    }
    return sb.toString()
}

@Composable
private fun ScanCarContent(
    onBackClick: () -> Unit,
    onScanClick : () -> Unit,
    onClearAllClick : () -> Unit,
    onClearModuleClick : (Int) -> Unit,
    onShareClick : () -> Unit,
    onShowErrors : (Int) -> Unit,
    scanResult: List<ScanResult>,
    snackBarHostState: SnackbarHostState = remember { SnackbarHostState() })
{
    Scaffold(
        modifier = Modifier
            .padding(all = 0.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
        containerColor = ToafColors.toafBackground,
        snackbarHost = { ToafSnackBarHost(hostState = snackBarHostState) },
        topBar = {
            TopBarModel(
                titleResource = Res.string.activity_scan_title,
                subTitle = null,
                onBackClicked = { onBackClick()})
        },
        content = { paddingValues ->
                Column(
                    modifier = Modifier.padding(paddingValues = paddingValues).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    )
                    {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    )
                    {
                        ToafButtons.GreenButton(
                            buttonText = stringResource(Res.string.activity_scan_button_scan),
                            modifier = Modifier.weight(1f),
                            onClick = { onScanClick() }
                        )
                        ToafButtons.GreenButton(
                            buttonText = stringResource(Res.string.activity_scan_button_clear_all),
                            modifier = Modifier.weight(1f),
                            onClick = { onClearAllClick() },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ToafButtons.GreenButton(
                            buttonText = stringResource(Res.string.activity_scan_button_share),
                            modifier = Modifier.weight(1f),
                            onClick = { onShareClick() },
                            enabled = scanResult.isNotEmpty()
                        )
                    }
                    }

                    Spacer(Modifier.height(8.dp))

                    CarScanTable(
                        scanResult = scanResult,
                        onClearModuleClick = onClearModuleClick,
                        onShowErrors = onShowErrors,
                    )

                    Spacer(Modifier.height(8.dp))
                }
        })
}

@Composable
private fun CarScanTable(scanResult : List<ScanResult>, onClearModuleClick : (Int) -> Unit, onShowErrors: (Int) -> Unit)
{
    val listState = rememberLazyListState()
    Column(
        modifier = Modifier
            .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
            .background(ToafColors.toafBackground, RoundedCornerShape(6.dp))
    )
    {
        ScanCarTableRowHeader()
        HorizontalDivider(color = Color.LightGray)
        Box(modifier = Modifier.fillMaxSize())
        {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(scanResult) { index, point ->
                    ScanCarTableRow(
                        point.module,
                        ident = point.ident,
                        point.errors,
                        onClearClick = { onClearModuleClick(index) },
                        onShowErrors =  { onShowErrors(index) }
                    )
                }
            }
            Box(
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                ToafVerticalScrollbar(listState = listState)
            }
        }

    }
}
@Composable
private fun ScanCarTableRowHeader()
{
    val style = TextStyle(
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    )
    {
        Text(text = stringResource(Res.string.activity_scan_column_module), style = style, modifier = Modifier.weight(1f))
        Text(text = stringResource(Res.string.activity_scan_column_present), style = style, modifier = Modifier.weight(1f))
        Text(text = stringResource(Res.string.activity_scan_column_errors), style = style, modifier = Modifier.weight(1f))
        Text(text = "", style = style, modifier = Modifier.weight(1f))
    }
}
@Composable
private fun ScanCarTableRow(module : IModule, ident : ModuleInfo?, errors: List<ModuleError>?, onClearClick : () -> Unit, onShowErrors : () -> Unit, isHeader : Boolean = false)
{
    val style = TextStyle(
        color = Color.White,
        fontSize = if (isHeader) 14.sp else 16.sp,
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    )
    {

        val present = ident != null
        val description = module.getModuleDescription()?.let { " - " + stringResource(it) } ?: ""
        Text(text = "${module.getModuleName()} $description".trim(), style = style, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (present) Icons.Default.Check else Icons.Default.Close,
                contentDescription = stringResource(if (present) Res.string.activity_scan_present_yes else Res.string.activity_scan_present_no),
                tint = if (present) ToafColors.toafToastSuccess else ToafColors.toafToastError
            )
        }
        Text(
            text = errors?.count()?.toString() ?: "---",
            style = style,
            modifier = Modifier.weight(1f).clickable(onClick = {onShowErrors()}))
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            ToafButtons.GreenButton(
                buttonText = stringResource(Res.string.activity_scan_button_clear),
                modifier = Modifier
                    .padding(3.dp),
                onClick = { onClearClick() },
                enabled = !errors.isNullOrEmpty()
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xff000000)
@Composable
fun ActivityScanModel()
{
    val bcu = ModuleBCU(null)
    val ipk = ModuleIPK(null)
    val lsm = ModuleLSM(null)
    val scanResult = listOf(
        ScanResult(bcu, ModuleInfo(),"","",listOf(ModuleError(bcu.getModuleAddress(),"Error 00", errorDetails = emptyList()))),
        ScanResult(ipk,ModuleInfo(),"","", null),
        ScanResult(lsm, null,"","", emptyList())
    )
    ScanCarContent(
        onBackClick = {},
        onScanClick = {},
        onShareClick = {},
        onClearAllClick = {},
        onClearModuleClick = {},
        onShowErrors = {},
        scanResult = scanResult
    )
}
