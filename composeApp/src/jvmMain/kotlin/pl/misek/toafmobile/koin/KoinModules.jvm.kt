package pl.misek.toafmobile.koin

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import pl.misek.toafmobile.ads.DesktopToafRewardAds
import pl.misek.toafmobile.ads.IToafRewardAds
import pl.misek.toafmobile.billing.DesktopToafBilling
import pl.misek.toafmobile.billing.IToafBilling
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.ToafDatabase
import pl.misek.toafmobile.database.ToafDatabase_Impl
import pl.misek.toafmobile.database.dao.ToafDao
import pl.misek.toafmobile.database.migration.Migration1to2
import pl.misek.toafmobile.interfaces.DesktopUsbBroadcastHandler
import pl.misek.toafmobile.interfaces.DesktopUsbEventManager
import pl.misek.toafmobile.interfaces.ISerialDevice
import pl.misek.toafmobile.interfaces.IUsbBroadcastHandler
import pl.misek.toafmobile.interfaces.IUsbEventManager
import pl.misek.toafmobile.network.DesktopWifiManager
import pl.misek.toafmobile.network.IWifiManager
import pl.misek.toafmobile.interfaces.serial.DesktopSerialDevice
import pl.misek.toafmobile.tools.DesktopFileSupport
import pl.misek.toafmobile.tools.IFileSupport
import java.io.File

actual fun platformModules(): Module  = module {
    single(named(KoinNames.LiveDataLogFolder.name)) { File(System.getProperty("user.home"), ".toaf") }
    single<ToafDatabase>{
        val appFolder = File(System.getProperty("user.home"), ".toaf")
        if (!appFolder.exists()) {
            appFolder.mkdirs()
            Logger.i { "Created directory : ${appFolder.absolutePath}"}
        }
        val dbFile = File(appFolder, "toaf.db")
        Logger.i { "Database file : ${dbFile.absolutePath}"}
        Room.databaseBuilder<ToafDatabase>(
            name = dbFile.absolutePath
        )
            .setDriver(BundledSQLiteDriver())
            .addMigrations(Migration1to2)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(connection: SQLiteConnection) {
                super.onCreate(connection)
                connection.execSQL("INSERT INTO settings (user_login," +
                        "user_password," +
                        "information_LAST_DEVICE_TYPE," +
                        "information_SERIAL_PORT_NAME," +
                        "information_WIFI_DEVICE_IP," +
                        "information_TOAF_BCU_KEYS," +
                        "information_TOAF_CURRENT_CAR_TYPE," +
                        "information_TOAF_EKA_CODES," +
                        "information_TOAF_KWP_FAST_INIT_SLEEP) " +
                        "VALUES ('','','USB','','',0,0,0,25)")
            }
        }).build()
    }
    single<ToafDao> { get<ToafDatabase>().toafDao()}
    single{ Database(get()) }
    single<IWifiManager> { DesktopWifiManager() }
    single<ISerialDevice> { DesktopSerialDevice() }
    factory<IToafBilling>  { DesktopToafBilling() }
    factory<IToafRewardAds> { DesktopToafRewardAds() }
    factory<IFileSupport> { DesktopFileSupport() }
    factory<IUsbBroadcastHandler> { DesktopUsbBroadcastHandler() }
    single<IUsbEventManager>{ DesktopUsbEventManager(get(),get(),get()) }
}