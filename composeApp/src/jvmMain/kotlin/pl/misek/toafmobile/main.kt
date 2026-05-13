package pl.misek.toafmobile

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import co.touchlab.kermit.CommonWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import io.sentry.Sentry
import org.jetbrains.compose.resources.painterResource
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import pl.misek.toafmobile.activities.AppNavigation
import pl.misek.toafmobile.database.ToafDatabase
import pl.misek.toafmobile.koin.commonModules
import pl.misek.toafmobile.koin.platformModules
import pl.misek.toafmobile.logger.SentryLogWriter
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.toaf_icon_windows
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

var database : ToafDatabase? = null

fun main() {
    startKoin {
        modules(commonModules, platformModules())
        createEagerInstances()
    }
    Sentry.init { options ->
        options.dsn =
            "https://d58e2adef3189565b4341038b83d85b0@o4510877541269504.ingest.de.sentry.io/4510877548937296"
        options.tracesSampleRate = 1.0
        options.isDebug = false
    }
    val isRelease = false

    if (isRelease)
    {
        Logger.setLogWriters(SentryLogWriter(minSeverity = Severity.Error))
    }
    else
    {
        Logger.setLogWriters(
            CommonWriter(),
            SentryLogWriter(minSeverity = Severity.Error)
        )
    }
    database = GlobalContext.get().get<ToafDatabase>()
    setupCrashHandlers(database)

    application {
        fun onExitApp()
        {
            safeCloseDatabase(database, "Crash Handler")
            stopKoin()
            exitApplication()
            exitProcess(0)
        }
        Window(
            onCloseRequest = { onExitApp() },
            title = "Toaf Desktop",
            icon = painterResource(Res.drawable.toaf_icon_windows)
        ) {
            AppNavigation(onExitApp = { onExitApp() })
        }
    }
}


fun setupCrashHandlers(database: ToafDatabase?) {
    // 1. Hak na wypadek ubicia procesu przez system
    Runtime.getRuntime().addShutdownHook(Thread {
        Logger.i {"Awaryjne zamykanie (Shutdown Hook). Czyszczenie zasobów..."}
        safeCloseDatabase(database, "Crash Handler")
    })

    // 2. Hak na wypadek nieprzechwyconego błędu (Crash aplikacji)
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
        Logger.e {"KRYTYCZNY BŁĄD w wątku ${thread.name}: ${exception.message}"}
        Sentry.captureException(exception)
        safeCloseDatabase(database, "Crash Handler")
        defaultHandler?.uncaughtException(thread, exception)
        exitProcess(1)
    }
}
private fun safeCloseDatabase(database: ToafDatabase?, contextInfo: String) {
    val executor = Executors.newSingleThreadExecutor()
    if (database != null) {
        try {
            Logger.i { "Próba zamknięcia bazy ($contextInfo)..." }
            // Uruchamiamy zamykanie w osobnym wątku i czekamy maksymalnie 2 sekundy
            val future = executor.submit { database.close() }
            future.get(2, TimeUnit.SECONDS)
            Logger.i { "Baza zamknięta pomyślnie ($contextInfo)." }
        } catch (e: Exception) {
            Logger.e { "BŁĄD ZAMYKANIA BAZY ($contextInfo) - Porzucam operację by uniknąć zombie: ${e.message}" }
        } finally {
            executor.shutdownNow()
        }
    }
}