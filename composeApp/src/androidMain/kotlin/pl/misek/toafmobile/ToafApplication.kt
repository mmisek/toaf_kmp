package pl.misek.toafmobile

import android.app.Application
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import pl.misek.toafmobile.koin.commonModules
import pl.misek.toafmobile.koin.platformModules

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
        }
    }
}