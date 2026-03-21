package pl.misek.toafmobile.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "live_data_templates")
data class LiveDataTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val module: String,
    val name: String,
    val codes: String
)