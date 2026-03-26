package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafEditText
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.definitions.ToafStyles.buttonPadding
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleOdometer
import pl.misek.toafmobile.network.UserInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

private const val tag = "AdvanceCodingOdometer"
@Composable
fun AdvanceCodingOdometer(
    koinModuleName : KoinNames,
    userInfo : UserInfo,
    onShowResult : (result : Boolean, onSuccess : String?, onFail : String? ) -> Unit
)
{
    val modulesManager : ModulesManager = koinInject()
    val currentModule = modulesManager.getCurrentModules().firstOrNull { it.getKoinName() == koinModuleName}
    var newOdometer by remember { mutableStateOf("") }
    var showProgressDialog by remember { mutableStateOf(false) }
    var showFullVersionDialog by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var odometerValidationResult by remember { mutableStateOf(true) }
    val writeOdoSuccessText = stringResource(Res.string.activity_advance_coding_odometer_write_successful)
    val writeOdoFailText = stringResource(Res.string.activity_advance_coding_odometer_write_error)
    val onValidationFailText = stringResource(Res.string.activity_advance_coding_odometer_matches_error)
    val onReadFailText = stringResource(Res.string.activity_advance_coding_odometer_read_error)

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
    )
    {
        Text(
            text = stringResource(Res.string.activity_advance_coding_odometer_programming),
            textAlign = TextAlign.Center,
            style = TextStyle(color = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(all = 2.dp)
        )
        ToafEditText(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(all = 5.dp),
            name = Res.string.activity_advance_coding_odometer_current,
            value = newOdometer,
            isError = !odometerValidationResult,
            onValueChanged = { newValue ->
                newOdometer = newValue
                odometerValidationResult = validateOdometer(newOdometer)
            }
        )
        Row(
            modifier = Modifier
                .padding(all = 2.dp)
                .fillMaxWidth()
        )
        {
            if (showProgressDialog)
            {
                ToafProgressDialog(
                    action = { action() },
                    onSuccess = { actionResult ->
                        if (actionResult is Boolean)
                        {
                            onShowResult(
                                actionResult,
                                writeOdoSuccessText,
                                writeOdoFailText
                            )
                        }
                    },
                    onError = {
                        onShowResult(
                            false,
                            writeOdoSuccessText,
                            writeOdoFailText
                        )
                    },
                    onDismissRequest = {showProgressDialog = false})
            }
            if (showFullVersionDialog)
            {
                DialogNotFullVersion(
                    onWatchAdResult = { showProgressDialog = true },
                    onCloseClick = {showProgressDialog = false}
                )
            }
            ToafButtons.GreenButton(
                buttonText = Res.string.activity_advance_coding_read_button,
                modifier = Modifier
                    .weight(1f)
                    .padding(buttonPadding),
                onClick = {
                        action = {
                            currentModule?.getOdometer().also {
                                if (it != null)
                                {
                                    newOdometer = it
                                }
                                else
                                {
                                    onShowResult(false,null,onReadFailText)
                                }
                            }
                        }
                    showProgressDialog = true
                })
            ToafButtons.RedButton(
                buttonText = Res.string.activity_advance_coding_save_button,
                modifier = Modifier
                    .weight(1f)
                    .padding(buttonPadding),
                onClick = {
                    if (odometerValidationResult) {
                        action = {
                            updateOdometer(currentModule,newOdometer.toLong())
                        }
                        if (userInfo.isFullVersion())
                        {
                            showProgressDialog = true
                        }
                        else
                        {
                            showFullVersionDialog = true
                        }
                    } else {
                        onShowResult(false,null,onValidationFailText)
                    }
                },
                enabled = newOdometer.isNotEmpty()
                )
        }
    }
    }
}

private fun validateOdometer(odometer : String) : Boolean
{
    val odo: Long = odometer.toLongOrNull() ?: 0
    return (odo in 1..999999)
}

private suspend fun updateOdometer(module : IModule?,  odometer : Long) : Boolean
{
    return withContext(Dispatchers.IO)
    {
        (module as IModuleOdometer).writeOdometer(odometer)
        val newOdometer = module.getOdometer() ?: "0"
        newOdometer.toLong()/100 == odometer/100
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AdvanceCodingOdometerPreview()
{
    AdvanceCodingOdometer(koinModuleName = KoinNames.ModuleR40Bcu, UserInfo()) { _, _, _ -> }
}