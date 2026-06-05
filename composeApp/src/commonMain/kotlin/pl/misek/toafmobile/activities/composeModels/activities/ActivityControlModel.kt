package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.control.ControlElement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.ToafConstants
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.definitions.ToafStyles
import pl.misek.toafmobile.activities.snackbar.ToafSnackBarHost
import pl.misek.toafmobile.control.FastControlElement
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.tools.JsonSupport
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun ActivityControlModel(moduleKoinName : KoinNames, onBackClick : () -> Unit) {
    val modulesManage : ModulesManager = koinInject()
    val database : Database = koinInject()
    val snackBarHostState = remember { SnackbarHostState() }
    val currentModule = modulesManage.getCurrentModules().firstOrNull {it.getKoinName() == moduleKoinName} as IModule
    var controlElements by remember { mutableStateOf<List<ControlElement>?>(emptyList()) }
    var databaseElements by remember { mutableStateOf<List<FastControlElement>?>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var refreshNeeded by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit, refreshNeeded) {
        withContext(Dispatchers.IO)
        {
            controlElements = JsonSupport.getControlElements(
                moduleName = currentModule.getModuleName())
            databaseElements = database.selectFastControlElementsForModule(currentModule.getModuleName())
            isLoading = false
        }
    }
    Scaffold(
        modifier = Modifier
            .padding(all = 0.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
        snackbarHost = { ToafSnackBarHost(snackBarHostState) },
        containerColor = ToafColors.toafBackground,
        topBar = {
            TopBarModel(
                titleResource = Res.string.activity_control_subtitle,
                onBackClicked = { onBackClick() })
        },
        content = { paddingValues ->
            if (isLoading)
            {
                Box(modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize(),
                    contentAlignment = Alignment.Center)
                {
                    CircularProgressIndicator(
                        color = ToafColors.toafTextColorWhite
                    )
                }
            }
            else
            {
            Column(modifier = Modifier
                .padding(paddingValues)
                .background(ToafColors.toafBackground)) {
                ControlList(
                    items = controlElements,
                    module = currentModule,
                    databaseItems = databaseElements,
                    snackBarHostState = snackBarHostState,
                    onRefreshNeeded = {refreshNeeded++}
                )
            }
            }
        }
    )
}

@Composable
private fun ControlList(items: List<ControlElement>?, module: IModule, databaseItems: List<FastControlElement>?, snackBarHostState: SnackbarHostState, onRefreshNeeded: () -> Int)
{
    LazyColumn (
        modifier = Modifier.
        background(ToafColors.toafBackground),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ){
        items(items!!.size) { index ->
            //val isFastControlElement = databaseItems?.first { x : FastControlElement -> x.}
            //?.anyMatch { x: FastControlElement -> x. name.equals(items[index].name) }
            val isFastControlElement = false
            ControlListPlaceholder(items[index], module, isFastControlElement, snackBarHostState = snackBarHostState, onRefreshNeeded)
        }
    }
}

@Composable
private fun ControlListPlaceholder(item :ControlElement?,module: IModule, isFastControlElement : Boolean, snackBarHostState: SnackbarHostState,onRefreshNeeded : () -> Int)
{
    val database : Database = koinInject()
    val scope = rememberCoroutineScope()
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var actionType by remember {mutableStateOf (ActionTypeEnum.None)}
    var showProgressDialog by remember { mutableStateOf(false) }
    val fastActionDeletedSuccessText = stringResource(Res.string.activity_control_delete_fast_action_successful)
    val fastActionItemSizeReachedLimit = stringResource(Res.string.activity_control_fast_action_limit_reached)
    val controlSuccessText = stringResource(Res.string.activity_control_control_correct)
    val controlFailText = stringResource(Res.string.activity_control_control_error)
    var sliderPosition by remember(item) {
        mutableFloatStateOf(item?.controlSetValue?.min?.toFloat() ?: 0f)
    }
    Column (
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .border(1.dp, Color.LightGray)
            .background(ToafColors.toafBackground),
    )
    {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp)
                .background(ToafColors.toafBackground),
            verticalAlignment = Alignment.CenterVertically,
        )
        {
            Text(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .padding(1.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            action = {
                                val moduleName = module.getModuleName()
                                val itemName = item?.name ?: ""
                                val element = database.selectFastControlElement(moduleName, itemName)
                                if (element !=null )
                                {
                                    actionType = ActionTypeEnum.DeleteFastAction
                                    database.deleteFastActionControl(moduleName,itemName) > 0
                                }
                                else
                                {
                                    val n = database.selectAllFastLiveDataElements().count()
                                    if (n > ToafConstants.TOAF_LITE_FAST_CONTROL_LIMIT)
                                    {
                                        actionType = ActionTypeEnum.FastActionLimit
                                        false
                                    }
                                    else
                                    {
                                        actionType = ActionTypeEnum.AddFastAction
                                        /*
                                        withContext(Dispatchers.Main)
                                        {
                                            Dialogs.addNewFastControlElement(
                                                localContext,
                                                moduleName,
                                                item,
                                                sliderPosition.toInt()
                                            )
                                        }
                                         */
                                        showProgressDialog = true
                                    }
                                }
                            }
                        }
                    ),

                text = (if (isFastControlElement) "[F] " else  "") + item?.description?.let { stringResource(it) },
                color = ToafColors.toafTextColorWhite,
            )
            ToafButtons.GreenButton(
                buttonText = Res.string.activity_control_button_start,
                modifier = Modifier.weight(0.4f).padding(ToafStyles.buttonPadding),
                onClick = {
                    actionType = ActionTypeEnum.Control
                    action = {
                        item?.let {
                            (module as IModuleControl).control(it)
                        }
                    }
                    showProgressDialog = true
                }
            )
        }
        if (item?.controlSetValue != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp)
                    .background(ToafColors.toafBackground),
                verticalAlignment = Alignment.CenterVertically,
            )
            {
                Text(modifier = Modifier
                    .padding(1.dp),
                    text = item.controlSetValue!!.min.toString(),
                    color = ToafColors.toafTextColorWhite)
                Slider(
                    modifier = Modifier
                        .weight(1f)
                        .padding(1.dp),
                    value = sliderPosition,
                    valueRange = item.controlSetValue!!.min.toFloat() ..item.controlSetValue!!.max.toFloat(),
                    onValueChange = { newValue ->
                        if (item.controlSetValue != null)
                            item.controlSetValue?.value = newValue.toInt()
                        sliderPosition = newValue
                    }
                )
                Text(modifier = Modifier
                    .padding(1.dp),
                    text = item.controlSetValue!!.max.toString() + item.controlSetValue!!.unit,
                    color = ToafColors.toafTextColorWhite)
            }
        }
        if (showProgressDialog)
        {
            ToafProgressDialog(
                action = {action()},
                onSuccess = { result ->
                    when (actionType) {
                        ActionTypeEnum.Control -> {
                            showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostState,
                                result = result as Boolean,
                                onSuccess = controlSuccessText,
                                onFail = controlFailText
                            )
                            showProgressDialog = false
                        }
                        ActionTypeEnum.FastActionLimit ->
                        {
                            showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostState,
                                result = false,
                                onSuccess = null,
                                onFail = fastActionItemSizeReachedLimit
                            )
                            showProgressDialog = false
                        }
                        ActionTypeEnum.DeleteFastAction ->
                        {
                            showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostState,
                                result = true,
                                onSuccess = fastActionDeletedSuccessText,
                                onFail = null
                            )
                            onRefreshNeeded()
                            showProgressDialog = false
                        }
                        ActionTypeEnum.AddFastAction ->
                        {
                            onRefreshNeeded()
                            showProgressDialog = false
                        }
                        else ->{}
                    }
                },
                onDismissRequest = { showProgressDialog = false}
            )

        }
    }
}

@Preview(showBackground = true, backgroundColor = 0x0FF00000)
@Composable
fun ActivityControlModulePreview()
{
    ActivityControlModel(KoinNames.ModuleR40Bcu) {}
}