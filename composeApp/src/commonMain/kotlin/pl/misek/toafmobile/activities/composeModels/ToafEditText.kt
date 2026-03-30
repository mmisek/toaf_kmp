package pl.misek.toafmobile.activities.composeModels

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.activity_advance_coding

@Composable
fun ToafEditText(
    modifier: Modifier,
    name : StringResource? =null,
    nameString : String? = null,
    value : String,
    readOnly : Boolean = false,
    isError : Boolean? = null,
    onValueChanged :(newValue :String) -> Unit)
{
    var newValue by remember { mutableStateOf(value) }
    OutlinedTextField(
        modifier = modifier,
        value = value,
        onValueChange = {
            newValue = it
            onValueChanged(newValue)},
        label = {
            Text(
                text = if (name != null ) stringResource(name) else nameString ?: "",
                style = TextStyle(color = Color.White, fontSize = 14.sp)
            )
        },
        textStyle = TextStyle(color = Color.White),
        readOnly = readOnly,
        isError = isError == true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            errorBorderColor = Color.White,
            unfocusedBorderColor = Color.White,
            focusedBorderColor = Color.White,
            unfocusedLabelColor = Color.White,
            disabledBorderColor = Color.White,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent,
        ),
        leadingIcon = {
            isError?.let {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isError) Color.Red else Color.Green
                )
            }
        }
    )
}

@Preview(showBackground = true, name = "ImageButtonPreview")
@Composable
fun ToafEditTextPreview()
{
    ToafEditText(
        modifier = Modifier.fillMaxWidth(),
        name = Res.string.activity_advance_coding,
        value = "Example text",
        onValueChanged = {_ ->},
    )
}
