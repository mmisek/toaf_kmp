package pl.misek.toafmobile.koin

import android.content.Context.USB_SERVICE
import android.hardware.usb.UsbManager
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import pl.misek.toafmobile.ads.AndroidToafRewardAds
import pl.misek.toafmobile.ads.IToafRewardAds
import pl.misek.toafmobile.billing.AndroidToafBilling
import pl.misek.toafmobile.billing.IToafBilling
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.ToafDatabase
import pl.misek.toafmobile.database.ToafDatabase_Impl
import pl.misek.toafmobile.database.dao.ToafDao
import pl.misek.toafmobile.interfaces.AndroidUsbBroadcastHandler
import pl.misek.toafmobile.interfaces.AndroidUsbEventManager
import pl.misek.toafmobile.interfaces.ISerialDevice
import pl.misek.toafmobile.interfaces.IUsbBroadcastHandler
import pl.misek.toafmobile.interfaces.IUsbEventManager
import pl.misek.toafmobile.network.AndroidWifiManager
import pl.misek.toafmobile.network.IWifiManager
import pl.misek.toafmobile.serial.AndroidSerialDevice
import pl.misek.toafmobile.tools.AndroidFileSupport
import pl.misek.toafmobile.tools.IFileSupport

actual fun platformModules(): Module = module {
    val usbManager =
    single(named(KoinNames.LiveDataLogFolder.name)) { androidContext().filesDir }
    single<ToafDatabase>{
        val dbFile = androidContext().getDatabasePath("toaf.db")
        Room.databaseBuilder<ToafDatabase>(
            context = androidContext(),
            name = dbFile.absolutePath
        )
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    single<ToafDao> {get<ToafDatabase>().toafDao()}
    single{ Database(get()) }
    single<IWifiManager> { AndroidWifiManager(get()) }
    single<UsbManager> { androidContext().getSystemService(USB_SERVICE) as UsbManager }
    factory<ISerialDevice> {
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(get())
        AndroidSerialDevice(get(),null) }
    single<IToafBilling> { AndroidToafBilling(androidContext(),get(),get()) }
    single<IToafRewardAds> { AndroidToafRewardAds(androidContext()) }
    single<IFileSupport> { AndroidFileSupport(androidContext()) }
    single<IUsbBroadcastHandler> { AndroidUsbBroadcastHandler(androidContext()) }
    single<IUsbEventManager> {
        AndroidUsbEventManager(usbBroadcastHandler = get(), usbManager = get(), database = get(), protocolFactory = get() ) }
}