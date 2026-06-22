package pl.misek.toafmobile.tools

import androidx.compose.runtime.Composable

expect class ShareHandler {
    fun shareMessage(text : String)
}

@Composable
expect fun rememberShareHandler(): ShareHandler
