package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import pl.misek.toafmobile.activities.composeModels.menus.ToafVerticalScrollbar
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.interfaces.ConnectionState
import pl.misek.toafmobile.interfaces.ToafWifiEventManager
import pl.misek.toafmobile.interfaces.UsbEvent
import pl.misek.toafmobile.interfaces.WifiEvent
import pl.misek.toafmobile.interfaces.wifi.ToafWifiDeviceInfo
import pl.misek.toafmobile.interfaces.wifi.ToafWifiInterface
import pl.misek.toafmobile.interfaces.wifi.ToafWifiUpdateInfo
import pl.misek.toafmobile.protocols.ProtocolFactory
import pl.misek.toafmobile.tools.JsonSupport
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DialogToafWifiConfiguration(
    devicesList : List<ToafWifiDeviceInfo>?,
    onCancelClick: () -> Unit,
    onFindDevicesClick: () -> Unit,
    onAddDeviceClick: () ->Unit,
    onConfigWifiDeviceClick: (selectedItem : ToafWifiDeviceInfo?) -> Unit,
    onSelectDeviceClick: (selectedItem : ToafWifiDeviceInfo?) -> Unit,
    onUpdateDeviceClick: (selectedItem : ToafWifiDeviceInfo?) -> Unit,
    scanInProgress : Boolean,
    updateInProgress : Boolean
) {

    var selectedSerialNumber by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val wifiEventManager : ToafWifiEventManager = koinInject()
    val wifiEvent = wifiEventManager.wifiEvent.collectAsState()
    val protocolFactory : ProtocolFactory = koinInject()
    var updateInfo by remember { mutableStateOf(ToafWifiUpdateInfo("0.0",emptyList())) }
    LaunchedEffect(devicesList?.size)
    {
        updateInfo = JsonSupport.getFirmwareUpdateVersion()
    }
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
                .padding(1.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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
                if (scanInProgress || updateInProgress)
                {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    )
                    {
                        Column (
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = ToafColors.toafTextColorWhite
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (scanInProgress) stringResource(Res.string.alert_dialog_wifi_configuration_message_searching) else stringResource(Res.string.alert_dialog_wifi_configuration_update_in_progress),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                }
                else if (devicesList?.isNotEmpty() == true){
                    val listState = rememberLazyListState()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp)
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
                                    */
                                    if (compareFirmwareVersion(devicesList[index].softwareVersion ?: "100",  updateInfo.version)) {
                                        ToafButtons.GreenButton(
                                            buttonText = Res.string.alert_dialog_wifi_configuration_check_update,
                                            onClick = {onUpdateDeviceClick(devicesList[index])},
                                            modifier = Modifier.fillMaxWidth().padding(2.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    Modifier,
                                    DividerDefaults.Thickness,
                                    DividerDefaults.color
                                )
                            }
                        }
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            ToafVerticalScrollbar(listState = listState)
                        }
                    }
                }
                else if (devicesList?.size == 0)
                {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    )
                    {
                        Text(
                            text = stringResource(Res.string.alert_dialog_wifi_configuration_not_found),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
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
                                onFindDevicesClick()
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

fun compareFirmwareVersion(oldVersion: String, newVersion : String) : Boolean
{
    var result = false
    try {
        val new = newVersion.toFloat()
        val old = oldVersion.toFloat()
        result = new > old
    }
    catch (_ : Exception) {
    }
    return  result
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
        onSelectDeviceClick = {_ ->},
        onUpdateDeviceClick = {_ ->},
        scanInProgress = true,
        updateInProgress = false
    )
}