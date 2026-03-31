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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.definitions.ToafColors
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

object DialogQuestion {
    @Composable
    fun Show(question : StringResource, onNoClick : () -> Unit, onYesClick : () -> Unit)
    {
        AlertDialog(
            shape = toafShape,
            modifier = Modifier.border(
                width = 1.dp,
                shape = toafShape,
                color = ToafColors.toafTextColorWhite
            ),

            containerColor = ToafColors.toafBackground,
            onDismissRequest = {onNoClick()},
            title = {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp))
                {
                    Text(
                        text = stringResource(Res.string.dialog_question_title),
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
                Text(
                    text = stringResource(question),
                    fontSize = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            },
            confirmButton = {
                Column {
                    ToafButtons.RedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                        buttonText = Res.string.dialog_question_no,
                        onClick = { onNoClick() })
                    ToafButtons.GreenButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                        buttonText = Res.string.dialog_question_yes,
                        onClick = { onYesClick() })
                }
            }
        )
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun DialogQuestionPreview()
{
    DialogQuestion.Show(
        question = Res.string.dialog_user_info_delete_user_question,
        onNoClick = {},
        onYesClick = {})
}