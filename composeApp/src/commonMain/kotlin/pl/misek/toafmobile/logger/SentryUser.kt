package pl.misek.toafmobile.logger

import kotlin.concurrent.Volatile

/** Login saved in the app, sent with every Sentry event as APP_USER tag; [ANONYMOUS] when none is saved. */
object SentryUser {
    const val ANONYMOUS = "anonymous"

    @Volatile
    var login: String = ANONYMOUS
}
