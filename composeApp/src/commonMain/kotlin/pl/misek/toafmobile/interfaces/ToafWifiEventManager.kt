package pl.misek.toafmobile.interfaces

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.misek.toafmobile.interfaces.wifi.ToafWifiDeviceInfo
import pl.misek.toafmobile.interfaces.wifi.ToafWifiInterface

class ToafWifiEventManager(private val wifiManager: ToafWifiManager) : DefaultLifecycleObserver
{
    private val tag = "ToafWifiEventManager"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _wifiEvent = MutableStateFlow<WifiEvent>(WifiEvent.Idle)
    val wifiEvent = _wifiEvent.asStateFlow()
    private var connectionCheckJob: Job? = null
    init {
        startObserving()
    }

    fun startObserving() {
        wifiManager.wifiState.onEach { event ->
            when(event)
            {
                is WifiEvent.Idle -> {}
                is WifiEvent.Selected -> {
                    connectWithTwi(event.wifiDevice)
                }
                else -> {}
            }
        }.launchIn(scope)
    }

    fun startMonitoring(ipAddress : String)
    {
        val twi = ToafWifiInterface(ipAddress = ipAddress)
        val device = ToafWifiDeviceInfo(ipAddress =  ipAddress)
        startMonitoring(twi, device)
    }

    private fun connectWithTwi(deviceInfo : ToafWifiDeviceInfo)
    {
        val twi = ToafWifiInterface(deviceInfo.ipAddress ?: "")
        CoroutineScope(Dispatchers.IO).launch{
            println("$tag : Checking connection with ip ${deviceInfo.ipAddress}")
            val di = twi.getDeviceInfo()
            if (di != null) {
                _wifiEvent.value = WifiEvent.Connected(di)
                startMonitoring(twi, di)
            }
            else {
                //stopMonitoring()
                _wifiEvent.value = WifiEvent.Disconnected(deviceInfo)
            }
        }
    }
    private fun startMonitoring(twi : ToafWifiInterface, deviceInfo : ToafWifiDeviceInfo)
    {
        connectionCheckJob?.cancel()
        connectionCheckJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val di = twi.getDeviceInfo()
                if (di == null) {
                    _wifiEvent.value = WifiEvent.Disconnected(deviceInfo)
                }
                else
                {
                    _wifiEvent.value = WifiEvent.Connected(deviceInfo)
                }
                delay(10000)
            }
        }
    }
    private fun stopMonitoring() {
        connectionCheckJob?.cancel()
        connectionCheckJob = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        scope.cancel()
        super.onDestroy(owner)
    }
}