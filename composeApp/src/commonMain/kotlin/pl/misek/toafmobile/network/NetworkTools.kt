package pl.misek.toafmobile.network

import co.touchlab.kermit.Logger

import java.awt.Desktop
import java.net.URI
import java.util.Locale

private const val tag ="NetworkTools"
object NetworkTools {
    fun openUrlInBrowser(url: String) {
        try {
            val uri = URI(url)
            val osName = System.getProperty("os.name").lowercase(Locale.getDefault())

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri)
                return
            }

            // security resolution
            val runtime = Runtime.getRuntime()
            when {
                osName.contains("win") -> {
                    runtime.exec(arrayOf("rundll32", "url.dll,FileProtocolHandler", url))
                }
                osName.contains("mac") -> {
                    runtime.exec(arrayOf("open", url))
                }
                osName.contains("nix") || osName.contains("nux") -> {
                    runtime.exec(arrayOf("xdg-open", url))
                }
                else -> {
                    println("Nie rozpoznano systemu operacyjnego. Skopiuj ten link: $url")
                }
            }
        } catch (e: Exception) {
            Logger.i {"$tag: ${e.message}"}
        }
    }
}