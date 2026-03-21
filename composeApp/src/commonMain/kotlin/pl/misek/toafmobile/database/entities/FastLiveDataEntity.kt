package pl.misek.toafmobile.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "fast_action_live_data",
    primaryKeys = ["module_name", "live_data_name"]
)
data class FastLiveDataEntity(
    @ColumnInfo(name = "module_name") val moduleName: String,
    @ColumnInfo(name = "live_data_name") val liveDataName: String,
    @ColumnInfo(name = "custom_name") val customName: String?
)