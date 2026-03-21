package pl.misek.toafmobile.activities.snackbar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

class ToafSnackBar {

    fun showSuccess(scope : CoroutineScope, snackBarHostState : SnackbarHostState, messageSuccess : String)
    {
        show(scope = scope, snackBarHostState = snackBarHostState, message = messageSuccess, snackBarType = SnackBarType.Success, icon = Icons.Default.CheckCircle)
    }

    fun showError(scope : CoroutineScope, snackBarHostState : SnackbarHostState, messageError : String)
    {
        show(scope = scope, snackBarHostState = snackBarHostState, message = messageError, snackBarType = SnackBarType.Error,icon = Icons.Default.Error)
    }

    fun showResult(scope : CoroutineScope, snackBarHostState : SnackbarHostState, result : Boolean, messageSuccess : String, messageError : String)
    {
        if (result)
            showSuccess(scope = scope, snackBarHostState = snackBarHostState, messageSuccess = messageSuccess)
        else
            showError(scope = scope, snackBarHostState = snackBarHostState, messageError = messageError)
    }

    private fun show(scope : CoroutineScope, snackBarHostState : SnackbarHostState, message : String, snackBarType : SnackBarType, icon : ImageVector?)
    {
        scope.launch {
            snackBarHostState.showSnackbar(
                visuals = CustomSnackBarVisuals(
                    message = message,
                    type = snackBarType,
                    icon = icon
                )
            )
        }
    }
}