package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.ModuleAddress
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Locale

private const val tag = "DialogShowErrors"
object DialogShowErrors {
    @Composable
    fun ShowErrors(errors : List<ModuleError>?, onClose : () -> Unit, onDeleteErrors: () -> Unit)
    {
        var expandId by remember { mutableIntStateOf(-1) }
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
                Column(modifier = Modifier.fillMaxWidth().padding(1.dp))
                {
                    Text(
                        text = stringResource(Res.string.alert_dialog_errors_title),
                        color = ToafColors.toafTextColorWhite,
                        fontSize = 16.sp
                    )
                    Text(
                        text = stringResource(Res.string.alert_dialog_errors_subtitle),
                        color = ToafColors.toafTextColorWhite,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .padding(1.dp)
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(ToafColors.toafBackground)
                ) {
                    items(errors!!.size) { index ->
                        ErrorItem(
                            moduleError = errors[index],
                            showDetails = expandId == index,
                            id = index,
                            onItemClick = { index ->
                                expandId = if (expandId == index) -1 else index
                            })
                    }
                }
            },
            confirmButton = {
                ToafButtons.GreenButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.alert_module_info_close,
                    onClick = {onClose()})
            },
            dismissButton = {
                ToafButtons.RedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(2.dp),
                    buttonText = Res.string.module_error_clear,
                    onClick = { onDeleteErrors() })
            }
        )
    }
}
@Composable
fun ErrorItem(moduleError: ModuleError, showDetails : Boolean, id :Int, onItemClick : (Int) -> Unit)
{
    val details = StringBuilder()
    for (detail in moduleError.details) {
        val param = when (val v = detail.second)
        {
            null -> ""
            is String -> v
            is Int, is Long, is Byte -> v.toString()
            is Float, is Double-> "%.2f".format(Locale.US,v)
            else -> {
                Logger.e {"$tag : no format for data type ${v::class.simpleName}"}
                ""
            }
        }
        if (param.isEmpty())
            details.append("\t${stringResource(detail.first)}\n")
        else
            details.append("\t${stringResource(detail.first)}: ${param}\n")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(1.dp)
            .background(ToafColors.toafBackground)
            .clickable {onItemClick(id)},
        content =
            {
                Text(modifier = Modifier
                    .padding(1.dp)
                    .fillMaxWidth(),
                    fontSize = 14.sp,
                    color = ToafColors.toafTextColorWhite,
                    text =  moduleError.code.uppercase()  + " - " + moduleError.description?.let { stringResource(it)}
                )
                if (showDetails)
                {
                    Text(
                        modifier = Modifier
                            .padding(1.dp)
                            .fillMaxWidth(),
                        color = ToafColors.toafTextColorWhite,
                        fontSize = 10.sp,
                        text = details.toString()
                    )
                }
            }
    )
}
@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun ShowErrorsPreview()
{
    val errors : MutableList<ModuleError> = ArrayList()
    errors.add(ModuleError( ModuleAddress.MODULE_BCU,"06",listOf(
        Pair(Res.string.module_error_clear, 12))))
    errors.add(ModuleError(ModuleAddress.MODULE_BCU,"8C",listOf(
        Pair(Res.string.module_error_clear, "test"),
        Pair(Res.string.module_live_data, null))))
    DialogShowErrors.ShowErrors(errors = errors, onClose = {}, {})
}