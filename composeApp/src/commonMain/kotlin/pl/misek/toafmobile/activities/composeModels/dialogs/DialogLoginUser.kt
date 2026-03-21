package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.database.Database
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

object DialogLoginUser {
    @Composable
    fun Show(
        database : Database?,
        onClose : () -> Unit,
        onLoginUserClick : (userName : String, password : String) -> Unit,
        onChangePasswordClick: () -> Unit,
        onCreateUserClick: () -> Unit) {

        val privacyUrl = "https://pastebin.com/98DhVxHG"
        val uriHandler = LocalUriHandler.current
        var userLogin by remember { mutableStateOf("") }
        var userPassword by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            val userCredential = withContext(Dispatchers.IO) { database?.getLoginAndPassword() }
            userLogin = userCredential?.first ?: ""
            userPassword = userCredential?.second ?: ""
        }
        Surface(
            color = Color.Transparent,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, Color.White),
            modifier = Modifier
                .padding(all = 3.dp)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        )
        {
            AlertDialog(
                modifier = Modifier.border(
                    width = 1.dp,
                    color = ToafColors.toafTextColorWhite,
                ),
                containerColor = ToafColors.toafBackground,
                onDismissRequest = { onClose() },
                properties = DialogProperties(
                    usePlatformDefaultWidth = true
                ),
                title = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(1.dp)
                    )
                    {
                        Text(
                            text = stringResource(Res.string.dialog_user_login_tittle),
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
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(all = 5.dp),
                            value = userLogin,
                            onValueChange = { userLogin = it },
                            label = {
                                Text(
                                    text = stringResource(Res.string.dialog_user_login_login),
                                    style = TextStyle(color = Color.White, fontSize = 14.sp)
                                )
                            },
                            textStyle = TextStyle(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedLabelColor = Color.White,
                                disabledBorderColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                //.weight(1f)
                                .padding(all = 5.dp),
                            value = userPassword,
                            onValueChange = { userPassword = it },
                            label = {
                                Text(
                                    text = stringResource(Res.string.dialog_user_login_password),
                                    style = TextStyle(color = Color.White, fontSize = 14.sp)
                                )
                            },
                            textStyle = TextStyle(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = Color.White,
                                focusedBorderColor = Color.White,
                                unfocusedLabelColor = Color.White,
                                disabledBorderColor = Color.White
                            ),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                            }
                        )
                    }
                },
                confirmButton = {
                    val focusManager = LocalFocusManager.current
                    val keyboardManager = LocalSoftwareKeyboardController.current
                    Column()
                    {
                        Row(modifier = Modifier.fillMaxWidth())
                        {
                            ToafButtons.RedButton(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .weight(0.5f)
                                    .padding(2.dp),
                                buttonText = Res.string.dialog_user_login_cancel,
                                onClick = { onClose() })
                            ToafButtons.GreenButton(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .weight(0.5f)
                                    .padding(2.dp),
                                buttonText = Res.string.dialog_user_login_do_login,
                                onClick = {
                                    focusManager.clearFocus()
                                    keyboardManager?.hide()
                                    onLoginUserClick(userLogin,userPassword)
                                })

                        }
                        Column {
                            ToafButtons.GreenButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(2.dp),
                                buttonText = Res.string.dialog_user_login_change_password,
                                onClick = { onChangePasswordClick() })
                            ToafButtons.GreenButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(2.dp),
                                buttonText = Res.string.dialog_user_login_create_new,
                                onClick = { onCreateUserClick() })
                            Text(
                                text = stringResource(Res.string.privacy_policy_link),
                                color = Color.White,
                                textDecoration = TextDecoration.Underline,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri(privacyUrl)
                                })
                        }
                    }
                }
            )
        }
    }
}


@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun DialogLoginUserPreview()
{
    DialogLoginUser.Show(
        database = null,
        onClose = {},
        onLoginUserClick = {_,_ ->},
        onChangePasswordClick = {},
        onCreateUserClick = {})
}