package pl.misek.toafmobile.modules

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolDS2

open class ModuleDS2(
    protocolDS2: ProtocolDS2?,
    moduleAddress: ModuleAddress,
    supportedCarTypes: List<CarType>
) :
    IModule {
    private var ds2: ProtocolDS2? = null
    private val moduleAddress: ModuleAddress
    private val supportedCarTypes: List<CarType>

    init {
        if (protocolDS2 != null) {
            ds2 = protocolDS2
        }
        this.moduleAddress = moduleAddress
        this.supportedCarTypes = supportedCarTypes
    }

    override fun getKoinName(): KoinNames {
        TODO("Not yet implemented")
    }
    override suspend fun getIdentification(): ModuleInfo? {
        val mi = interfaceWrite(byteArrayOf(0x00)).let { ModulesFunction.getModuleInfo(it) }
        if (mi != null && mi.ci == "") {
            reset()
            delay(1000)
            return interfaceWrite(byteArrayOf(0x00)).let { ModulesFunction.getModuleInfo(it) }
        }
        return mi
    }

    override suspend fun getVIN() :String? = null

    override suspend fun getOdometer() :String? = null

    override suspend fun getAllErrors() :List<ModuleError>? = null

    override suspend fun clearAllErrors(): Boolean {
        getIdentification()
        return interfaceWrite(byteArrayOf(0x05), 500).isValid
    }

    override suspend fun reset(): Boolean {
        interfaceWrite(byteArrayOf(0x12))
        return true
    }

    override fun getModuleName() = moduleAddress.showName

    override fun getModuleAddress() : ModuleAddress = moduleAddress

    override fun getModuleProtocol() = ds2!!

    override fun getImageResource(): DrawableResource? = null

    override fun getModuleDescription() : StringResource? = null

    override fun setModuleProtocol(protocol: IProtocol) {
        ds2 = protocol as ProtocolDS2
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return supportedCarTypes
    }

    fun interfaceWriteWithAddress(address: Byte, data: ByteArray): OBDMessage {
        var result = OBDMessage()
        CoroutineScope(Dispatchers.IO).launch {
            result = async {ds2?.write(address, data)}.await()!!
        }
        return result

    }

    fun interfaceWriteWithAddress(address: Byte, data: ByteArray, timeout: Long?): OBDMessage {
        var result = OBDMessage()
        CoroutineScope(Dispatchers.IO).launch {
            result = async {ds2?.write(address, data, timeout)}.await()!!
        }
        return result
    }

    suspend fun interfaceWrite(data: ByteArray): OBDMessage {
         return ds2?.write(moduleAddress.address, data)!!
    }

    suspend fun interfaceWrite(data: ByteArray, timeout: Long?): OBDMessage {
        return ds2?.write(moduleAddress.address, data, timeout)!!
    }
    suspend fun interfaceWrite(data: Array<ByteArray>): OBDMessage {
        return ds2?.write(moduleAddress.address, data)!!
    }

    suspend fun interfaceWrite(command: Byte, data: ByteArray): OBDMessage {
        return ds2?.write(moduleAddress.address, command, data)!!
    }

    suspend fun interfaceWrite(command: Byte, data: ByteArray, timeout: Long?): OBDMessage {
        return ds2?.write(moduleAddress.address, command, data, timeout)!!
    }
}
