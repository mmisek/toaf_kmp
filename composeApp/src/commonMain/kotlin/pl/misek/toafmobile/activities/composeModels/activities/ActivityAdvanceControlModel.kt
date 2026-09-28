package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceControl
import pl.misek.toafmobile.modules.interfaces.AdvanceControlElement
import pl.misek.toafmobile.control.ControlElement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.runtime.collectAsState
import pl.misek.toafmobile.viewModels.UserInfoViewModel
import pl.misek.toafmobile.activities.ToafConstants
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.composeModels.menus.ToafVerticalScrollbar
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.definitions.ToafStyles
import pl.misek.toafmobile.activities.snackbar.ToafSnackBarHost
import pl.misek.toafmobile.control.FastControlElement
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.l322.ModuleLWS
import pl.misek.toafmobile.network.UserInfo
import pl.misek.toafmobile.tools.JsonSupport
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun ActivityAdvanceControlModel(moduleKoinName : KoinNames, onBackClick : () -> Unit) {
    val modulesManage : ModulesManager = koinInject()
    val currentModule = modulesManage.getCurrentModules().firstOrNull {it.getKoinName() == moduleKoinName}
    val advanceControlElements = remember(currentModule) {
        (currentModule as? IModuleAdvanceControl)?.getAdvanceControlElements() ?: emptyList()
    }
    val userInfoViewModel : UserInfoViewModel = koinViewModel()
    val userInfo by userInfoViewModel.userInfo.collectAsState()
    ActivityAdvanceControlContent(advanceControlElements, userInfo, onBackClick)
}

@Composable
private fun ActivityAdvanceControlContent(advanceControlElements : List<AdvanceControlElement>, userInfo : UserInfo, onBackClick : () -> Unit) {
    val snackBarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedElement by remember { mutableStateOf<AdvanceControlElement?>(null) }

    Scaffold(
        modifier = Modifier
            .padding(all = 0.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
        snackbarHost = { ToafSnackBarHost(snackBarHostState) },
        containerColor = ToafColors.toafBackground,
        topBar = {
            TopBarModel(
                titleResource = selectedElement?.name ?: Res.string.module_advance_control,
                onBackClicked = {
                    if (selectedElement != null)
                        selectedElement = null
                    else
                        onBackClick()
                })
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(ToafColors.toafBackground)
            ) {
                val element = selectedElement
                if (element == null)
                {
                    advanceControlElements.forEach { item ->
                        ToafButtons.GreenButton(
                            modifier = Modifier.padding(5.dp).fillMaxWidth(),
                            buttonText = item.name,
                            onClick = { selectedElement = item }
                        )
                    }
                }
                else
                {
                    element.content(userInfo) { result, onSuccess, onFail ->
                        showResult(
                            scope = scope,
                            snackBarHostState = snackBarHostState,
                            result = result,
                            onSuccess = onSuccess,
                            onFail = onFail
                        )
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0x0FF00000)
@Composable
fun ActivityAdvanceControlModulePreview()
{
    ActivityAdvanceControlContent(ModuleLWS(null).getAdvanceControlElements(), UserInfo()) {}
}