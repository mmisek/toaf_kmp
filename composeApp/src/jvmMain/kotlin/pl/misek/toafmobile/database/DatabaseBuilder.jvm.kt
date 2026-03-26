package pl.misek.toafmobile.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

fun getDatabaseBuilder(): RoomDatabase.Builder<ToafDatabase> {
    val appFolder = File(System.getProperty("user.home"), ".toaf")
    if (!appFolder.exists()) {
        appFolder.mkdirs()
    }
    val dbFile = File(appFolder, "toaf.db")

    return Room.databaseBuilder<ToafDatabase>(
        name = dbFile.absolutePath
    )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setDriver(BundledSQLiteDriver()) // Silnik SQLite dla KMP
        .setQueryCoroutineContext(Dispatchers.IO)
}