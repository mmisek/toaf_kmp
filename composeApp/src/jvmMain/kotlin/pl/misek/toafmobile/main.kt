package pl.misek.toafmobile

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.sentry.Sentry
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import pl.misek.toafmobile.activities.AppNavigation
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.ToafDatabase
import pl.misek.toafmobile.database.ToafDatabase_Impl
import pl.misek.toafmobile.koin.commonModules
import pl.misek.toafmobile.koin.platformModules

fun main() {
    startKoin {
        modules(commonModules, platformModules())
        createEagerInstances()
    }
    Sentry.init { options ->
        options.dsn = "https://d58e2adef3189565b4341038b83d85b0@o4510877541269504.ingest.de.sentry.io/4510877548937296"
        options.tracesSampleRate = 1.0
        options.isDebug = false
    }

    val database = GlobalContext.get().get<ToafDatabase>()
    setupCrashHandlers(database)

    application {
        Window(
            onCloseRequest = {
                database.close()
                exitApplication()
            },
            title = "Toaf Desktop",
        ) {
            AppNavigation()
        }
    }
}

fun setupCrashHandlers(database: ToafDatabase) {
    // 1. Hak na wypadek ubicia procesu przez system
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Awaryjne zamykanie (Shutdown Hook). Czyszczenie zasobów...")
        try {
            database.close()
        } catch (e: Exception) {
            println("Błąd zamykania bazy w Shutdown Hook: ${e.message}")
        }
    })

    // 2. Hak na wypadek nieprzechwyconego błędu (Crash aplikacji)
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
        println("KRYTYCZNY BŁĄD w wątku ${thread.name}: ${exception.message}")
        Sentry.captureException(exception)
        try {
            database.close()
        } catch (e: Exception) {
            println("Błąd zamykania bazy po crashu: ${e.message}")
        }

        defaultHandler?.uncaughtException(thread, exception)
    }
}