package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.modules.ModuleInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

object DialogShowIdentification {
    @Composable
    fun ShowIdentification(info: ModuleInfo?, odometer: String?, vin: String?, onConfirm : () -> Unit)
    {
        var infoText = (info?.getFormatedInfo() ?: "")
        if (!vin.isNullOrEmpty())
            infoText += stringResource(Res.string.alert_module_info_vin) + ": " + vin
        if (!odometer.isNullOrEmpty())
            infoText += "\n" + stringResource(Res.string.alert_module_info_odometer) + ": " + odometer
        AlertDialog(
            shape = toafShape,
            modifier = Modifier.border(
                width = 1.dp,
                shape = toafShape,
                color = ToafColors.toafTextColorWhite
            ),

            containerColor = ToafColors.toafBackground,
            onDismissRequest = {onConfirm()},
            title = {
                Text(
                    text = stringResource(Res.string.alert_module_info_tittle),
                    color = ToafColors.toafTextColorWhite)
                    },
            text = {
                Text(
                    modifier = Modifier.fillMaxWidth().padding(1.dp),
                    text = infoText,
                    color = ToafColors.toafTextColorWhite
                )
            },
            confirmButton = {
                ToafButtons.GreenButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    buttonText = Res.string.alert_module_info_close,
                    onClick = {onConfirm()})
            },
        )
    }
}
@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun ShowIdentificationPreview()
{
    val moduleInfo = ModuleInfo()
    moduleInfo.id1 = "YWA001233"
    moduleInfo.id2 = "BMW112233"
    moduleInfo.bi = "03"
    moduleInfo.hw = "A"
    moduleInfo.ci = "02"
    moduleInfo.di = "12"
    moduleInfo.sw = "0.11"
    moduleInfo.date = "10/10/2022"
    moduleInfo.serial = "AA001234"
    moduleInfo.producer = "BMW"
    val vin = "VIN....."
    val odometer = "123456"
    DialogShowIdentification.ShowIdentification(moduleInfo, odometer, vin) {}
}