package pl.misek.toafmobile.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "user_login") var userLogin: String? = "",
    @ColumnInfo(name = "user_password") var userPassword: String? = "",

    @ColumnInfo(name = "information_INTERNET_ACCOUNT_NECESSARY") var internetAccountNecessary: String? = "N",
    @ColumnInfo(name = "information_ACCESS_TO_PUBLIC_BACKUP") var accessToPublicBackup: String? = "N",
    @ColumnInfo(name = "information_LAST_DEVICE_TYPE") var lastDeviceType: String? = "",
    @ColumnInfo(name = "information_WIFI_DEVICE_IP") var wifiDeviceIp: String? = "",
    @ColumnInfo(name = "information_TOAF_FULL_VERSION") var toafFullVersion: String? = "N",

    @ColumnInfo(name = "information_TOAF_BCU_KEYS") var toafBcuKeys: Int = 0,
    @ColumnInfo(name = "information_TOAF_CURRENT_CAR_TYPE") var toafCurrentCarType: Int = 0,
    @ColumnInfo(name = "information_TOAF_EKA_CODES") var toafEkaCodes: Int = 0,
    @ColumnInfo(name = "information_TOAF_KWP_FAST_INIT_SLEEP") var toafKwpFastInitSleep: Int = 25
)