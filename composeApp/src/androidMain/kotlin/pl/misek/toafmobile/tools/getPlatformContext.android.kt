package pl.misek.toafmobile.tools

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import java.lang.ref.WeakReference

object ActivityProvider {
    private var activityReference : WeakReference<Activity>? = null
    var currentActivity: Activity?
        get() = activityReference?.get()
        set(value) {
            activityReference = value?.let { WeakReference(it) }
        }
}

@Composable
actual fun getPlatformActivity(): Any? {
    return LocalContext.current
    //return ActivityProvider.currentActivity
}