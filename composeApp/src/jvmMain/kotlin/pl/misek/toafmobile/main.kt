package pl.misek.toafmobile

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.sentry.Sentry
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import pl.misek.toafmobile.activities.AppNavigation
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.ToafDatabase
import pl.misek.toafmobile.database.ToafDatabase_Impl
import pl.misek.toafmobile.koin.commonModules
import pl.misek.toafmobile.koin.platformModules
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

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
                safeCloseDatabase(database, "Crash Handler")
                stopKoin()
                exitApplication()
                exitProcess(0)
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
        safeCloseDatabase(database, "Crash Handler")
    })

    // 2. Hak na wypadek nieprzechwyconego błędu (Crash aplikacji)
    val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
        println("KRYTYCZNY BŁĄD w wątku ${thread.name}: ${exception.message}")
        Sentry.captureException(exception)
        safeCloseDatabase(database, "Crash Handler")
        defaultHandler?.uncaughtException(thread, exception)
        exitProcess(1)
    }
}
private fun safeCloseDatabase(database: ToafDatabase, contextInfo: String) {
    val executor = Executors.newSingleThreadExecutor()
    try {
        println("Próba zamknięcia bazy ($contextInfo)...")
        // Uruchamiamy zamykanie w osobnym wątku i czekamy maksymalnie 2 sekundy
        val future = executor.submit { database.close() }
        future.get(2, TimeUnit.SECONDS)
        println("Baza zamknięta pomyślnie ($contextInfo).")
    } catch (e: Exception) {
        println("BŁĄD ZAMYKANIA BAZY ($contextInfo) - Porzucam operację by uniknąć zombie: ${e.message}")
    } finally {
        executor.shutdownNow()
    }
}