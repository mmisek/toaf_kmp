package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.interfaces.UsbDeviceInfo
import pl.misek.toafmobile.interfaces.wifi.ToafWifiDeviceInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

object DialogShowInterfaceInfo {
    @Composable
    fun ShowUsb(usbDevice : UsbDeviceInfo?, database : Database?, onClose : () -> Unit)
    {
        val scope = rememberCoroutineScope()
        var fastInitSleepValue by remember {mutableIntStateOf(25)}
        fastInitSleepValue = runBlocking { database?.getIntSettingValueByName(DatabaseSettingFields.TOAF_KWP_FAST_INIT_SLEEP) ?: 25}
        AlertDialog(
            shape = toafShape,
            modifier = Modifier.border(
                width = 1.dp,
                shape = toafShape,
                color = ToafColors.toafTextColorWhite
            ),

            containerColor = ToafColors.toafBackground,
            onDismissRequest = {onClose()},
            title = {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp))
                {
                    Text(
                        text = stringResource(Res.string.dialog_interface_info_title),
                        fontSize = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        textAlign = TextAlign.Center,
                        color = Color.White,
                    )
                }
            },
            text = {
                Column(modifier = Modifier.padding(5.dp)) {
                    InfoRow(
                        labelRes = Res.string.dialog_interface_info_path,
                        value = usbDevice?.portName?.trim { it <= ' ' })
                    InfoRow(
                        labelRes = Res.string.dialog_interface_info_manufacturer,
                        value = usbDevice?.manufacturer?.trim { it <= ' ' })
                    InfoRow(
                        labelRes = Res.string.dialog_interface_info_name,
                        value = usbDevice?.name?.trim { it <= ' ' })
                    InfoRow(
                        labelRes = Res.string.dialog_interface_info_version,
                        value = usbDevice?.version?.trim { it <= ' ' })
                    InfoRow(
                        labelRes = Res.string.dialog_interface_info_serial_number,
                        value = usbDevice?.serialNumber?.trim { it <= ' ' })
                    InputRow(
                        labelRes = Res.string.dialog_interface_info_fast_init_sleep,
                        value = fastInitSleepValue.toString(),
                        onValueChange = { newValue ->
                            val newFastInitSleepValue =  newValue.toIntOrNull()
                            if (newFastInitSleepValue != null)
                            {
                                scope.launch {
                                    withContext(Dispatchers.IO)
                                    {
                                        database?.updateIntSettingByName(
                                            name = DatabaseSettingFields.TOAF_KWP_FAST_INIT_SLEEP,
                                            value = newFastInitSleepValue
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            },
            confirmButton = {
                ToafButtons.GreenButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.alert_module_info_close,
                    onClick = {onClose()})
            }
        )
    }

    @Composable
    fun ShowWifi(wifiDevice : ToafWifiDeviceInfo?, onClose : () -> Unit)
    {
        var deviceInfo by remember { mutableStateOf<ToafWifiDeviceInfo?>(ToafWifiDeviceInfo()) }
        deviceInfo = wifiDevice

        AlertDialog(
            modifier = Modifier.border(
                width = 1.dp,
                color = ToafColors.toafTextColorWhite,
            ),
            containerColor = ToafColors.toafBackground,
            onDismissRequest = {onClose()},
            title = {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp))
                {
                    Text(
                        text = stringResource(Res.string.dialog_interface_info_title),
                        fontSize = 20.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(modifier = Modifier.padding(5.dp)) {
                    InfoRow(
                        labelRes = Res.string.dialog_wifi_interface_name,
                        value = deviceInfo?.deviceName?.trim { it <= ' ' })
                    InfoRow(
                        labelRes = Res.string.dialog_wifi_interface_serial_number,
                        value = deviceInfo?.serialNumber?.trim { it <= ' ' })
                    InfoRow(
                        labelRes = Res.string.dialog_wifi_interface_hardware,
                        value = deviceInfo?.hardwareVersion?.trim { it <= ' ' })
                    InfoRow(
                        labelRes = Res.string.dialog_wifi_interface_software,
                        value = deviceInfo?.softwareVersion?.trim { it <= ' ' })
                }
            },
            confirmButton = {
                ToafButtons.GreenButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.alert_module_info_close,
                    onClick = {onClose()})
            }
        )
    }
}
@Composable
fun InfoRow(labelRes: StringResource, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle( style = SpanStyle(fontWeight = FontWeight.Bold))
                {
                    append(stringResource(labelRes))
                    append(" : ")
                }
                append(value ?: "")
            },
            modifier = Modifier.fillMaxWidth(),
            color = Color.White
        )
    }
}

@Composable
fun InputRow(labelRes: StringResource, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(labelRes),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(0.5f),
            color = Color.White
        )

        OutlinedTextField(
            value = value,
            onValueChange = { newValue ->
                if (newValue.length <= 2 && newValue.all { it.isDigit() }) {
                    onValueChange(newValue)
                }
            },
            modifier = Modifier
                .weight(0.5f)
                .padding(start = 10.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                unfocusedBorderColor = Color.White,
                focusedBorderColor = Color.White,
                unfocusedLabelColor = Color.White,
                disabledBorderColor = Color.White
            )
        )
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun DialogShowInterfaceInfoPreview()
{
}