package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.definitions.ToafStyles
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleLSMAutoLight
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvanceCodingAutoLights(koinModuleName : KoinNames,onShowResult : (result : Boolean, onSuccess : String?, onFail : String?) -> Unit) {
    val modulesManager : ModulesManager = koinInject()
    val currentModule = modulesManager.getCurrentModules().firstOrNull { it.getKoinName() == koinModuleName}
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var actionType by remember { mutableStateOf(ActionTypeEnum.ReadCoding) }
    var showProgressDialog by remember { mutableStateOf(false) }
    val autoLightOptions = stringArrayResource(Res.array.activity_advance_coding_lsm_auto_light_modes)
    var autoLightOption by remember { mutableStateOf(autoLightOptions[0]) }
    var expandedState by remember { mutableStateOf(false) }
    var currentCoding : List<ByteArray>? = emptyList()
    val writeCodingSuccessText = stringResource(Res.string.message_coding_save_ok)
    val writeCodingFailText = stringResource(Res.string.activity_advance_coding_lsm_write_error)
    val readCodingFailText = stringResource(Res.string.activity_advance_coding_lsm_read_error)

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
            .padding(all = 3.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    )
    {
        if (showProgressDialog)
        {
            ToafProgressDialog(
                action = { action() },
                onSuccess = { actionResult ->
                    if (actionResult is Boolean)
                    {
                        when(actionType)
                        {
                            ActionTypeEnum.WriteCoding ->
                            {
                                onShowResult(
                                    actionResult,
                                    writeCodingSuccessText,
                                    writeCodingFailText
                                )
                            }
                            ActionTypeEnum.ReadCoding ->
                            {
                                autoLightOption = autoLightOptions[(currentModule as IModuleLSMAutoLight).detectAutoLightMode(currentCoding)]
                                if (!actionResult)
                                    onShowResult(
                                        false,
                                        null,
                                        readCodingFailText
                                    )
                            }
                            else -> {}
                        }
                    }
                },
                onError = {
                    onShowResult(
                        false,
                        writeCodingSuccessText,
                        writeCodingFailText
                    )
                },
                onDismissRequest = {showProgressDialog = false})
        }
        Text(
            text = stringResource(Res.string.activity_advance_coding_lsm_auto_light),
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
                value = autoLightOption,
                onValueChange = {},
                trailingIcon = {
                    CompositionLocalProvider(LocalContentColor provides Color.White){
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedState) }},
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
                onDismissRequest = {expandedState = false})
                {
                    autoLightOptions.forEach { option ->
                        DropdownMenuItem(
                            text = {Text(option)},
                            onClick =
                            {
                                autoLightOption = option
                                expandedState = false
                            },
                         )
                    }
                }
        }
        Row(
            modifier = Modifier
                .padding(all = 2.dp)
                .fillMaxWidth()
        )
        {
            ToafButtons.GreenButton(
                buttonText = Res.string.activity_advance_coding_read_button,
                modifier = Modifier.weight(1f).padding(ToafStyles.buttonPadding),
                onClick = {
                    action = {
                        currentCoding = (currentModule as IModuleCoding).readCoding()
                    }
                    actionType = ActionTypeEnum.ReadCoding
                    showProgressDialog = true
                })
            ToafButtons.RedButton(
                buttonText = Res.string.activity_advance_coding_save_button,
                modifier = Modifier.weight(1f).padding(ToafStyles.buttonPadding),
                onClick = {
                    actionType = ActionTypeEnum.WriteCoding
                    val autoLightOptionIndexValue = autoLightOptions.indexOf(autoLightOption)
                    val newCoding = (currentModule as IModuleLSMAutoLight).setAutoLightMode(
                        coding = currentCoding!!,
                        mode = autoLightOptionIndexValue)
                    action = {
                        (currentModule as IModuleCoding).writeCoding(newCoding)
                    }
                    showProgressDialog = true
                },
                enabled = currentCoding != null
                )
        }
    }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun AdvanceCodingAutoLightsPreview()
{
    AdvanceCodingAutoLights(KoinNames.ModuleR40Lsm) { _, _, _ -> }
}