package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pl.misek.toafmobile.activities.ToafConstants
import pl.misek.toafmobile.viewModels.UserInfoViewModel
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.TopBarModelMainActivity
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogChangeUserPassword
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogCreateUser
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogLoginUser
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogQuestion
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogSelectCarType
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogSerialPort
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogShowInterfaceInfo
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogShowUserInfo
import pl.misek.toafmobile.activities.composeModels.menus.MenuMain
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.snackbar.ToafSnackBar
import pl.misek.toafmobile.activities.snackbar.ToafSnackBarHost
import pl.misek.toafmobile.tools.getPlatformActivity
import pl.misek.toafmobile.control.FastControlElement
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.interfaces.ConnectionState
import pl.misek.toafmobile.interfaces.ISerialDevice
import pl.misek.toafmobile.interfaces.IUsbEventManager
import pl.misek.toafmobile.interfaces.ToafWifiEventManager
import pl.misek.toafmobile.interfaces.ToafWifiManager
import pl.misek.toafmobile.interfaces.UsbDeviceInfo
import pl.misek.toafmobile.interfaces.UsbEvent
import pl.misek.toafmobile.interfaces.WifiEvent
import pl.misek.toafmobile.interfaces.wifi.ToafWifiDeviceInfo
import pl.misek.toafmobile.interfaces.wifi.ToafWifiInterface
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.network.ServerConnectionState
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.protocols.ProtocolFactory
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

