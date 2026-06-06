package pl.misek.toafmobile

import android.app.Application
import co.touchlab.kermit.Logger
import com.google.android.gms.ads.MobileAds
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import pl.misek.toafmobile.koin.commonModules
import pl.misek.toafmobile.koin.platformModules

private const val tag = "ToafApplication"
class ToafApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ToafApplication)
            modules(commonModules,platformModules())
            createEagerInstances()
        }

        SentryAndroid.init(this) { options ->
            options.dsn = "https://d58e2adef3189565b4341038b83d85b0@o4510877541269504.ingest.de.sentry.io/4510877548937296"
            options.tracesSampleRate = 1.0
            options.isDebug = false
            options.isAnrEnabled = true
            options.beforeSend = SentryOptions.BeforeSendCallback{event, _ ->
                event.setTag("APP_VERSION_CODE", BuildKonfig.VERSION_CODE.toString())
                event.setTag("APP_VERSION_NAME", BuildKonfig.VERSION_NAME)
                event.setTag("APP_PLATFORM", getPlatformName())
                event
            }
        }
        MobileAds.initialize(this) {initializationStatus ->
            Logger.i {"MobileAds : ${initializationStatus.adapterStatusMap}"}
        }
    }
}