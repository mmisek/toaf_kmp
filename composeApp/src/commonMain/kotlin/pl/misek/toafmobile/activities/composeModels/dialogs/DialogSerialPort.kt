package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.interfaces.IUsbEventManager
import pl.misek.toafmobile.interfaces.UsbEvent
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogSerialPort (onCancelClick : () -> Unit, onOkClick : (String) -> Unit)
{
    var expandedState by remember { mutableStateOf(false) }
    var availableSerialPorts by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSerialPort by remember { mutableStateOf("") }
    val usbEventViewModel : IUsbEventManager = koinInject()
    val database : Database = koinInject()

    LaunchedEffect(Unit)
    {
        availableSerialPorts = usbEventViewModel.getAvailableDevices()
        val currentDevice = database.getStringSettingValueByName(DatabaseSettingFields.SERIAL_PORT_NAME)
        if (currentDevice != null && availableSerialPorts.contains(currentDevice)) {
                selectedSerialPort = currentDevice
        }
    }
    LaunchedEffect(Unit)
    {
        usbEventViewModel.usbState.collect { event ->
            when (event) {
                is UsbEvent.Attached, is UsbEvent.Detached  -> {
                    availableSerialPorts = usbEventViewModel.getAvailableDevices().sorted()
                }
                else -> {}
            }
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
        onDismissRequest = { onCancelClick() },
        title = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp))
            {
                Text(
                    text = stringResource(Res.string.dialog_serial_port_select_port),
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
            Column(
                modifier = Modifier
                    .padding(all = 3.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Text(
                    text = stringResource(Res.string.dialog_serial_port_available_ports),
                    textAlign = TextAlign.Center,
                    style = TextStyle(color = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(all = 2.dp)
                )
                ExposedDropdownMenuBox(
                    expanded = expandedState,
                    onExpandedChange = { expandedState = !expandedState },
                )
                {
                    TextField(
                        value = selectedSerialPort,
                        onValueChange = {},
                        trailingIcon = {
                            CompositionLocalProvider(LocalContentColor provides Color.White) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedState)
                            }
                        },
                        readOnly = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(all = 5.dp)
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedLabelColor = Color.White,
                            disabledBorderColor = Color.White
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = expandedState,
                        onDismissRequest = { expandedState = false })
                    {
                        availableSerialPorts.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick =
                                    {
                                        selectedSerialPort = option
                                        expandedState = false
                                    },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column {
                ToafButtons.RedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.dialog_input_cancel,
                    onClick = { onCancelClick() })
                ToafButtons.GreenButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.dialog_information_ok,
                    onClick = { onOkClick(selectedSerialPort) })
            }
        }
    )
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
private fun DialogSerialPortPreview()
{
    DialogSerialPort(
        onCancelClick = {},
        onOkClick = {})
}