package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.network.ToafVersionInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun DialogShowUpdateInformation(versionInfo : ToafVersionInfo?, onClose : () -> Unit)
{
    val urlLink = stringResource(Res.string.dialog_update_link)
    val urlName = stringResource(Res.string.dialog_update_link_name)
    val updateText1 = stringResource(Res.string.dialog_update_text_1)
    val updateText2 = stringResource(Res.string.dialog_update_text_2)
    AlertDialog(
        shape = toafShape,
        modifier = Modifier.border(
            width = 1.dp,
            shape = toafShape,
            color = ToafColors.toafTextColorWhite
        ),

        containerColor = ToafColors.toafBackground,
        onDismissRequest = {onClose()},
        title = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp))
            {
                Text(
                    text = stringResource(Res.string.dialog_update_title),
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
            val updateText = buildAnnotatedString {
                append(String.format(updateText1, versionInfo?.versionName))
                val link = LinkAnnotation.Url(
                    url = urlLink,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = ToafColors.toafToastLink,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
                withLink(link){
                    append(urlLink)
                }
                append(updateText2)
            }
            Column(modifier = Modifier.padding(5.dp)) {
                Text(
                    text = updateText,
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White
                )
            }
        },
        confirmButton = {
            Column {
                ToafButtons.GreenButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.alert_module_info_close,
                    onClick = { onClose() })
            }
        }
    )
}





@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun DialogShowUpdateInformationPreview()
{
    val versionInfo = ToafVersionInfo("1.0.3",101,"ANDROID")
    DialogShowUpdateInformation(versionInfo, onClose = {})
}
