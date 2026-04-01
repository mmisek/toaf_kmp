package pl.misek.toafmobile.database

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import co.touchlab.kermit.Logger
import java.io.File

fun getDatabaseBuilder(): RoomDatabase.Builder<ToafDatabase> {
    val appFolder = File(System.getProperty("user.home"), ".toaf")
    if (!appFolder.exists()) {
        appFolder.mkdirs()
        Logger.i { "Created directory : ${appFolder.absolutePath}"}
    }
    val dbFile = File(appFolder, "toaf.db")
    Logger.i { "Database file : ${dbFile.absolutePath}"}
    return Room.databaseBuilder<ToafDatabase>(
        name = dbFile.absolutePath
    )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .setDriver(BundledSQLiteDriver()) // Silnik SQLite dla KMP
        .setQueryCoroutineContext(Dispatchers.IO)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                super.onCreate(connection)
                connection.execSQL("INSERT INTO settings (user_login," +
                        "user_password," +
                        "information_LAST_DEVICE_TYPE," +
                        "information_SERIAL_PORT_NAME," +
                        "information_WIFI_DEVICE_IP," +
                        ") " +
                        "VALUES ('','','USB','','')")
            }
    })
}