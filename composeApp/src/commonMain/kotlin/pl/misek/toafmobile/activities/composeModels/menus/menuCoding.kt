package pl.misek.toafmobile.activities.composeModels.menus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

object MenuCoding {
    @Composable
    fun Menu(
        onSaveBackupClick : () -> Unit,
        onReadPrivateBackupClick : () -> Unit,
        onReadPublicBackupClick : () ->Unit)
    {
        var expanded by remember {mutableStateOf(false)}
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .wrapContentSize(Alignment.TopEnd)
        )
        {
            IconButton( onClick = {expanded = !expanded }) {
                Icon(Icons.Default.MoreVert, contentDescription = "", tint = Color.White)
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {expanded = false}
            ) {
                DropdownMenuItem(
                    text = {Text(stringResource(Res.string.activity_coding_menu_save_backup))},
                    leadingIcon =  {Icon(Icons.Default.UploadFile, contentDescription = null)},
                    onClick = {
                        expanded = false
                        onSaveBackupClick()}
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {Text(stringResource(Res.string.activity_coding_menu_load_backup))},
                    leadingIcon =  {Icon(Icons.Default.FileOpen, contentDescription = null)},
                    onClick = {
                        expanded = false
                        onReadPrivateBackupClick()
                    }
                )
                DropdownMenuItem(
                    text = {Text(stringResource(Res.string.activity_coding_menu_load_public_backup))},
                    leadingIcon =  {Icon(Icons.Default.FileOpen, contentDescription = null)},
                    onClick = {
                        expanded = false
                        onReadPublicBackupClick()
                    }
                )
            }
        }
    }
}