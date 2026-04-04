package pl.misek.toafmobile.activities.composeModels

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.touchlab.kermit.Logger
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.definitions.ToafColors
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.dialog_waiting_checking_purchas
import toafmobile.composeapp.generated.resources.progress_dialog_message

private const val tag = "ToafProgressDialog"
@Composable
fun <T> ToafWaitingDialog(
    action : suspend () -> T,
    onSuccess : (T) -> Unit = {},
    onError : (Throwable) -> Unit = {},
    onDismissRequest: () -> Unit,
){
    ToafProgressDialog(
        action,
        onSuccess,
        onError,
        onDismissRequest,
        Res.string.dialog_waiting_checking_purchas
    )
}

@Composable
fun <T> ToafProgressDialog(
    action : suspend () -> T,
    onSuccess : (T) -> Unit = {},
    onError : (Throwable) -> Unit = {},
    onDismissRequest: () -> Unit,
    progressResource : StringResource? = Res.string.progress_dialog_message
) {
    val dialogShape = RoundedCornerShape(8.dp)
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                val result = action()
                onSuccess(result)
            }

        } catch (ex : Exception)
        {
            Sentry.captureException(ex)
            Logger.e {"$tag : ${ex.toString()}"}
            onError(ex)
        }
        finally {
            onDismissRequest()
        }
    }
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )

    ) {
        Surface(
            modifier = Modifier.wrapContentSize().background(Color.Transparent),
            shape = dialogShape,
            color = ToafColors.toafBackground,
            border = BorderStroke(1.dp, Color.White),
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = ToafColors.toafToastSuccess
                )

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = stringResource(progressResource ?: Res.string.progress_dialog_message),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White
                )
            }
        }
    }
}
@Composable
@Preview(showBackground = true, backgroundColor = 0xFF000000)
private fun Preview()
{
    ToafProgressDialog({ },{},{},{})
}