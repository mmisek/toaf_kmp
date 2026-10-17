package pl.misek.toafmobile.activities

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import co.touchlab.kermit.Logger
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.database.DatabaseSettingFields
import pl.misek.toafmobile.interfaces.IInterfaceDevice
import pl.misek.toafmobile.interfaces.IUsbEventManager
import pl.misek.toafmobile.interfaces.ToafWifiEventManager
import pl.misek.toafmobile.interfaces.UsbDeviceInfo
import pl.misek.toafmobile.interfaces.wifi.ToafWifiInterface
import pl.misek.toafmobile.protocols.ProtocolFactory
import pl.misek.toafmobile.serial.AndroidSerialDevice
import pl.misek.toafmobile.tools.ActivityProvider
import pl.misek.toafmobile.tools.hideNavigationBar

class AndroidActivityMain : FragmentActivity() {
    private val tag = "ActivityMain"

    private var database: Database? = null
    private var protocolFactory: ProtocolFactory? = null
    var device: IInterfaceDevice? = null
    private lateinit var usbEventViewModel : IUsbEventManager
    private lateinit var toafWifiEventManager: ToafWifiEventManager
    // położenie logo na systemowym splash screenie, od którego startuje animacja SplashOverlay
    private val systemSplashLogoBounds = mutableStateOf<Rect?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setOnExitAnimationListener { splashScreenViewProvider ->
            val iconView = splashScreenViewProvider.iconView
            val location = IntArray(2)
            iconView.getLocationInWindow(location)
            systemSplashLogoBounds.value = Rect(
                center = Offset(location[0] + iconView.width / 2f, location[1] + iconView.height / 2f),
                radius = iconView.width * SYSTEM_SPLASH_LOGO_FRACTION / 2f
            )
            // usunięcie bez przenikania, dopiero gdy SplashOverlay narysuje logo w tym samym miejscu
            val splashView = splashScreenViewProvider.view
            splashView.postOnAnimation { splashView.postOnAnimation { splashScreenViewProvider.remove() } }
        }
        super.onCreate(savedInstanceState)
        ActivityProvider.currentActivity = this
        protocolFactory = get<ProtocolFactory>()
        database = get<Database>()
        usbEventViewModel = get<IUsbEventManager>()
        toafWifiEventManager = get<ToafWifiEventManager>()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        CoroutineScope(Dispatchers.IO).launch {
            val lastDeviceType = database?.getStringSettingValueByName(DatabaseSettingFields.LAST_DEVICE_TYPE)
            if (lastDeviceType == "WIFI")
            {
                val ip = database?.getStringSettingValueByName(DatabaseSettingFields.WIFI_DEVICE_IP) ?:""
                toafWifiEventManager.startMonitoring(ipAddress = ip)

            }
        }
        checkIntentForUsbDevices(intent)
        setContent {
            var showSplash by rememberSaveable { mutableStateOf(true) }
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = ToafColors.toafBackground
                )
                { innerPadding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                    ) {
                        AppNavigation({})
                    }
                }
                if (showSplash) {
                    SplashOverlay(
                        systemLogoBounds = systemSplashLogoBounds.value,
                        onFinished = { showSplash = false }
                    )
                }
            }
        }
        hideNavigationBar(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        checkIntentForUsbDevices(intent)
    }

    private fun checkIntentForUsbDevices(intent : Intent)
    {
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED)
        {
            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (device != null)
            {
                val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
                usbEventViewModel.handeConnectedDevice(device = device.getUsbDeviceInfo(), driver = driver)
            }
        }
    }

    private fun checkForAlreadyConnectedDevices(){
        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val connectedDevices = usbManager.deviceList
        for (device in connectedDevices.values){
            val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            if (driver != null){
                if (usbManager.hasPermission(device)){
                    usbEventViewModel.handeConnectedDevice(device = device.getUsbDeviceInfo(), driver = driver)
                }
                else
                    Log.d(tag, "Device found: ${device}, no permission")
                break
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //unregisterReceiver(usbReceiver)
    }

    override fun onResume() {
        super.onResume()
        hideNavigationBar(this)
        checkForAlreadyConnectedDevices()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideNavigationBar(this)
    }


    suspend fun connectWithDevice()
    {
        val interfaceType = withContext(Dispatchers.IO){database?.getStringSettingValueByName(DatabaseSettingFields.LAST_DEVICE_TYPE) }
        if (interfaceType == "WIFI")
        {
            val ipAddress = withContext(Dispatchers.IO){database?.getStringSettingValueByName(DatabaseSettingFields.WIFI_DEVICE_IP) ?: "" }
            val twi = ToafWifiInterface(ipAddress)
            val deviceInfo = withContext(Dispatchers.IO) {twi.getDeviceInfo()}
            device = twi
        }
        else
        {
            val usbManager =  getSystemService(USB_SERVICE) as UsbManager
            val usbDevices = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (usbDevices.isNotEmpty())
            {
                device = AndroidSerialDevice(usbManager,)
                device?.connect("")
            }
            else
            {
                device = null
            }
        }
        protocolFactory?.updateProtocolFactory(device)
    }

    fun UsbDevice.getUsbDeviceInfo() : UsbDeviceInfo
    {
        val serial = try { this.serialNumber } catch (ex: SecurityException) {
            Logger.w{"$tag: no permission to read serialNumber for ${this.deviceName}: $ex"}
            null
        }
        val manufacturer = try { this.manufacturerName } catch (ex: SecurityException) {
            Logger.w{"$tag: no permission to read manufacturerName for ${this.deviceName}: $ex"}
            null
        }
        return UsbDeviceInfo(
            serialNumber = serial,
            name = this.deviceName,
            productId = this.productId.toString(),
            vendorID = this.vendorId.toString(),
            manufacturer = manufacturer,
            version = this.version
        )
    }

    companion object {
        // logo w drawable/splash_logo.xml jest wcięte o 48dp z 288dp, więc zajmuje 2/3 ikony
        private const val SYSTEM_SPLASH_LOGO_FRACTION = 192f / 288f
    }
}