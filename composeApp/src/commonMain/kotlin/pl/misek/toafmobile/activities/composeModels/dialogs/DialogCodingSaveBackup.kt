package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafComposeElements
import pl.misek.toafmobile.activities.definitions.ToafColors
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun CodingBackupDialog(
    onCancelClick: () -> Unit,
    onSaveClick: (String, Boolean) -> Unit,
    dataTransfer : Boolean)
{
    var description by remember { mutableStateOf("") }
    var isPublic by remember { mutableStateOf(false) }
    AlertDialog(
        containerColor = ToafColors.toafBackground,
        onDismissRequest = {onCancelClick()},
        title = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp)) {
                Text(
                    text = stringResource(Res.string.dialog_make_backup_tittle),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        },
        text = {
            if (dataTransfer)
            {
                ToafComposeElements.SmallProgressIndicator()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { newText ->
                        if (newText.length <= 15) {
                            description = newText
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    singleLine = true,
                    maxLines = 1,
                    label = {
                        Text(
                            text = stringResource(Res.string.dialog_make_backup_description),
                            style = TextStyle(color = Color.White, fontSize = 14.sp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.White,
                        focusedBorderColor = Color.White,

                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,

                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color.White
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                )
                {
                    Checkbox(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        colors = CheckboxDefaults.colors(
                            uncheckedColor = Color.White,
                            checkmarkColor = Color.White,
                            checkedColor = Color.LightGray
                        )
                    )
                    Text(
                        text = stringResource(Res.string.dialog_make_backup_mark_as_public),
                        modifier = Modifier
                            .padding(5.dp),
                        fontSize = 12.sp,
                        color = Color.White
                    )
                }
                Text(
                    text = stringResource(Res.string.dialog_make_backup_info),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    fontSize = 12.sp,
                    color = Color.White,
                    minLines = 5
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            )
            {
                ToafButtons.RedButton(
                    buttonText = Res.string.dialog_make_backup_cancel,
                    onClick = { onCancelClick() },
                    modifier = Modifier
                        .padding(5.dp)
                        .weight(1f),
                )
                ToafButtons.GreenButton(
                    buttonText = Res.string.dialog_make_backup_save_backup,
                    onClick = { onSaveClick(description, isPublic) },
                    modifier = Modifier
                        .padding(5.dp)
                        .weight(1f)
                )
            }
        },
        dismissButton = null
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun BackupDialogScreenPreview()
{
    MaterialTheme{
        CodingBackupDialog(onCancelClick = {}, onSaveClick = {description, isPublic ->}, dataTransfer = true)
    }
}