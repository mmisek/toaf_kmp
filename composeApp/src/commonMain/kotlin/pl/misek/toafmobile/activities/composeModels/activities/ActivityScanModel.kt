package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.composeModels.menus.ToafVerticalScrollbar
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.snackbar.ToafSnackBarHost
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.l322.ModuleBCU
import pl.misek.toafmobile.modules.r40.ModuleIPK
import pl.misek.toafmobile.modules.r40.ModuleLSM
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

private data class ScanResult(
        val module : IModule,
        val modulePresent : Boolean,
        val errors : List<ModuleError>?
        )
@Composable
fun ActivityScanModel(onBackClick :() -> Unit) {
    val modulesManager : ModulesManager = koinInject()
    val carModules = modulesManager.getCurrentModules()
    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var actionType by remember { mutableStateOf(ActionTypeEnum.None) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var dialogShowNotFullVersion by remember { mutableStateOf(false) }
    val scanResult = remember { mutableStateListOf<ScanResult>()  }
    val scanSuccessful = stringResource(Res.string.activity_scan_successful)
    val scanFailed = stringResource(Res.string.activity_scan_failed)
    val clearSuccessful = stringResource(Res.string.activity_scan_clear_successful)
    val clearFailed = stringResource(Res.string.activity_scan_clear_failed)
    val unexpectedError = stringResource(Res.string.activity_scan_unexpected_error)
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
                            modulePresent =  ident!= null,
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
                        if (item.modulePresent && !item.errors.isNullOrEmpty()) {
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
        snackBarHostState = snackBarHostState,
        onClearModuleClick = { index ->
            actionType = ActionTypeEnum.ClearErrors
            action = {
                val item = scanResult.getOrNull(index)
                if (item == null || !item.modulePresent)
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

    if (showProgressDialog) {
        ToafProgressDialog(
            action = { action() },
            onSuccess = { actionResult ->
                when (actionType) {
                    ActionTypeEnum.ScanCar -> {
                        val result = actionResult as? List<*>
                        showResult(scope, snackBarHostState,!result.isNullOrEmpty(), scanSuccessful, scanFailed)
                    }
                    ActionTypeEnum.ClearAllErrors,
                    ActionTypeEnum.ClearErrors -> {
                        val result = actionResult as? Boolean
                        showResult(scope, snackBarHostState,result == true, clearSuccessful, clearFailed)
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
private fun ScanCarContent(
    onBackClick: () -> Unit,
    onScanClick : () -> Unit,
    onClearAllClick : () -> Unit,
    onClearModuleClick : (Int) -> Unit,
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

                    Spacer(Modifier.height(8.dp))

                    CarScanTable(
                        scanResult = scanResult,
                        onClearModuleClick = onClearModuleClick
                    )

                    Spacer(Modifier.height(8.dp))
                }
        })
}

@Composable
private fun CarScanTable(scanResult : List<ScanResult>, onClearModuleClick : (Int) -> Unit)
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
                        point.modulePresent,
                        point.errors,
                        onClearClick = { onClearModuleClick(index) }
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
private fun ScanCarTableRow(module : IModule, present : Boolean, errors: List<ModuleError>?, onClearClick : () -> Unit, isHeader : Boolean = false)
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
        Text(text = errors?.count()?.toString() ?: "---", style = style, modifier = Modifier.weight(1f))
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
        ScanResult(bcu,true,listOf(ModuleError(bcu.getModuleAddress(),"Error 00", errorDetails = emptyList()))),
        ScanResult(ipk,false,null),
        ScanResult(lsm, true,emptyList())
    )
    ScanCarContent(
        onBackClick = {},
        onScanClick = {},
        onClearAllClick = {},
        onClearModuleClick = {},
        scanResult = scanResult
    )
}
