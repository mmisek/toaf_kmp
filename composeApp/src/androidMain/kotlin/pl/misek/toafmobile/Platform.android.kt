package pl.misek.toafmobile

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()
actual fun getPlatformType() =  PlatformType.Android
actual fun getPlatformName() = "ANDROID"
