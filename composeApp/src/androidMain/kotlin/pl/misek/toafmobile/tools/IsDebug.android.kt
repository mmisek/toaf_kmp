package pl.misek.toafmobile.tools

import android.content.Context
import android.content.pm.ApplicationInfo

// BuildConfig jest generowany tylko w module androidApp - flagę debug odczytujemy z ApplicationInfo
private var debuggable = false

fun initIsDebug(context: Context) {
    debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

actual fun isDebug() = debuggable
