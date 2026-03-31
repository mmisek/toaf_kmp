package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.interfaces.wifi.ToafWifiInterface
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun DialogToafWifiAccessPointConfiguration(
    twi : ToafWifiInterface?,
    onClose : () -> Unit,
    onReadOperationResult : (result : Boolean) ->Unit,
    onSaveOperationResult : (result : Boolean) ->Unit)
    {

        val scope = rememberCoroutineScope()
        var twiAccessPointName by remember { mutableStateOf("") }
        var twiAccessPointPassword by remember { mutableStateOf("") }
        var operationSaveResult by remember { mutableStateOf(false) }
        var credentials by remember { mutableStateOf<Pair<String, String>?>(null) }

        fun getLoginAndPassword()
        {
            scope.launch {
                withContext(Dispatchers.IO)
                {
                    val credentials = twi?.getSsidAndPassword()
                    credentials?.let {
                        twiAccessPointName = it.first
                        twiAccessPointPassword = it.second
                    }
                }
            }
        }
        fun setLoginAndPassword()
        {
            scope.launch {
                withContext(Dispatchers.IO)
                {
                     operationSaveResult =
                         twi?.setSsidAndPassword(twiAccessPointName,twiAccessPointPassword) == true
                }
            }
        }
        LaunchedEffect(Unit) {
            getLoginAndPassword()
        }
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color.White),
            modifier = Modifier
                .padding(all = 3.dp)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        )
        {
        AlertDialog(
            shape = toafShape,
            modifier = Modifier.border(
                width = 1.dp,
                shape = toafShape,
                color = ToafColors.toafTextColorWhite
            ),

            containerColor = ToafColors.toafBackground,
            onDismissRequest = { onClose() },
            properties = DialogProperties(
                usePlatformDefaultWidth = true
            ),
            title = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp)
                )
                {
                    Text(
                        text = stringResource(Res.string.alert_dialog_wifi_configuration_title),
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
                        value = twiAccessPointName,
                        onValueChange = { twiAccessPointName = it },
                        label = {
                            Text(
                                text = stringResource(Res.string.alert_dialog_wifi_configuration_ap_password_SSID),
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
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            //.weight(1f)
                            .padding(all = 5.dp),
                        value = twiAccessPointPassword,
                        onValueChange = { twiAccessPointPassword = it },
                        label = {
                            Text(
                                text = stringResource(Res.string.alert_dialog_wifi_configuration_ap_password_password),
                                style = TextStyle(color = Color.White, fontSize = 14.sp)
                            )
                        },
                        textStyle = TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedLabelColor = Color.White,
                            disabledBorderColor = Color.White
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                        }
                    )
                }
            },
            confirmButton = {
                val focusManager = LocalFocusManager.current
                val keyboardManager = LocalSoftwareKeyboardController.current
                Column()
                {
                    Row(modifier = Modifier.fillMaxWidth())
                    {
                        ToafButtons.GreenButton(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .weight(0.5f)
                                .padding(2.dp),
                            buttonText = Res.string.alert_dialog_wifi_configuration_ap_password_button_read,
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { getLoginAndPassword() }
                                }
                                onReadOperationResult(credentials == null)
                            })
                        ToafButtons.GreenButton(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .weight(0.5f)
                                .padding(2.dp),
                            buttonText = Res.string.alert_dialog_wifi_configuration_ap_password_button_save,
                            onClick = {
                                focusManager.clearFocus()
                                keyboardManager?.hide()
                                scope.launch {
                                    withContext(Dispatchers.IO) { setLoginAndPassword() }
                                    onSaveOperationResult(operationSaveResult)
                                }

                            })

                    }
                    Column {
                        ToafButtons.RedButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(2.dp),
                            buttonText = Res.string.alert_dialog_wifi_configuration_ap_password_button_cancel,
                            onClick = { onClose() })
                    }
                }
            }
        )
    }
}


@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun DialogToafWifiAccessPointConfigurationPreview()
{
    DialogToafWifiAccessPointConfiguration(
        twi = ToafWifiInterface(""),
        onClose = {},
        onReadOperationResult = {},
        onSaveOperationResult = {})
}