package pl.misek.toafmobile.logger

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import io.sentry.Sentry
import io.sentry.SentryLevel

class SentryLogWriter(private val minSeverity: Severity = Severity.Error) : LogWriter() {

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        if (severity.ordinal < minSeverity.ordinal) return

        val sentryLevel = when (severity) {
            Severity.Warn -> SentryLevel.WARNING
            Severity.Error -> SentryLevel.ERROR
            Severity.Assert -> SentryLevel.FATAL
            else -> SentryLevel.INFO
        }

        if (throwable != null) {
            Sentry.captureException(throwable) { scope ->
                scope.level = sentryLevel
                scope.setTag("function", tag)
                scope.setExtra("message", message)
            }
        } else {
            Sentry.captureMessage(message, sentryLevel) { scope ->
                scope.setTag("function", tag)
            }
        }
    }
}