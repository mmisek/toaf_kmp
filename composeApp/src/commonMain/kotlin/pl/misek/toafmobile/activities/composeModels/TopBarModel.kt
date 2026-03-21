package pl.misek.toafmobile.activities.composeModels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import io.sentry.Sentry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import pl.misek.toafmobile.BuildKonfig
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.interfaces.ConnectionState
import pl.misek.toafmobile.interfaces.ISerialDevice
import pl.misek.toafmobile.interfaces.IUsbEventManager
import pl.misek.toafmobile.interfaces.ToafWifiEventManager
import pl.misek.toafmobile.interfaces.UsbDeviceInfo
import pl.misek.toafmobile.interfaces.UsbEvent
import pl.misek.toafmobile.interfaces.WifiEvent
import pl.misek.toafmobile.interfaces.wifi.ToafWifiDeviceInfo
import pl.misek.toafmobile.interfaces.wifi.ToafWifiInterface
import pl.misek.toafmobile.network.ServerConnectionState
import pl.misek.toafmobile.protocols.ProtocolFactory
import pl.misek.toafmobile.viewModels.UserInfoViewModel
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarModel(titleResource : StringResource, subTitle : StringResource? = null, subTitleText : String? = null, onBackClicked: () ->Unit, showBackIcon : Boolean = true)
{
    TopAppBar(
        windowInsets = WindowInsets.statusBars,
        title =
            {
                Column(modifier = Modifier.fillMaxWidth())
                {
                    Text(
                        text = stringResource(titleResource),
                        color = ToafColors.toafTextColorWhite,
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (subTitle != null|| subTitleText != null) {
                        Text(
                            text = if (subTitle != null) stringResource(subTitle) else (subTitleText ?: ""),
                            color = ToafColors.toafTextColorWhite,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
        navigationIcon = {
            if (!showBackIcon) null else {
                IconButton(onClick = onBackClicked) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.dialog_question_yes)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ToafColors.toafBackground,
            titleContentColor = ToafColors.greenButton,
            navigationIconContentColor = ToafColors.toafTextColorWhite
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarModelMainActivity(
    menuContent :@Composable RowScope.() -> Unit,
    onClickShowInterfaceInfo: (UsbDeviceInfo?,ToafWifiDeviceInfo?) -> Unit,
    onShowUserInfo: () -> Unit)
{
    val database : Database = koinInject()
    val serialDevice : ISerialDevice = koinInject()
    val protocolFactory : ProtocolFactory = koinInject()

    val usbEventViewModel : IUsbEventManager = koinInject()
    val usbEvent by usbEventViewModel.usbState.collectAsState()
    var interfaceConnectionState by remember { mutableStateOf(ConnectionState.USB_DISCONNECTED) }
    val wifiEventManager : ToafWifiEventManager = koinInject()
    val wifiEvent by wifiEventManager.wifiEvent.collectAsState()
    var usbDeviceInfo by remember { mutableStateOf<UsbDeviceInfo?>(null) }
    var wifiDeviceInfo by remember { mutableStateOf<ToafWifiDeviceInfo?>(null) }

    val userInfoViewModel : UserInfoViewModel = koinViewModel()
    val userInfo by userInfoViewModel.userInfo.collectAsState()
    var serveConnectionState by remember { mutableStateOf(ServerConnectionState.DISCONNECTED) }

    LaunchedEffect(Unit)
    {
        val lastDeviceType = database.getStringSettingValueByName(DatabaseSettingFields.LAST_DEVICE_TYPE)
        if (lastDeviceType == "WIFI")
        {
            val ip = database.getStringSettingValueByName(DatabaseSettingFields.WIFI_DEVICE_IP)
            wifiEventManager.startMonitoring(ip ?: "")
        }
    }
    LaunchedEffect(userInfo)
    {
        serveConnectionState = userInfo.isLogged
    }
    LaunchedEffect(usbEvent) {
        when (usbEvent) {
            is UsbEvent.Attached -> {
                try {
                    serialDevice.connect("COM1")
                    if (serialDevice.isConnected) {
                        wifiDeviceInfo = null
                        usbDeviceInfo = (usbEvent as UsbEvent.Attached).device
                        protocolFactory.updateProtocolFactory(serialDevice)
                        interfaceConnectionState = ConnectionState.USB_CONNECTED
                    }
                } catch (ex: Exception) {
                    Sentry.captureException(ex)
                    usbDeviceInfo = null
                    protocolFactory.updateProtocolFactory(null)
                    interfaceConnectionState = ConnectionState.USB_DISCONNECTED
                }
            }

            is UsbEvent.Detached -> {
                usbDeviceInfo = null
                interfaceConnectionState = ConnectionState.USB_DISCONNECTED
            }

            else -> {}
        }
    }
    LaunchedEffect(wifiEvent)
    {
        when (wifiEvent) {
            is WifiEvent.Connected -> {
                usbDeviceInfo = null
                wifiDeviceInfo = (wifiEvent as WifiEvent.Connected).wifiDevice
                val twi =
                    ToafWifiInterface((wifiEvent as WifiEvent.Connected).wifiDevice.ipAddress ?: "")
                protocolFactory.updateProtocolFactory(twi)
                interfaceConnectionState = ConnectionState.WIFI_CONNECTED
            }

            is WifiEvent.Disconnected -> {
                wifiDeviceInfo = null
                interfaceConnectionState = ConnectionState.WIFI_DISCONNECTED
            }

            else -> {}
        }
    }
    TopAppBar(
        windowInsets = WindowInsets.statusBars,
        title =
            {
                Column(modifier = Modifier.fillMaxWidth())
                {
                    Text(
                        text = stringResource(Res.string.app_name),
                        color = ToafColors.toafTextColorWhite,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = BuildKonfig.VERSION_NAME,
                        color = ToafColors.toafTextColorWhite,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
        navigationIcon = {},
        actions = {
            IconButton(onClick = { onClickShowInterfaceInfo(usbDeviceInfo, wifiDeviceInfo) }) {
                Icon(
                    painter = painterResource(when(interfaceConnectionState)
                        {
                            ConnectionState.USB_CONNECTED -> Res.drawable.usb_connected
                            ConnectionState.USB_DISCONNECTED -> Res.drawable.usb_disconnected
                            ConnectionState.WIFI_CONNECTED -> Res.drawable.wifi_connected
                            ConnectionState.WIFI_DISCONNECTED -> Res.drawable.wifi_disconnected
                        }
                    ),
                    contentDescription = "",
                    tint = Color.Unspecified
                )
            }
            IconButton(onClick =  { onShowUserInfo() }) {
                Icon(
                    painter = painterResource(when (serveConnectionState)
                        {
                            ServerConnectionState.CONNECTED -> Res.drawable.toaf_login_correct
                            ServerConnectionState.DISCONNECTED -> Res.drawable.toaf_login_failed
                        }
                    ),
                    contentDescription = "",
                    tint = Color.Unspecified
                )
            }
            menuContent()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ToafColors.toafBackground,
            titleContentColor = ToafColors.toafTextColorWhite,
            navigationIconContentColor = ToafColors.toafTextColorWhite
        )
    )
}
@Preview(showBackground = true, backgroundColor = 0xFF0000)
@Composable
fun TopBarModelPreview()
{
    MaterialTheme{
        TopBarModel(
            Res.string.activity_control_dsc_tittle,
            Res.string.activity_diagnostic_title,
            onBackClicked = {})
    }
}
