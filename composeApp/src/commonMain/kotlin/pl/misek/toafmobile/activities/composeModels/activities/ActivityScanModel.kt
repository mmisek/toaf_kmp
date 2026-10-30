package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogInput
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.composeModels.menus.ToafVerticalScrollbar
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.containers.SERVOTRONIC_RAW_MAX
import pl.misek.toafmobile.modules.containers.ServotronicMap
import pl.misek.toafmobile.modules.containers.ServotronicPoint
import pl.misek.toafmobile.modules.containers.servotronicMaToRaw
import pl.misek.toafmobile.modules.containers.servotronicRawToMa
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.l322.ModuleBCU
import pl.misek.toafmobile.modules.interfaces.IModuleServotronic
import pl.misek.toafmobile.modules.r40.ModuleDDE
import pl.misek.toafmobile.modules.r40.ModuleIPK
import pl.misek.toafmobile.modules.r40.ModuleLSM
import pl.misek.toafmobile.network.UserInfo
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
    ScanCarContent(
        onBackClick = { onBackClick() },
        onScanClick = {
            actionType = ActionTypeEnum.ScanCar
            action = {
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
                    for ((module, present, errors) in scanResult) {
                        if (present && !errors.isNullOrEmpty())
                            module.getAllErrors()
                    }
                true}
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
                        showResult(scope, snackBarHostState,!result.isNullOrEmpty(),"Scan complete", "Not found any module")
                    }
                    ActionTypeEnum.ClearAllErrors -> {
                        val result = actionResult as? Boolean
                        showResult(scope, snackBarHostState,result == true, "Clear errors complete", "Clear errors failed")
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
                    "Unexpected error"
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
    scanResult: List<ScanResult>)
{
    Scaffold(
        modifier = Modifier
            .padding(all = 0.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
        containerColor = ToafColors.toafBackground,
        topBar = {
            TopBarModel(
                titleResource = Res.string.srs2_coding_airbag_driver,
                subTitle = null,
                onBackClicked = { onBackClick()})
        },
        content = { paddingValues ->
                Column(
                    modifier = Modifier.padding(paddingValues = paddingValues).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                {
                    Text(
                        text = "Car scan",
                        textAlign = TextAlign.Center,
                        style = TextStyle(color = Color.White, fontSize = 16.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 2.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    )
                    {
                        ToafButtons.GreenButton(
                            buttonText = "Scan car",
                            modifier = Modifier.weight(1f),
                            onClick = { onScanClick() }
                        )
                        ToafButtons.GreenButton(
                            buttonText = "Clear all errors",
                            modifier = Modifier.weight(1f),
                            onClick = { onClearAllClick() },
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    CarScanTable(
                        scanResult = scanResult
                    )

                    Spacer(Modifier.height(8.dp))
                }
        })
}

@Composable
private fun CarScanTable(scanResult : List<ScanResult>)
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
                        point.errors
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
        Text(text = "Module", style = style, modifier = Modifier.weight(1f))
        Text(text = "Present", style = style, modifier = Modifier.weight(1f))
        Text(text = "Errors", style = style, modifier = Modifier.weight(1f))
        Text(text = "", style = style, modifier = Modifier.weight(1f))
    }
}
@Composable
private fun ScanCarTableRow(module : IModule, present : Boolean, errors: List<ModuleError>?, isHeader : Boolean = false)
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

        Text(text = module.getModuleName() + " " + module.getModuleDescription(), style = style, modifier = Modifier.weight(1f))
        Text(text = if (present) "YES" else "NO", style = style, modifier = Modifier.weight(1f))
        Text(text = errors?.count()?.toString() ?: "---", style = style, modifier = Modifier.weight(1f))
        ToafButtons.GreenButton(
            buttonText = "clear",
            modifier = Modifier
                .padding(3.dp),
            onClick = {module.clearAllErrors()}
        )
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
        scanResult = scanResult
    )
}
