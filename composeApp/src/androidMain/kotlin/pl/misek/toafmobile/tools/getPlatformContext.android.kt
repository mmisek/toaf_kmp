package pl.misek.toafmobile.tools

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable

@Composable
actual fun getPlatformActivity(): Any? {
    return LocalActivity
}