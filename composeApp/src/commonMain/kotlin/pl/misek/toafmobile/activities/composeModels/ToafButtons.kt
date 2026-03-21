package pl.misek.toafmobile.activities.composeModels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.composeModels.ToafButtons.ImageButton
import pl.misek.toafmobile.activities.definitions.ToafStyles
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.usb_connected

object ToafButtons {

    @Composable
    fun GreenButton(buttonText: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
        ToafButton(buttonText, ToafColors.greenButton, onClick, modifier, enabled)
    }
    @Composable
    fun GreenButton(buttonText: StringResource, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
        ToafButton(buttonText, ToafColors.greenButton, onClick, modifier, enabled)
    }

    @Composable
    fun RedButton(buttonText: StringResource, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
        ToafButton(buttonText, ToafColors.redButton, onClick, modifier, enabled)
    }

    @Composable
    fun ToafButton(
        buttonText: StringResource,
        color: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled : Boolean
    ) {
        FilledTonalButton(
            enabled = enabled,
            modifier = modifier,
            border = BorderStroke(ToafStyles.borderSize, ToafColors.borderColor),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = color,
                disabledContainerColor = ToafColors.disabledButton
            ),
            onClick = { onClick() },
            content = {
                Text(stringResource(buttonText),
                    fontSize = ToafStyles.buttonFontSize,
                    color = Color.White,
                    textAlign = TextAlign.Center,) }
        )
    }

    @Composable
    fun ToafButton(
        buttonText: String,
        color: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled : Boolean
    ) {
        FilledTonalButton(
            enabled = enabled,
            modifier = modifier,
            border = BorderStroke(ToafStyles.borderSize, ToafColors.borderColor),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = color,
                disabledContainerColor = ToafColors.disabledButton
            ),
            onClick = { onClick() },
            content = {
                Text(buttonText,
                    fontSize = ToafStyles.buttonFontSize,
                    color = Color.White,
                    textAlign = TextAlign.Center,) }
        )
    }
    @Composable
    fun ImageButton(moduleName: String, moduleDescriptor: StringResource?, buttonImage: DrawableResource?, onClick: () -> Unit, modifier: Modifier) {
        FilledTonalButton(
            modifier = modifier,
            shape = RoundedCornerShape(5),
            border = BorderStroke(ToafStyles.borderSize, ToafColors.borderColor),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = ToafColors.greenButton
            ),
            onClick = { onClick() },
            content = {
                Column()
                {
                    Image(
                        painter = painterResource(buttonImage ?: Res.drawable.usb_connected),
                        contentDescription = "",
                        modifier = Modifier.fillMaxWidth().padding(5.dp)
                    )
                    Text(
                        moduleName,
                        fontSize = ToafStyles.buttonFontSize,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.White,
                    )
                    Text(
                        text = if (moduleDescriptor != null) stringResource(moduleDescriptor) else "ERROR",
                        fontSize = ToafStyles.buttonFontSizeSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.White,
                    )
                }
            }
        )
    }
    @Composable
    fun ControlButton(moduleName: String, controlDescriptor: String, onClick: () -> Unit, modifier: Modifier) {
        FilledTonalButton(
            modifier = modifier,
            shape = RoundedCornerShape(5),
            border = BorderStroke(ToafStyles.borderSize, ToafColors.borderColor),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = ToafColors.greenButton
            ),
            onClick = { onClick() },
            content = {
                Column()
                {
                    Text(
                        moduleName,
                        fontSize = ToafStyles.buttonFontSize,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.White,
                    )
                    Text(
                        text = controlDescriptor,
                        fontSize = ToafStyles.buttonFontSizeSmall,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.White,
                    )
                }
            }
        )
    }
}

@Preview(showBackground = true, name = "ImageButtonPreview")
@Composable
fun ImageButtonPreview()
{
    val currentModule = remember { MockModule() }
    ImageButton(
        moduleName = currentModule.getModuleName(),
        moduleDescriptor = currentModule.getModuleDescription(),
        buttonImage = currentModule.getImageResource(),
        onClick = {},
        modifier = Modifier.padding(5.dp)
    )
}
