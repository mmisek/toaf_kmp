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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
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
    override fun onCreate(savedInstanceState: Bundle?) {
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
        //setAdView()
        //MobileAds.initialize(
        //    this
        //) { }
        //toafAds = ToafRewardAds(this)
        checkIntentForUsbDevices(intent)
        setContent {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = ToafColors.toafBackground
            )
            { innerPadding ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    AppNavigation()
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
    /*
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding!!.root)
        binding!!.activityMainButtonDiagnostic.setOnClickListener { openDiagnosticModule() }
        Companion.fragmentManager = supportFragmentManager
        setSupportActionBar(binding!!.activityMainToolbar)
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayShowHomeEnabled(false)
            supportActionBar!!.setDisplayHomeAsUpEnabled(false)
            supportActionBar!!.subtitle = BuildConfig.VERSION_NAME
        }
        val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        binding!!.activityMainFrameControl.addView(layoutInflater.inflate(R.layout.modules, null))
        binding!!.activityMainFrameLiveData.addView(layoutInflater.inflate(R.layout.modules, null))

        runOnUiThread { initModules(this@ActivityMain, null) }
        enableUSBFilters()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setAdView()
        MobileAds.initialize(
            this
        ) { }
        toafAds = ToafRewardAds(this)
        //var parser = new JSONSupportKotlin();
        //parser.getControlElements("EHC");
        //AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("es"));
    }
    */


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
            //interfaceConnectionState = if (deviceInfo != null) ConnectionState.WIFI_CONNECTED else ConnectionState.WIFI_DISCONNECTED
            device = twi
        }
        else
        {
            val usbManager =  getSystemService(USB_SERVICE) as UsbManager
            val usbDevices = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            if (usbDevices.isNotEmpty())
            {
                device = AndroidSerialDevice(usbManager, usbDevices[0])
                device?.connect("")
                //interfaceConnectionState =  if (device?.isConnected == true) ConnectionState.USB_CONNECTED else ConnectionState.USB_DISCONNECTED
            }
            else
            {
                device = null
                //interfaceConnectionState = ConnectionState.USB_DISCONNECTED
            }
        }
        protocolFactory?.updateProtocolFactory(device)
    }

    private fun checkInternetConnection(): Boolean {
        val connectivityManager = this.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun UsbDevice.getUsbDeviceInfo() : UsbDeviceInfo
    {
        return UsbDeviceInfo(
            serialNumber = this.serialNumber,
            name = this.deviceName,
            productId = this.productId.toString(),
            vendorID = this.vendorId.toString(),
            manufacturer = this.manufacturerName,
            version = this.version
        )
    }

}