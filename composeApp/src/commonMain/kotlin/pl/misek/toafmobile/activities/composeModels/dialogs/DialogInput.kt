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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafEditText
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun DialogInput (title : StringResource, name : StringResource, value : String, onCancelClick : () -> Unit, onOkClick : (String) -> Unit)
{
    var newValue by remember { mutableStateOf(value) }

    AlertDialog(
        modifier = Modifier.border(
            width = 1.dp,
            color = ToafColors.toafTextColorWhite,
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
            ToafEditText(
                modifier = Modifier.fillMaxWidth().padding(5.dp),
                name = name,
                value = newValue,
                onValueChanged = {v -> newValue = v},
                )
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
                    onClick = { onOkClick(newValue) })
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
        onOkClick = {})
}