package pl.misek.toafmobile.tools

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

actual class ShareHandler {
    actual fun shareMessage(text: String) {
        val selection = StringSelection(text)
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(selection, null)
    }
}

@Composable
actual fun rememberShareHandler(): ShareHandler {
    return remember { ShareHandler() }
}