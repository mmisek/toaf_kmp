package pl.misek.toafmobile.koin

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
        val dbFile = File(System.getProperty("java.io.tmpdir"),"toaf.db")
        Room.databaseBuilder<ToafDatabase>(
            name = dbFile.absolutePath
        )
        .setDriver(BundledSQLiteDriver())
        .addMigrations(Migration1to2)
        .build()
    }
    single<ToafDao> { get<ToafDatabase>().toafDao()}
    single{ Database(get()) }
    single<IWifiManager> { DesktopWifiManager() }
    single<ISerialDevice> { DesktopSerialDevice() }
    factory<IToafBilling>  { DesktopToafBilling() }
    factory<IToafRewardAds> { DesktopToafRewardAds() }
    factory<IFileSupport> { DesktopFileSupport() }
    factory<IUsbBroadcastHandler> { DesktopUsbBroadcastHandler() }
    factory<IUsbEventManager>{ DesktopUsbEventManager(get(),get(),get()) }
}