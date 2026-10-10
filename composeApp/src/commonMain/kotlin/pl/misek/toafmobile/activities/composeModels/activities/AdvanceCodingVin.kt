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
import pl.misek.toafmobile.PlatformType
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafEditText
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.definitions.ToafStyles
import pl.misek.toafmobile.getPlatformType
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.network.UserInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

private const val tag = "AdvanceCodingVin"
@Composable
fun AdvanceCodingVin(koinModuleName : KoinNames, userInfo: UserInfo, onShowResult : (result : Boolean, onSuccess : String?, onFail : String?) -> Unit) {
    val modulesManager : ModulesManager = koinInject()
    val currentModule = modulesManager.getCurrentModules().firstOrNull { it.getKoinName() == koinModuleName}
    var newVin by remember{ mutableStateOf("") }
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var dialogShowNotFullVersion by remember { mutableStateOf(false) }
    var vinValidationResult by remember { mutableStateOf(true) }
    val writeVinSuccessText = stringResource(Res.string.activity_advance_coding_vin_write_successful)
    val writeVinFailText = stringResource(Res.string.activity_advance_coding_vin_write_error)
    val readVinFailText = stringResource(Res.string.activity_advance_coding_vin_read_error)
    val vinIsNotCorrectedText = stringResource(Res.string.activity_advance_coding_vin_matches_error)
    val dialogFullVersionTittle =stringResource(Res.string.dialog_full_version_tittle)
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
            text = stringResource(Res.string.activity_advance_coding_vin_programming),
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
            name = Res.string.activity_advance_coding_vin_current,
            value = newVin,
            isError = !vinValidationResult,
            onValueChanged = { newValue ->
                newVin = newValue
                vinValidationResult = validateVin(newVin, currentModule)
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
                                writeVinSuccessText,
                                writeVinFailText
                            )
                        }
                    },
                    onError = {
                        onShowResult(
                            false,
                            null,
                            writeVinFailText
                        )
                    },
                    onDismissRequest = {showProgressDialog = false})
            }
            if (dialogShowNotFullVersion)
            {
                DialogNotFullVersion(
                    onWatchAdResult = { showProgressDialog = true},
                    onCloseClick = { dialogShowNotFullVersion = false }
                )
            }
            ToafButtons.GreenButton(
                buttonText = Res.string.activity_advance_coding_read_button,
                modifier = Modifier.weight(1f).padding(ToafStyles.buttonPadding),
                onClick = {
                    action = {
                        currentModule?.getVIN().also {
                            if (it != null) {
                                newVin = it
                            } else {
                                onShowResult(false, null, readVinFailText)
                            }
                        }
                    }
                    showProgressDialog = true
                })
            ToafButtons.RedButton(
                buttonText = Res.string.activity_advance_coding_save_button,
                modifier = Modifier.weight(1f).padding(ToafStyles.buttonPadding),
                onClick = {
                    vinValidationResult = validateVin(newVin, currentModule)
                    if (vinValidationResult) {
                        action = { updateVin(currentModule, newVin) }
                        if (userInfo.isFullVersion())
                            showProgressDialog = true
                        else
                            if (getPlatformType() == PlatformType.Android) {
                                dialogShowNotFullVersion = true
                            }
                            else
                            {
                                onShowResult(false,null,dialogFullVersionTittle)
                            }
                    } else {
                        onShowResult(false, null, vinIsNotCorrectedText)
                    }
                },
                enabled = newVin.isNotEmpty())
            }
        }
    }
}

private fun validateVin(vin : String, module : IModule?) : Boolean
{
    val regex = when ((module as IModuleVIN).getVinLength())
    {
        7 ->"[a-zA-Z0-9][a-zA-Z][0-9]{6}".toRegex()
        // LWS: 2 litery + 6 cyfr hex
        8 -> "[a-zA-Z]{2}[0-9a-fA-F]{6}".toRegex()
        17 -> "[a-zA-Z0-9]{10}[a-zA-Z][0-9]{6}".toRegex()
        else -> "".toRegex()
    }
    return if (vin.isEmpty()) true else vin.matches(regex)
}

private suspend fun updateVin(module : IModule?, vin : String) : Boolean
{
    return withContext(Dispatchers.IO)
    {
        (module as IModuleVIN).writeVIN(vin)
        (module.getVIN() ?: "") == vin
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AdvanceCodingVinPreview()
{
    AdvanceCodingVin(KoinNames.ModuleR40Bcu, UserInfo()) { _, _, _ -> }
}