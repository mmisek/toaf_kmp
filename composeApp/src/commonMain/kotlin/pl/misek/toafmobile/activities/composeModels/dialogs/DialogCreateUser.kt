package pl.misek.toafmobile.activities.composeModels.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafButtons.toafShape
import pl.misek.toafmobile.activities.definitions.ToafColors
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

object DialogCreateUser {
    @Composable
    fun Show(
        onClose : () -> Unit,
        onCreateUserClick : (userName : String, userEmail : String,userPassword1 : String, userPassword2: String) -> Unit)
        {
        //val privacyUrl = "https://pastebin.com/98DhVxHG"
        //val uriHandler = LocalUriHandler.current
        var userLogin by remember { mutableStateOf("") }
        var userEmail by remember { mutableStateOf("") }
        var userPassword1 by remember { mutableStateOf("") }
        var userPassword2 by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
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
                shape = toafShape,
                modifier = Modifier.border(
                    width = 1.dp,
                    shape = toafShape,
                    color = ToafColors.toafTextColorWhite
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
                            text = stringResource(Res.string.dialog_user_create_tittle),
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
                                    text = stringResource(Res.string.dialog_user_create_login),
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
                            singleLine = true
                        )
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(all = 5.dp),
                            value = userEmail,
                            onValueChange = { userEmail = it },
                            label = {
                                Text(
                                    text = stringResource(Res.string.dialog_user_create_email),
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
                            singleLine = true
                        )
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(all = 5.dp),
                            value = userPassword1,
                            onValueChange = { userPassword1 = it },
                            label = {
                                Text(
                                    text = stringResource(Res.string.dialog_user_create_password),
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
                            },
                            singleLine = true
                        )
                            OutlinedTextField(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    //.weight(1f)
                                    .padding(all = 5.dp),
                                value = userPassword2,
                                onValueChange = { userPassword2 = it },
                                label = {
                                    Text(
                                        text = stringResource(Res.string.dialog_user_create_confirm_password),
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
                                },
                                singleLine = true
                            )
                    }
                },
                confirmButton = {
                    val focusManager = LocalFocusManager.current
                    val keyboardManager = LocalSoftwareKeyboardController.current
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
                            buttonText = Res.string.dialog_user_create_create,
                            onClick = {
                                focusManager.clearFocus()
                                keyboardManager?.hide()
                                onCreateUserClick(userLogin, userEmail, userPassword1, userPassword2)
                            })
                    }
                }
            )
        }
    }
}


@Composable
@Preview(showBackground = true, backgroundColor = 0xff000000)
fun DialogCreateUserPreview()
{
    DialogCreateUser.Show(
        onClose = {},
        onCreateUserClick = {user ,email, pass1, pass2 ->})
}