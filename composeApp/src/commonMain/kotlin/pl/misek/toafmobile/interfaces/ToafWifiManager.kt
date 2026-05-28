package pl.misek.toafmobile.interfaces

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.activities.isValidIpAddress
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogToafWifiAccessPointConfiguration
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogToafWifiConfiguration
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogToafWifiInterfaceAddDevice
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.interfaces.wifi.ToafWifiDeviceInfo
import pl.misek.toafmobile.interfaces.wifi.ToafWifiInterface
import pl.misek.toafmobile.network.IWifiManager
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

class ToafWifiManager {
    private val _wifiState = MutableStateFlow<WifiEvent>(WifiEvent.Idle)
    val wifiState = _wifiState.asStateFlow()

    @Composable
    fun Configuration(
        onCancelClick: () -> Unit,
        onAddInterfaceClick: () -> Unit,
        onConfigDeviceClick: (ToafWifiDeviceInfo) -> Unit,
        onSelectDevice: (ToafWifiDeviceInfo) -> Unit,
        deviceInfo: ToafWifiDeviceInfo? = null
    ) {
        val database: Database = koinInject()
        val scope = rememberCoroutineScope()
        val wifiManager : IWifiManager = koinInject()
        var devicesList by remember { mutableStateOf<List<ToafWifiDeviceInfo>?>(null) }
        var scanningInProgress by remember { mutableStateOf(false) }
        if (deviceInfo != null)
            devicesList = devicesList.orEmpty() +deviceInfo
        DialogToafWifiConfiguration(
            devicesList = devicesList,
            onCancelClick = { onCancelClick() },
            onFindDevicesClick = {
                scanningInProgress = true
                devicesList = emptyList()
                scope.launch {
                    withContext(Dispatchers.IO)
                    {
                        val ipList = wifiManager.scanNetwork()
                        for (ip in ipList) {
                            Logger.i{"Scan $ip to ToafWifiInterface"}
                            val twi = ToafWifiInterface(ipAddress = ip)
                            val info = twi.getDeviceInfo()
                            info?.let {
                                withContext(Dispatchers.Main){
                                    devicesList = devicesList.orEmpty() + it
                                }
                            }
                        }
                    }
                    scanningInProgress = false
                }
            },
            onAddDeviceClick = { onAddInterfaceClick() },
            onSelectDeviceClick = { item ->
                item?.let {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            database.updateStringSettingByName(
                                DatabaseSettingFields.WIFI_DEVICE_IP,
                                it.ipAddress
                            )
                            database.updateStringSettingByName(
                                DatabaseSettingFields.LAST_DEVICE_TYPE,
                                "WIFI"
                            )
                            val twi = ToafWifiInterface(it.ipAddress ?: "")
                            val deviceInfo = twi.getDeviceInfo()
                            if (deviceInfo != null)
                                _wifiState.value = WifiEvent.Selected(deviceInfo)
                            else
                                _wifiState.value = WifiEvent.Error(it)
                            onSelectDevice(it)
                        }
                    }
                }
            },
            onConfigWifiDeviceClick = { item -> item?.let { onConfigDeviceClick(item) } },
            inProgress = scanningInProgress
        )
    }

    @Composable
    fun AddDevice(
        onCancelClick: () -> Unit,
        onAddClick: (ToafWifiDeviceInfo?) -> Unit,
        onShowResult: (result: Boolean, onSuccessText: String?, onFailText: String?) -> Unit
    ) {
        val scope = rememberCoroutineScope()
        val database: Database = koinInject()
        var ipAddress by remember { mutableStateOf("") }
        val ipAddressIsNotValid =
            stringResource(Res.string.alert_dialog_wifi_configuration_ip_address_is_not_valid)
        val wifiDeviceNotFound =
            stringResource(Res.string.alert_dialog_wifi_configuration_message_devices_not_found)
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                ipAddress =
                    database.getStringSettingValueByName(DatabaseSettingFields.WIFI_DEVICE_IP) ?: ""
            }
        }
        DialogToafWifiInterfaceAddDevice(
            ipAddress = ipAddress,
            onCancelClick = { onCancelClick() },
            onAddClick = { newIp ->
                if (newIp.isValidIpAddress()) {
                    scope.launch {
                        val twi = ToafWifiInterface(newIp)
                        val deviceInfo =
                            withContext(Dispatchers.IO) { twi.getDeviceInfo() }
                        if (deviceInfo != null) {
                            val dev = ToafWifiDeviceInfo(
                                deviceName = deviceInfo.deviceName,
                                serialNumber = deviceInfo.serialNumber,
                                hardwareVersion = deviceInfo.hardwareVersion,
                                softwareVersion = deviceInfo.softwareVersion,
                                ipAddress = newIp
                            )
                            onAddClick(dev)
                        } else {
                            onShowResult(false, null, wifiDeviceNotFound)
                            onAddClick(null)
                        }
                    }
                } else {
                    onShowResult(false, null, ipAddressIsNotValid)
                }
            }
        )
    }

    @Composable
    fun ConfigDevice(
        deviceInfo: ToafWifiDeviceInfo,
        onCloseClick: () -> Unit,
        onShowResult: (result: Boolean, onSuccessText: String?, onFailText: String?) -> Unit
    ) {
        var twi by remember { mutableStateOf<ToafWifiInterface?>(null) }
        twi = ToafWifiInterface(deviceInfo.ipAddress ?: "")
        val readAccessPointConfigurationFailed =
            stringResource(Res.string.alert_dialog_wifi_configuration_ap_password_message_read_not_ok)
        val saveAccessPointConfigurationSuccess =
            stringResource(Res.string.alert_dialog_wifi_configuration_ap_password_message_save_ok)
        val saveAccessPointConfigurationFailed =
            stringResource(Res.string.alert_dialog_wifi_configuration_ap_password_message_save_not_ok)
        DialogToafWifiAccessPointConfiguration(
            twi = twi,
            onClose = { onCloseClick() },
            onReadOperationResult = { result ->
                if (!result)
                    onShowResult(false, null, readAccessPointConfigurationFailed)
            },
            onSaveOperationResult = { result ->
                onShowResult(
                    result,
                    saveAccessPointConfigurationSuccess,
                    saveAccessPointConfigurationFailed
                )
            })
    }
}