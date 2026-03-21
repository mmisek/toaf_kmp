package pl.misek.toafmobile.activities.composeModels.menus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

object MenuMain {
    @Composable
    fun Menu(
        onShowSelectCarType : () -> Unit,
        onShowUserLogin : () -> Unit,
        onShowWifiInterfaceConfig : () -> Unit
    )
    {
        var expanded by remember {mutableStateOf(false)}
        Box(
            modifier = Modifier
                ///.fillMaxWidth()
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
                    text = {Text(stringResource(Res.string.main_menu_select_car_type))},
                    leadingIcon =  {Icon(Icons.Default.CarRental, contentDescription = null)},
                    onClick = {
                        expanded = false
                        onShowSelectCarType()}
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {Text(stringResource(Res.string.main_menu_user_login))},
                    leadingIcon =  {Icon(Icons.Default.Person, contentDescription = null)},
                    onClick = {
                        expanded = false
                        onShowUserLogin()
                    }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = {Text(stringResource(Res.string.main_menu_wifi))},
                    leadingIcon =  {Icon(Icons.Default.WifiFind, contentDescription = null)},
                    onClick = {
                        expanded = false
                        onShowWifiInterfaceConfig()
                    }
                )
                DropdownMenuItem(
                    text = {Text(stringResource(Res.string.activity_coding_menu_load_public_backup))},
                    leadingIcon =  {Icon(Icons.Default.FileOpen, contentDescription = null)},
                    onClick = {
                        expanded = false
                        //onReadPublicBackupClick()
                    }
                )
            }
        }
    }
}