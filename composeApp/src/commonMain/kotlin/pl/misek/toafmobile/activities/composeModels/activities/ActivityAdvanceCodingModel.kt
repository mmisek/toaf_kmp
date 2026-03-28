package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pl.misek.toafmobile.modules.interfaces.IModule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pl.misek.toafmobile.activities.composeModels.TopBarModel
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.snackbar.ToafSnackBarHost
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModuleBcuKeys
import pl.misek.toafmobile.modules.interfaces.IModuleEKA
import pl.misek.toafmobile.modules.interfaces.IModuleLSMAutoLight
import pl.misek.toafmobile.modules.interfaces.IModuleOdometer
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.viewModels.UserInfoViewModel
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.activity_advance_coding

@Composable
fun ActivityModuleAdvanceCodingModel(moduleKoinName : KoinNames, onBackClick : () ->Unit) {
    val moduleKoinName = moduleKoinName
    val modulesManage : ModulesManager = koinInject()
    val currentModule = modulesManage.getCurrentModules().firstOrNull {it.getKoinName() == moduleKoinName} as IModule
    val userInfoViewModel : UserInfoViewModel = koinViewModel()
    val userInfo by userInfoViewModel.userInfo.collectAsState()
    val scope = rememberCoroutineScope()
    val snackBarHostState = remember { SnackbarHostState() }
    var showReadPrivateBackupDialog by remember { mutableStateOf(false) }
    var showReadPublicBackupDialog by remember { mutableStateOf(false) }
    var readCurrentCoding by remember { mutableStateOf(true) }

    LaunchedEffect(readCurrentCoding, showReadPrivateBackupDialog, showReadPublicBackupDialog) {

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
                titleResource = Res.string.activity_advance_coding,
                onBackClicked = { onBackClick() })
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
            {
                if (currentModule is IModuleVIN) {
                    AdvanceCodingVin(
                        koinModuleName = moduleKoinName,
                        userInfo =  userInfo,
                        onShowResult = { result, onSuccess, onFail ->
                            showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostState,
                                result = result,
                                onSuccess = onSuccess,
                                onFail = onFail
                            )
                        })
                }
                if (currentModule is IModuleOdometer) {
                    AdvanceCodingOdometer(
                        koinModuleName = moduleKoinName,
                        userInfo =  userInfo,
                        onShowResult = { result, onSuccess, onFail ->
                            showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostState,
                                result = result,
                                onSuccess = onSuccess,
                                onFail = onFail
                            )
                        }
                    )
                }
                if (currentModule is IModuleBcuKeys)
                {
                    AdvanceCodingBcuKeys(
                        koinModuleName = moduleKoinName,
                        userInfo =  userInfo,
                        onShowResult = { result, onSuccess, onFail ->
                            showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostState,
                                result = result,
                                onSuccess = onSuccess,
                                onFail = onFail
                            )
                        }
                    )
                }
                if (currentModule is IModuleLSMAutoLight)
                {
                    AdvanceCodingAutoLights(
                        koinModuleName = moduleKoinName,
                        onShowResult = { result, onSuccess, onFail ->
                            showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostState,
                                result = result,
                                onSuccess = onSuccess,
                                onFail = onFail
                            )
                        }
                    )
                }
                if (currentModule is IModuleEKA)
                {
                    AdvanceCodingEka(
                        koinModuleName = moduleKoinName,
                        onShowResult = { result, onSuccess, onFail ->
                            showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostState,
                                result = result,
                                onSuccess = onSuccess,
                                onFail = onFail
                            )
                        }
                    )
                }
            }
        }
    )
}


@Preview(showBackground = true, backgroundColor = 0x0FF00000)
@Composable
fun ActivityModuleAdvanceCodingModelPreview()
{
    ActivityModuleAdvanceCodingModel(
        moduleKoinName = KoinNames.ModuleR40Lsm,
        onBackClick = {})
}