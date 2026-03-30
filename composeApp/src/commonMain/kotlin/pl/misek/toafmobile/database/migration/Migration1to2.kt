package pl.misek.toafmobile.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val Migration1to2 = object : Migration(1,2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE settings ADD COLUMN information_SERIAL_PORT_NAME TEXT"
        )
    }
}