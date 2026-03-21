package pl.misek.toafmobile.activities.snackbar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pl.misek.toafmobile.activities.definitions.ToafColors

@Composable
fun ToafSnackBarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState = hostState) { data ->
        val customVisuals = data.visuals as? CustomSnackBarVisuals
        val type = customVisuals?.type ?: SnackBarType.Info
        val icon = customVisuals?.icon

        val backgroundColor = when (type) {
            SnackBarType.Success -> ToafColors.toafToastSuccess
            SnackBarType.Error -> ToafColors.toafToastError
            SnackBarType.Info -> MaterialTheme.colorScheme.surfaceVariant
        }

        val contentColor = Color.White

        Snackbar(
            containerColor = backgroundColor,
            contentColor = contentColor,
            dismissActionContentColor = MaterialTheme.colorScheme.errorContainer,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically)
            {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
                Text(
                    text = data.visuals.message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}