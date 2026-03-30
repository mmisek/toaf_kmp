package pl.misek.toafmobile.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import pl.misek.toafmobile.database.dao.ToafDao
import pl.misek.toafmobile.database.entities.*

@Database(
    entities = [
        SettingsEntity::class,
        FastControlEntity::class,
        FastLiveDataEntity::class,
        LiveDataTemplateEntity::class
    ],
    version = 2,
    exportSchema = false
)
@ConstructedBy(ToafDatabaseConstructor::class) // Magia KMP!
abstract class ToafDatabase : RoomDatabase() {

    abstract fun toafDao(): ToafDao
}

// Ten obiekt mówi kompilatorowi: "Wygeneruj mi konstruktor pod każdą platformę z osobna"
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ToafDatabaseConstructor : RoomDatabaseConstructor<ToafDatabase>