private enum class ShowInterfaceTypeInformation
{
    None,
    Usb,
    Wifi
}
private enum class DialogType
{
    None,
    WifiConfig,
    WifiConfigDevice,
    WifiAddDevice,
    UsbConfig
}
@Composable
fun ActivityMainModel(
    onClickDiagnosticButton : () ->Unit,
    onDeviceChanged: suspend () -> Unit,
    updateInterface : Int
) {
    val scope = rememberCoroutineScope()
    val activity = getPlatformActivity()
    val database : Database = koinInject()
    val toafApi : ToafApi = koinInject()
    val toafWifiManager : ToafWifiManager = koinInject()
    val modulesManager : ModulesManager = koinInject()
    val currentModules = modulesManager.getCurrentModules()
    val serialDevice : ISerialDevice = koinInject()

    val userInfoViewModel : UserInfoViewModel = koinViewModel()
    val userInfo by userInfoViewModel.userInfo.collectAsState()

    val snackBarHostName = remember { SnackbarHostState() }
    var showInterfaceInformation by remember { mutableStateOf(ShowInterfaceTypeInformation.None) }
    var dialogType by remember { mutableStateOf(DialogType.None) }

    var wifiDeviceToAdd by remember { mutableStateOf<ToafWifiDeviceInfo?>(null) }
    var wifiDeviceToConfig by remember { mutableStateOf<ToafWifiDeviceInfo?>(null) }

    var showUserInfoDialog by remember { mutableStateOf(false) }
    var showUserDeleteQuestionDialog by remember { mutableStateOf(false) }
    var showSelectCarTypeDialog by remember { mutableStateOf(false) }
    var showUserLoginDialog by remember { mutableStateOf(false) }
    var showCreateUserDialog by remember { mutableStateOf(false) }
    var showChangeUserPasswordDialog by remember { mutableStateOf(false) }
    var currentCarTypeIndex by remember { mutableIntStateOf(0) }

    var usbDeviceInfo by remember { mutableStateOf<UsbDeviceInfo?>(null) }
    var wifiDeviceInfo by remember { mutableStateOf<ToafWifiDeviceInfo?>(null) }
    val usbEventManager : IUsbEventManager = koinInject()

    val loginCorrect = stringResource(Res.string.dialog_user_login_login_correct)
    val loginIncorrect = stringResource(Res.string.dialog_user_login_error)
    val createUserLoginTooShort = stringResource(Res.string.dialog_user_create_login_to_short)
    val createUserEmailIsWrong = stringResource(Res.string.dialog_user_create_email_is_wrong)
    val createUserPasswordIsTooShort = stringResource(Res.string.dialog_user_password_to_short)
    val createUserPasswordIsMismatch = stringResource(Res.string.dialog_user_create_password_mismatch)
    val createUserSuccess = stringResource(Res.string.dialog_user_create_user_created)
    val createUserError = stringResource(Res.string.dialog_user_create_create_error)
    val userDeleteSuccess = stringResource(Res.string.dialog_user_info_delete_user_successful)
    val userDeleteError = stringResource(Res.string.dialog_user_info_delete_user_error)
    val userChangePasswordSuccess = stringResource(Res.string.dialog_user_password_change_ok)
    val userChangePasswordError = stringResource(Res.string.dialog_user_password_change_error)

    val fastControlResultSuccess = stringResource(Res.string.activity_control_control_correct)
    val fastControlResultError = stringResource(Res.string.activity_control_control_error)
    val fastControlElements = remember {mutableStateListOf<FastControlElement>() }

    LaunchedEffect(currentCarTypeIndex, updateInterface)
    {
        currentCarTypeIndex = withContext(Dispatchers.IO) {
            database.getIntSettingValueByName(DatabaseSettingFields.TOAF_CURRENT_CAR_TYPE)}
        withContext(Dispatchers.IO)
        {
            fastControlElements.clear()

            val elements = database.selectAllFastControlElements()
            elements.let{
                for (element in elements)
                {
                    //element.controlModule = currentModules.firstOrNull { it.getModuleName() == element.moduleName }
                    fastControlElements.add(element)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { ToafSnackBarHost(hostState = snackBarHostName) },
        modifier = Modifier
            .padding(all = 0.dp)
            .fillMaxWidth()
            .fillMaxHeight(),
        containerColor = ToafColors.toafBackground,
        topBar = {
            TopBarModelMainActivity(
                menuContent = {
                    MenuMain.Menu(
                        onShowSelectCarType = {showSelectCarTypeDialog = !showSelectCarTypeDialog},
                        onShowUserLogin = {showUserLoginDialog = !showUserLoginDialog},
                        onShowWifiInterfaceConfig =  {dialogType = DialogType.WifiConfig},
                        onShowUsbInterfaceConfig = {dialogType = DialogType.UsbConfig}
                    )
                },
                onClickShowInterfaceInfo = { usb, wifi ->
                    if (usb != null) {
                        usbDeviceInfo = usb
                        showInterfaceInformation = ShowInterfaceTypeInformation.Usb
                    }
                    else if (wifiDeviceInfo != null)
                    {
                        wifiDeviceInfo = wifi
                        showInterfaceInformation = ShowInterfaceTypeInformation.Wifi
                    }

                },
                onShowUserInfo = { showUserInfoDialog = !showUserInfoDialog }
            )
        },
        content = { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .background(ToafColors.toafBackground)
            ) {
                ToafButtons.GreenButton(
                    modifier = Modifier
                        .padding(5.dp)
                        .fillMaxWidth(),
                    buttonText = Res.string.activity_main_button_diagnostic,
                    onClick = {onClickDiagnosticButton()}
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(fastControlElements.size){index ->
                        /*
                        ToafButtons.ControlButton(
                            moduleName = fastControlElements[index].moduleName,
                            controlDescriptor = fastControlElements[index].customName,
                            onClick = {
                                scope.launch {
                                    fastControlElements[index].controlModule?.let {
                                        val controlModule = it as IModuleControl
                                        fastControlElements[index].controlElement?.let { element ->
                                            val controlResult = withContext(Dispatchers.IO)
                                            {
                                                controlModule.control(element)
                                            }
                                            ToafSnackBar().showResult(
                                                scope = scope,
                                                snackBarHostState = snackBarHostName,
                                                result = controlResult,
                                                messageSuccess = fastControlResultSuccess,
                                                messageError = fastControlResultError)
                                        }
                                    }
                                }
                            },
                            modifier =  Modifier.fillMaxWidth().aspectRatio(1.5f)
                        )*/
                    }
                }
            }
            if (showInterfaceInformation != ShowInterfaceTypeInformation.None) {
                when (showInterfaceInformation) {
                    ShowInterfaceTypeInformation.Usb -> {
                        DialogShowInterfaceInfo.ShowUsb(
                            usbDevice = usbDeviceInfo,
                            database = database,
                            onClose = {
                                showInterfaceInformation = ShowInterfaceTypeInformation.None
                            })
                    }

                    ShowInterfaceTypeInformation.Wifi -> {
                        DialogShowInterfaceInfo.ShowWifi(
                            wifiDevice = wifiDeviceInfo,
                            onClose = {
                                showInterfaceInformation = ShowInterfaceTypeInformation.None
                            })
                    }

                    else -> {}
                }
            }
            if (showUserInfoDialog)
            {
                DialogShowUserInfo.Show(
                    userInfo = userInfo,
                    onClose = {showUserInfoDialog = false},
                    onDeleteUser = {showUserDeleteQuestionDialog = true})
            }
            if (showUserDeleteQuestionDialog)
            {
                DialogQuestion.Show(
                    question = Res.string.dialog_user_info_delete_user_question,
                    onNoClick = {showUserDeleteQuestionDialog = false},
                    onYesClick = {
                        scope.launch {
                            val userDeleteResult = withContext(Dispatchers.IO) {toafApi.deleteUser()}
                            ToafSnackBar().showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostName,
                                result = userDeleteResult,
                                messageSuccess = userDeleteSuccess,
                                messageError = userDeleteError)
                            if (userDeleteResult) {
                                withContext(Dispatchers.IO) {database.updateSettings("","")}
                                userInfoViewModel.performLoginUser()
                            }
                            showUserDeleteQuestionDialog = false
                        }
                    }
                )
            }
            if (showUserLoginDialog)
            {
                DialogLoginUser.Show(
                   database = database,
                    onClose = { showUserLoginDialog = false},
                    onLoginUserClick = {user,password ->
                        scope.launch {
                            withContext(Dispatchers.IO)
                            {
                                database.updateSettings(user, password)
                                userInfoViewModel.performLoginUser()
                            }
                            ToafSnackBar().showResult(
                                scope = scope,
                                snackBarHostState = snackBarHostName,
                                result = userInfo.isLogged == ServerConnectionState.CONNECTED,
                                messageSuccess = loginCorrect,
                                messageError = loginIncorrect)
                            if (userInfo.isLogged == ServerConnectionState.CONNECTED) {
                                showUserLoginDialog = false
                            }
                        }
                    },
                    onCreateUserClick = {showCreateUserDialog = !showCreateUserDialog},
                    onChangePasswordClick = { showChangeUserPasswordDialog = !showChangeUserPasswordDialog}
                )
            }
            if (showChangeUserPasswordDialog)
            {
                DialogChangeUserPassword.Show(
                    database = database,
                    onClose = {showChangeUserPasswordDialog = false},
                    onChangePasswordClick = {login, oldPassword, newPassword1, newPassword2 ->
                        scope.launch {
                            var canChangePassword = true
                            if (newPassword1.length < 8 || newPassword2.length < 8) {
                                ToafSnackBar().showError(
                                    scope = scope,
                                    snackBarHostState = snackBarHostName,
                                    messageError = createUserPasswordIsTooShort
                                )
                                canChangePassword = false
                            } else if (newPassword1 != newPassword2) {
                                ToafSnackBar().showError(
                                    scope = scope,
                                    snackBarHostState = snackBarHostName,
                                    messageError = createUserPasswordIsMismatch
                                )
                                canChangePassword = false
                            }
                            if (canChangePassword) {
                                val data = buildJsonObject {
                                    put("password", newPassword1)
                                }
                                val changePasswordResult = withContext(Dispatchers.IO){ toafApi.userChangePass(data)}
                                ToafSnackBar().showResult(
                                    scope = scope,
                                    snackBarHostState = snackBarHostName,
                                    result = changePasswordResult,
                                    messageSuccess = userChangePasswordSuccess,
                                    messageError = userChangePasswordError
                                )

                                if (changePasswordResult)
                                {
                                    withContext(Dispatchers.IO) {database.updateSettings(login,newPassword1)}
                                    userInfoViewModel.performLoginUser()
                                }
                                showChangeUserPasswordDialog = !changePasswordResult
                            }
                        }
                    }
                )
            }
            if (showCreateUserDialog)
            {
                DialogCreateUser.Show(
                    onClose = {showCreateUserDialog = false},
                    onCreateUserClick= {newUserLogin, newUserEmail, newUserPassword1, newUserPassword2 ->
                        scope.launch()
                        {
                            var canCreateUser = true
                            if (newUserLogin.length < 5) {
                                ToafSnackBar().showError(
                                    scope = scope,
                                    snackBarHostState = snackBarHostName,
                                    messageError = createUserLoginTooShort
                                )
                                canCreateUser = false
                            } else if (!newUserEmail.isValidEmail()) {
                                ToafSnackBar().showError(
                                    scope = scope,
                                    snackBarHostState = snackBarHostName,
                                    messageError = createUserEmailIsWrong
                                )
                                canCreateUser = false
                            } else if (newUserPassword1.length < 8) {
                                ToafSnackBar().showError(
                                    scope = scope,
                                    snackBarHostState = snackBarHostName,
                                    messageError = createUserPasswordIsTooShort
                                )
                                canCreateUser = false
                            } else if (newUserPassword1 != newUserPassword2) {
                                ToafSnackBar().showError(
                                    scope = scope,
                                    snackBarHostState = snackBarHostName,
                                    messageError = createUserPasswordIsMismatch
                                )
                                canCreateUser = false
                            }
                            if (canCreateUser) {
                                val data = buildJsonObject {
                                    put("login", newUserLogin)
                                    put("email", newUserEmail)
                                    put("password", newUserPassword1)
                                }
                                val addUserResult =
                                    withContext(Dispatchers.IO) { toafApi.addUser(data) }
                                if (addUserResult) {
                                    val result = withContext(Dispatchers.IO)
                                    {
                                        if (database.getLoginAndPassword() == null) {
                                            database.insertSettings(
                                                newUserLogin,
                                                newUserPassword1
                                            ) > 0
                                        } else {
                                            database.updateSettings(
                                                newUserLogin,
                                                newUserPassword1
                                            )
                                        }
                                    }
                                    userInfoViewModel.performLoginUser()
                                    ToafSnackBar().showResult(
                                        scope = scope,
                                        snackBarHostState = snackBarHostName,
                                        result = result,
                                        messageSuccess = String.format(
                                            createUserSuccess,
                                            newUserLogin
                                        ),
                                        messageError = String.format(
                                            createUserError,
                                            newUserLogin
                                        )
                                    )
                                    showCreateUserDialog = !result
                                }
                            }
                        }
                    }
                )
            }
            if (showSelectCarTypeDialog)
            {
                DialogSelectCarType.Show(
                    currentIndex = currentCarTypeIndex,
                    onClose = { showSelectCarTypeDialog = false},
                    onSelect = {index ->
                        scope.launch {
                            withContext(Dispatchers.IO)
                            {
                                database.updateIntSettingByName(DatabaseSettingFields.TOAF_CURRENT_CAR_TYPE, index)
                            }
                        }
                        currentCarTypeIndex = index
                        showSelectCarTypeDialog = false
                    }
                )
            }
            when (dialogType)
            {
                DialogType.UsbConfig ->
                    DialogSerialPort(
                        onCancelClick = { dialogType = DialogType.None},
                        onOkClick = { item ->
                            scope.launch {
                                database.updateStringSettingByName(
                                    DatabaseSettingFields.SERIAL_PORT_NAME,
                                    item
                                )
                                database.updateStringSettingByName(DatabaseSettingFields.LAST_DEVICE_TYPE, "USB")
                                serialDevice.connect(item)
                                dialogType = DialogType.None
                            }
                        }
                    )
                DialogType.WifiConfig ->
                    toafWifiManager.Configuration (
                        onCancelClick = {dialogType = DialogType.None},
                        onAddInterfaceClick = { dialogType = DialogType.WifiAddDevice},
                        onConfigDeviceClick = {item ->
                            wifiDeviceToConfig = item
                            dialogType = DialogType.WifiConfigDevice
                        },
                        onSelectDevice = {item ->
                            showResult(scope, snackBarHostName,true, "Wybrano : ${item.deviceName}", null)
                        },
                        deviceInfo = wifiDeviceToAdd
                    )
                DialogType.WifiConfigDevice -> {
                    wifiDeviceToConfig?.let {
                        toafWifiManager.ConfigDevice(
                            deviceInfo = it,
                            onCloseClick = { dialogType = DialogType.WifiConfig },
                            onShowResult = { result, successText, failText ->
                                showResult(
                                    scope,
                                    snackBarHostName,
                                    result,
                                    successText,
                                    failText
                                )
                            }
                        )
                    }
                }
                DialogType.WifiAddDevice -> {
                    toafWifiManager.AddDevice(
                        onCancelClick = { dialogType = DialogType.WifiConfig },
                        onAddClick ={ item ->
                            wifiDeviceToAdd = item
                            dialogType = DialogType.WifiAddDevice},
                        onShowResult = { result, successText, failText -> showResult(scope,snackBarHostName, result, successText,failText)}
                    )
                }
                else -> {}
            }
        }
    )
}


fun showResult(scope : CoroutineScope, snackBarHostState: SnackbarHostState, result: Boolean, onSuccess: String?, onFail : String?)
{
    if (onSuccess != null && onFail != null)
    {
        ToafSnackBar().showResult(
            scope = scope,
            snackBarHostState = snackBarHostState,
            result = result,
            messageSuccess = onSuccess,
            messageError = onFail
        )
    }
    else if (onFail != null)
    {
        ToafSnackBar().showError(
            scope = scope,
            snackBarHostState = snackBarHostState,
            messageError = onFail
        )
    }
    else if (onSuccess != null)
    {
        ToafSnackBar().showSuccess(
            scope = scope,
            snackBarHostState = snackBarHostState,
            messageSuccess = onSuccess
        )
    }
}

fun String.isValidEmail(): Boolean {
    val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$".toRegex()
    return this.matches(emailRegex)
}

fun String.isValidIpAddress() : Boolean{
    val ipAddressRegex ="^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$".toRegex()
    return this.matches(ipAddressRegex)
}

@Preview(showBackground = true, backgroundColor = 0x0FF00000)
@Composable
fun ActivityMainModelPreview()
{
    ActivityMainModel(
        onClickDiagnosticButton = {},
        onDeviceChanged = {},
        updateInterface = 0
    )
}