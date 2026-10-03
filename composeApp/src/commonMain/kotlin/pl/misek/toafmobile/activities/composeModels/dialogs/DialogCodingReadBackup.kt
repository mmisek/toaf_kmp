package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.menus.ToafVerticalScrollbar
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.composeModels.ToafComposeElements
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.backup.BackupElement
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

@Composable
fun CodingReadBackupDialog(
    moduleName: String,
    items: List<BackupElement>?,
    onCancelClick: () -> Unit,
    onLoadClick: (Int) -> Unit,
    busy :Boolean
) {
    var selectedBackupId by remember { mutableIntStateOf(10) }
    AlertDialog(
        shape = toafShape,
        modifier = Modifier.border(
            width = 1.dp,
            shape = toafShape,
            color = ToafColors.toafTextColorWhite
        ),

        containerColor = ToafColors.toafBackground,
        onDismissRequest = {onCancelClick()},
        title = {
            Column(modifier = Modifier
                .fillMaxWidth()
                .padding(1.dp)) {
                Text(
                    text = stringResource(Res.string.dialog_backup_read_list_tittle, moduleName),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                if (busy)
                    ToafComposeElements.SmallProgressIndicator()
                else {
                    val listState = rememberLazyListState()
                    Box(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 16.dp)
                        ) {
                            items(items?.size ?: 0) { item ->
                                BackupListItem(
                                    selected = items?.get(item)?.id == selectedBackupId,
                                    item = items?.get(item),
                                    onSelect = { id -> selectedBackupId = id })
                                HorizontalDivider(
                                    Modifier,
                                    DividerDefaults.Thickness,
                                    DividerDefaults.color
                                )
                            }
                        }
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            ToafVerticalScrollbar(listState = listState)
                        }
                    }
                }
            }
        } ,
        confirmButton = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToafButtons.RedButton(
                        buttonText = Res.string.dialog_backup_read_list_cancel,
                        onClick = onCancelClick,
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(5.dp),
                    )
                    ToafButtons.GreenButton(
                        buttonText = Res.string.dialog_backup_read_list_read,
                        onClick = { onLoadClick(selectedBackupId) },
                        modifier = Modifier
                            .weight(0.5f)
                            .padding(5.dp),
                    )
                }
            }
    )
}

@Composable
fun BackupListItem(
    selected: Boolean,
    item: BackupElement?,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                color = if (selected) Color.Green else Color.Transparent,
                shape = RoundedCornerShape(2.dp)
            )
            .clickable { onSelect(item?.id ?: -1) }
            .padding(10.dp),

        ) {
        BackupInfoRow(
            label = stringResource(Res.string.dialog_backup_read_list_element_description),
            value = item?.description ?: ""
        )

        BackupInfoRow(
            label = stringResource(Res.string.dialog_backup_read_list_element_created),
            value = item?.created ?: ""
        )

        if (item?.author?.isNotEmpty() == true) {
            BackupInfoRow(
                label = stringResource(Res.string.dialog_backup_read_list_element_author),
                value = item.author
            )
        }
    }
}

@Composable
fun BackupInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.35f),
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(
            text = value,
            modifier = Modifier.weight(0.65f),
            color = Color.White
        )
    }
}
@Preview(showBackground = true)
@Composable
fun PreviewBackupDialog() {
    CodingReadBackupDialog(
        moduleName = "BCU",
        items = mutableListOf(
            BackupElement(0,"Opis backup", "macrin", "2022.10.11"),
            BackupElement(10,"Opis backup 10", "macrin", "2025.10.11"),
            BackupElement(22,"Opis backup 22", "macrin", "2023.10.11"),
            BackupElement(33,"Opis backup 33", "macrin", "2022.01.01"),
            ),
        onCancelClick = {},
        onLoadClick = {},
        busy = false
    )
}