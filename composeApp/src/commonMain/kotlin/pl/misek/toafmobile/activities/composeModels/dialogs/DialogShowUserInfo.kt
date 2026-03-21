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
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.network.UserInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

object DialogShowUserInfo {
    @Composable
    fun Show(userInfo : UserInfo?, onClose : () -> Unit, onDeleteUser : () -> Unit)
    {
        AlertDialog(
            modifier = Modifier.border(
                width = 1.dp,
                color = ToafColors.toafTextColorWhite,
            ),
            containerColor = ToafColors.toafBackground,
            onDismissRequest = {onClose()},
            title = {
                Column(modifier = Modifier
                    .fillMaxWidth()
                    .padding(1.dp))
                {
                    Text(
                        text = stringResource(Res.string.dialog_user_info_title),
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
                Column(modifier = Modifier.padding(5.dp)) {
                    InfoRow(
                        labelRes = Res.string.dialog_user_info_login,
                        value = userInfo?.username)
                    InfoRow(
                        labelRes = Res.string.dialog_user_info_email,
                        value = userInfo?.email)
                    InfoRow(
                        labelRes = Res.string.dialog_user_info_full_version,
                        value = if (userInfo?.isFullVersion() == true)
                            stringResource(Res.string.dialog_user_info_full_version_active)
                        else
                            stringResource(Res.string.dialog_user_info_full_version_not_active)
                    )
                    InfoRow(
                        labelRes = Res.string.dialog_user_info_bcu_keys,
                        value = userInfo?.bcuKeys.toString())
                    InfoRow(
                        labelRes = Res.string.dialog_user_info_eka_codes,
                        value = userInfo?.ekaKeys.toString())
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
                    ToafButtons.RedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(2.dp),
                        buttonText = Res.string.dialog_user_info_delete_user,
                        onClick = { onDeleteUser() })
                }
            }
        )
    }
}




@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun DialogShowUserInfoPreview()
{
    val userInfo = UserInfo(
        username = "exampleUser",
        email = "email@example.com",
        fullVersion =  "Y",
        bcuKeys = 10,
        ekaKeys = 9)
    DialogShowUserInfo.Show(
        userInfo = userInfo,
        onClose = {},
        onDeleteUser = {})
}