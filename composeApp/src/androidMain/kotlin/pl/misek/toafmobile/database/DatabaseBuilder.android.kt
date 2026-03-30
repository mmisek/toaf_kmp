package pl.misek.toafmobile.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import pl.misek.toafmobile.database.migration.Migration1to2

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<ToafDatabase> {
    val dbFile = context.getDatabasePath("toaf_mobile.db")

    return Room.databaseBuilder<ToafDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath
    )
        .setDriver(BundledSQLiteDriver()) // Silnik SQLite dla KMP
        .setQueryCoroutineContext(Dispatchers.IO)
}