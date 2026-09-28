package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.composeModels.ToafEditText
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun DialogInput (title : StringResource, name : StringResource, value : String, onCancelClick : () -> Unit, onOkClick : (String) -> Unit, helpText :StringResource? = null, helpImage : DrawableResource? = null, validate : ((String) -> String?)? = null, okButtonText : StringResource = Res.string.dialog_information_ok)
{
    var newValue by remember { mutableStateOf(value) }
    var showHelp by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

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
                    text = stringResource(title),
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
            Column {
                ToafEditText(
                    modifier = Modifier.fillMaxWidth().padding(5.dp),
                    name = name,
                    value = newValue,
                    isError = if (validationError != null) true else null,
                    onValueChanged = {v ->
                        newValue = v
                        validationError = null
                    },
                    onHelpClick = if (helpText != null) { { showHelp = true } } else null
                    )
                validationError?.let {
                    Text(
                        text = it,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp),
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                }
            }
            if (showHelp && helpText != null) {
                DialogHelp(
                    helpText = helpText,
                    image = helpImage,
                    onClose = { showHelp = false })
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
                    buttonText = okButtonText,
                    onClick = {
                        validationError = validate?.invoke(newValue)
                        if (validationError == null)
                            onOkClick(newValue)
                    })
            }
        }
    )
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
private fun DialogInputPreview()
{
    DialogInput(
        title = Res.string.activity_coding_bcu_key_add_message,
        name = Res.string.activity_coding_bcu_key_add_hint,
        value = "AB112",
        onCancelClick = {},
        onOkClick = {},
        helpText =  Res.string.activity_coding_bcu_key_add_hint,
        helpImage = Res.drawable.rover75barcode)
}