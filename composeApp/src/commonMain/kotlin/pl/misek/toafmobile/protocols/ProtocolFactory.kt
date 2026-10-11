package pl.misek.toafmobile.protocols

import co.touchlab.kermit.Logger
import pl.misek.toafmobile.interfaces.IInterfaceDevice

class ProtocolFactory
{
    private val tag = "ProtocolFactory"
    var currentDevice : IInterfaceDevice? = null
        private set
    val protocolDs2 = ProtocolDS2(null)
    val protocolKwp = ProtocolKWP(null,false)
    val protocolKwpStar = ProtocolKWPStar(null,false)
    val protocolDde = ProtocolDDE(null)
    val protocolEdc15 = ProtocolEDC15(null)
    val protocolKw1281 = ProtocolKW1281(null)

    fun updateProtocolFactory(newDevice : IInterfaceDevice?)
    {
        Logger.i {"$tag: New device : ${newDevice.toString()}"}
        currentDevice = newDevice
        protocolDs2.setDevice(newDevice)
        protocolKwp.setDevice(newDevice)
        protocolKwpStar.setDevice(newDevice)
        protocolDde.setDevice(newDevice)
        protocolEdc15.setDevice(newDevice)
        protocolKw1281.setDevice(newDevice)
    }
}
