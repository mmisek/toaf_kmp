package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.composeModels.ToafComposeElements
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.interfaces.ConnectionState
import pl.misek.toafmobile.interfaces.ToafWifiEventManager
import pl.misek.toafmobile.interfaces.UsbEvent
import pl.misek.toafmobile.interfaces.WifiEvent
import pl.misek.toafmobile.interfaces.wifi.ToafWifiDeviceInfo
import pl.misek.toafmobile.interfaces.wifi.ToafWifiInterface
import pl.misek.toafmobile.protocols.ProtocolFactory
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun DialogToafWifiConfiguration(
    devicesList : MutableList<ToafWifiDeviceInfo>,
    onCancelClick: () -> Unit,
    onFindDevicesClick: suspend () -> Unit,
    onAddDeviceClick: () ->Unit,
    onConfigWifiDeviceClick: (selectedItem : ToafWifiDeviceInfo?) -> Unit,
    onSelectDeviceClick: (selectedItem : ToafWifiDeviceInfo?) -> Unit
) {

    var selectedSerialNumber by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val wifiEventManager : ToafWifiEventManager = koinInject()
    val wifiEvent = wifiEventManager.wifiEvent.collectAsState()
    val protocolFactory : ProtocolFactory = koinInject()

    LaunchedEffect(wifiEvent.value) {
        when (val wifi = wifiEvent.value) {
            is WifiEvent.Connected -> {
                //wifiDeviceInfo = wifi.wifiDevice
                val twi = ToafWifiInterface(wifi.wifiDevice.ipAddress ?: "")
                protocolFactory.updateProtocolFactory(twi)
                //connectionState = ConnectionState.WIFI_CONNECTED
            }

            is WifiEvent.Disconnected -> {
                //wifiDeviceInfo = null
                //connectionState = ConnectionState.WIFI_DISCONNECTED
            }

            else -> {}
        }

    }
    AlertDialog(
        shape = toafShape,
        modifier = Modifier.border(
            width = 1.dp,
            shape = toafShape,
            color = ToafColors.toafTextColorWhite
        ),

        containerColor = ToafColors.toafBackground,
        onDismissRequest = {onCancelClick()},
        title = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp)) {
                Text(
                    text = stringResource(Res.string.alert_dialog_wifi_configuration_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .padding(2.dp)
            ) {
                if (isBusy)
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 3.dp,
                        color = ToafColors.toafTextColorWhite)
                }
                else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        items(devicesList.size) { index ->
                            WifiInterfaceItem(
                                selected = devicesList[index].deviceName == selectedSerialNumber,
                                item = devicesList[index],
                                onSelect = { id -> selectedSerialNumber = id })
                            Column()
                            {
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                )
                                {
                                    ToafButtons.GreenButton(
                                        buttonText = Res.string.alert_dialog_wifi_configuration_button_config,
                                        onClick = { onConfigWifiDeviceClick(devicesList[index]) },
                                        modifier =  Modifier.fillMaxWidth().weight(0.5f).padding(2.dp)
                                    )
                                    ToafButtons.GreenButton(
                                        buttonText = Res.string.alert_dialog_wifi_configuration_button_select,
                                        onClick = { onSelectDeviceClick(devicesList[index]) },
                                        modifier =  Modifier.fillMaxWidth().weight(0.5f).padding(2.dp)
                                    )
                                }
                                /*
                                ToafButtons.RedButton(
                                    buttonText = R.string.alert_dialog_wifi_configuration_button_debug,
                                    onClick = {},
                                    modifier =  Modifier.fillMaxWidth().padding(2.dp)
                                )
                                ToafButtons.GreenButton(
                                    buttonText = R.string.alert_dialog_wifi_configuration_check_update,
                                    onClick = {},
                                    modifier =  Modifier.fillMaxWidth().padding(2.dp)
                                )
                                */
                            }
                            HorizontalDivider(
                                Modifier,
                                DividerDefaults.Thickness,
                                DividerDefaults.color
                            )
                        }
                    }
                }
            }
        } ,
        confirmButton = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                ) {
                    ToafButtons.GreenButton(
                        buttonText = Res.string.alert_dialog_wifi_configuration_button_find_devices,
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                isBusy = true
                                onFindDevicesClick()
                                isBusy = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                    )
                    ToafButtons.GreenButton(
                        buttonText = Res.string.alert_dialog_wifi_configuration_button_add_device,
                        onClick = { onAddDeviceClick()  },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                    )
                    ToafButtons.RedButton(
                        buttonText = Res.string.alert_dialog_wifi_configuration_button_close,
                        onClick = { onCancelClick() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                    )
                }
            }
    )
}

@Composable
fun WifiInterfaceItem(
    selected: Boolean,
    item: ToafWifiDeviceInfo?,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = if (selected) Color.Green else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            )
            .clickable { onSelect(item?.serialNumber ?: "") }
            .padding(10.dp),

        ) {
        InfoRow(
            labelRes = Res.string.alert_dialog_wifi_configuration_device_name,
            value = item?.deviceName ?: ""
        )
        InfoRow(
            labelRes = Res.string.alert_dialog_wifi_configuration_device_serial_number,
            value = item?.serialNumber ?: ""
        )
        InfoRow(
            labelRes = Res.string.alert_dialog_wifi_configuration_device_hardware,
            value = item?.hardwareVersion ?: ""
        )
        InfoRow(
            labelRes = Res.string.alert_dialog_wifi_configuration_device_software,
            value = item?.softwareVersion ?: ""
        )
        InfoRow(
            labelRes = Res.string.alert_dialog_wifi_configuration_device_ip,
            value = item?.ipAddress ?: ""
        )

    }
}

@Preview(showBackground = true)
@Composable
fun DialogToafWifiConfigurationPreview() {
    DialogToafWifiConfiguration(
        devicesList = mutableListOf(
            ToafWifiDeviceInfo("ToafWifi", "AA00","0.1","0.2"),
            ToafWifiDeviceInfo("ToafWifi", "AA10","0.5","0.3A"),
        ),
        onCancelClick = {},
        onAddDeviceClick = {},
        onFindDevicesClick = {},
        onConfigWifiDeviceClick = {},
        onSelectDeviceClick = {_ ->}
    )
}