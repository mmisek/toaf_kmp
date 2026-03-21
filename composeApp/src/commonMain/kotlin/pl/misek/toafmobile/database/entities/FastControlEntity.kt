package pl.misek.toafmobile.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "fast_action_control",
    primaryKeys = ["module_name", "control_name"]
)
data class FastControlEntity(
    @ColumnInfo(name = "module_name") val moduleName: String,
    @ColumnInfo(name = "control_name") val controlName: String,
    @ColumnInfo(name = "control_value") val controlValue: Float,
    @ColumnInfo(name = "custom_name") val customName: String?
)