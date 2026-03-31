package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.definitions.ToafColors
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun DialogToafWifiInterfaceAddDevice(ipAddress : String, onCancelClick : () -> Unit, onAddClick : (deviceIp : String) -> Unit)
{
    var ip by remember {mutableStateOf("")}
    ip = ipAddress
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
                .padding(1.dp))
            {
                Text(
                    text = stringResource(Res.string.alert_dialog_wifi_configuration_add_device_title),
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
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 5.dp),
                    value = ip,
                    onValueChange = { ip = it },
                    label = {
                        Text(
                            text = stringResource(Res.string.alert_dialog_wifi_configuration_add_device_enter_ip_address),
                            style = TextStyle(color = Color.White, fontSize = 14.sp)
                        )
                    },
                    textStyle = TextStyle(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedLabelColor = Color.White,
                        disabledBorderColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            val focusManager = LocalFocusManager.current
            val keyboardManager = LocalSoftwareKeyboardController.current
            Column {
                ToafButtons.RedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.alert_dialog_wifi_configuration_add_device_cancel,
                    onClick = { onCancelClick() })
                ToafButtons.GreenButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.alert_dialog_wifi_configuration_add_device_add,
                    onClick = {
                        focusManager.clearFocus()
                        keyboardManager?.hide()
                        onAddClick(ip)
                    })
            }
        }
    )
}
@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun DialogToafWifiInterfaceAddDevicePreview()
{
    DialogToafWifiInterfaceAddDevice(
        ipAddress = "192.168.1.1",
        onCancelClick = {},
        onAddClick = {ip ->})
}