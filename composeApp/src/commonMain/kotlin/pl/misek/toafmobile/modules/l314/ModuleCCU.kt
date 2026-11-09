package pl.misek.toafmobile.modules.l314

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWPStar
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_bcu
import toafmobile.composeapp.generated.resources.module_description_ccu
import kotlin.time.Duration.Companion.milliseconds

class ModuleCCU(private var kwp: ProtocolKWPStar) : IModule, IModuleControl, IModuleVIN,
    IModuleCoding, IModuleLiveData {
    private val tag = "ModuleCCU"

    private val MODULE_ADDRESS = ModuleAddress.MODULE_CCU.address

    private var localLiveDataElements : List<LiveDataElement> = emptyList()

    override fun getModuleProtocol() = kwp

    override fun getKoinName() = KoinNames.ModuleL20Ccu

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolKWPStar
    }

    private suspend fun moduleInit(): Boolean {
        if (testerPresent())
            return true
        repeat(5) {
            val result = kwp.write(MODULE_ADDRESS, 0x81.toByte(), byteArrayOf(), 50).isValid
            if (result)
                return true
            else
                delay(1000.milliseconds)
        }
        return false
    }

    private suspend fun closeConnection(): Boolean {
        return kwp.write(MODULE_ADDRESS, 0x82.toByte(), byteArrayOf()).isValid
    }

    override fun getModuleName(): String {
        return "CCU"
    }

    override fun getModuleAddress(): ModuleAddress {
        return ModuleAddress.MODULE_CCU
    }

    override fun getImageResource() = Res.drawable.module_bcu

    override fun getModuleDescription() = Res.string.module_description_ccu

    override suspend fun getIdentification(): ModuleInfo? {

        var info: ModuleInfo? = null
        try {
            if (moduleInit()) {
                val message = kwp.write(MODULE_ADDRESS, 0x1A.toByte(), byteArrayOf(), 50)
                if (message.isValid) {
                    try {
                        info = ModuleInfo()
                        val answer = message.answer
                        info.id1 = String(answer.copyOfRange(5, 7))
                        info.ci = String.format("%02X", answer[7].toInt() and 0xFF)
                        // SW_VER wg SGBD: bajt1 * 100 + bajt2
                        info.sw = ((answer[9].toInt() and 0xFF) * 100 + (answer[10].toInt() and 0xFF)).toString()
                        info.hw = String(answer.copyOfRange(11, 12))
                        info.serial = String.format(
                            "%02X%02X%02X",
                            answer[13].toInt() and 0xFF,
                            answer[14].toInt() and 0xFF,
                            answer[15].toInt() and 0xFF
                        )
                    }
                    catch (ex: CancellationException) {
                        throw ex
                    }
                    catch (ex : Exception)
                    {
                        Logger.e {"$tag : $ex, $message"}
                    }
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e{"$tag : $ex"}
        }

        return info
    }




    override suspend fun getVIN(): String? {
        if (moduleInit()) {
            val message = kwp.write(MODULE_ADDRESS, 0x21.toByte(), byteArrayOf(0x15.toByte()), 50)
            if (message.isValid) {
                try {
                    var vin = (message.answer[6].toInt() and 0x0F) shl 16
                    vin += (message.answer[7].toInt() and 0xFF) shl 8
                    vin += message.answer[8].toInt() and 0xFF
                    return vin.toString()
                }
                catch (ex: CancellationException) {
                    throw ex
                }
                catch (ex : Exception)
                {
                    Logger.e {"$tag: $ex, $message"}
                }
            }
        }
        return null
    }

    override fun getVinLength(): Int = 17

    override suspend fun writeVIN(newVin: String): Boolean {
        var result = false
        if (newVin.length != getVinLength()) return false
        val serial = newVin.substring(11).toIntOrNull() ?: return false
        if (moduleInit() && securityAccess(0x01)) {
            val vinData = (if (newVin[6] == 'B') 0x500000 else 0x300000) + serial
            val data = byteArrayOf(0x15, ((vinData shr 16) and 0xFF).toByte(), ((vinData shr 8) and 0xFF).toByte(),(vinData and 0xFF).toByte())
            result = kwp.write(MODULE_ADDRESS, 0x3B.toByte(), data, 50).isValid
        }
        return result
    }

    override suspend fun getOdometer(): String? {
        return null
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        return null
    }

    override suspend fun clearAllErrors(): Boolean {
        return false
    }

    override suspend fun reset(): Boolean {
        return kwp.write(MODULE_ADDRESS, 0x11.toByte(), byteArrayOf(0x01), 50).isValid
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        if (moduleInit()) {
            securityAccess(0x01)
            val data = when (controlElement.block)
            {
                0x00 -> byteArrayOf(0x0F, 0x00, 0x02)
                0x01 -> byteArrayOf(0x1C, 0x04)
                0x02 -> byteArrayOf(0x0F, 0x04, 0x11)
                0x03 -> byteArrayOf(0x0B, 0x00, 0x00, 0x00, 0x00 ,0x00, 0x00 ,0x00, 0x00, 0x00, 0x04)
                0x04 -> byteArrayOf(0x0B, 0x00, 0x00, 0x00, 0x00 ,0x00, 0x00 ,0x00, 0x00, 0x00, 0x02)
                0x05 -> byteArrayOf(0x0B, 0x04, 0x00, 0x00, 0x00 ,0x00, 0x00 ,0x00, 0x00, 0x00, 0x11)
                0x06 -> byteArrayOf(0x0B, 0x02, 0x00, 0x00, 0x00 ,0x00, 0x00 ,0x00, 0x00, 0x00, 0x11)
                0x07 -> byteArrayOf(0x0B, 0x00, 0x00, 0x00, 0x04 ,0x00, 0x00 ,0x00, 0x00, 0x00, 0x11)
                0x08 -> byteArrayOf(0x0B, 0x00, 0x00, 0x00, 0x02 ,0x00, 0x00 ,0x00, 0x00, 0x00, 0x11)
                0x09 -> byteArrayOf(0x0B, 0x00, 0x00, 0x04, 0x00 ,0x00, 0x00 ,0x00, 0x00, 0x00, 0x11)
                0x0A -> byteArrayOf(0x0B, 0x00, 0x00, 0x0A, 0x00 ,0x00, 0x00 ,0x00, 0x00, 0x00, 0x11)
                0x0B -> byteArrayOf(0x0B, 0x00, 0x00, 0x00, 0x00 ,0x04, 0x00 ,0x00, 0x00, 0x00, 0x11)
                0x0C -> byteArrayOf(0x1A, 0x04)
                0x0D -> byteArrayOf(0x1A, 0x02)
                0x0E -> byteArrayOf(0x0B, 0x00, 0x00, 0x00, 0x00 ,0x00, 0x00 ,0x04, 0x00, 0x00, 0x11)
                0x0F -> byteArrayOf(0x0B, 0x00, 0x00, 0x00, 0x00 ,0x00, 0x04 ,0x00, 0x00, 0x00, 0x11)
                0x10 -> byteArrayOf(0x0B, 0x00, 0x00, 0x00, 0x00 ,0x00, 0x02 ,0x00, 0x00, 0x00, 0x11)
                0x11 -> byteArrayOf(0x45, 0x04)
                0x12 -> byteArrayOf(0x0D, 0x04, 0x00, 0x00, 0x00, 0x00)
                0x13 -> byteArrayOf(0x0D, 0x00, 0x00, 0x04, 0x00, 0x00)
                0x14 -> byteArrayOf(0x0D, 0x00, 0x04, 0x00, 0x00, 0x00)
                0x15 -> byteArrayOf(0x0D, 0x00, 0x00, 0x00, 0x00, 0x04)
                0x16 -> byteArrayOf(0x1D, 0x04)
                0x17 -> byteArrayOf(0x0E, 0x04, 0x00, 0x00)
                0x18 -> byteArrayOf(0x0C, 0x00, 0x04, 0x00)
                0x19 -> byteArrayOf(0x0C, 0x00, 0x00, 0x04)
                0x1A -> byteArrayOf(0x0C, 0x04, 0x00, 0x00)
                0x1B -> byteArrayOf(0x0D, 0x00, 0x00, 0x00, 0x04, 0x00)
                else -> byteArrayOf()
            }
            return kwp.write(MODULE_ADDRESS, 0x3B.toByte(), data).isValid
        }
        return false
    }

    private suspend fun testerPresent(): Boolean {
        return kwp.write(MODULE_ADDRESS, 0x3E.toByte(), byteArrayOf(0x00, 0x00)).isValid
    }

    private suspend fun securityAccess(mode: Byte): Boolean {
        val message = kwp.write(MODULE_ADDRESS, 0x27.toByte(), byteArrayOf(mode), 50)
        if (message.isValid) {
            val code =
                ((message.answer[6].toInt() and 0xFF) shl 8) + (message.answer[7].toInt() and 0xFF)
            if (code == 0x0000) return true
            val seed = securityAccessCalculator(code)
            val data = byteArrayOf(
                (mode + 1).toByte(),
                0x00, 0x42, 0xEA.toByte(), 0xB7.toByte(),
                ((seed and 0xFF00) shr 8).toByte(),
                (seed and 0xFF).toByte()
            )
            return kwp.write(MODULE_ADDRESS, 0x27.toByte(), data, 50).isValid
        }
        return false
    }

    private suspend fun startDiagnosticSession(mode: Byte): Boolean {
        return kwp.write(MODULE_ADDRESS, 0x10.toByte(), byteArrayOf(mode), 50).isValid
    }

    private suspend fun stopDiagnosticSession(): Boolean {
        return kwp.write(MODULE_ADDRESS, 0x20.toByte(), byteArrayOf(), 50).isValid
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.L314)
    }

    private fun securityAccessCalculator(inKey: Int): Int {
        var key = inKey
        val n = (key and 0x0F) +1
        for (i in 0 until n)
        {
            val bit15 = (key ushr 15) and 1
            val bit6  = (key ushr 6) and 1
            val bit4  = (key ushr 4) and 1
            val bit2  = (key ushr 2) and 1
            val bit1  = (key ushr 1) and 1
            val bit0  = key and 1
            val newBit = bit15 xor bit6 xor bit4 xor bit2 xor bit1 xor bit0
            key = ((key shl 1) or newBit) and 0xFFFF
        }
        return key
    }

    override suspend fun readCoding(): List<ByteArray>? {
        if (securityAccess(0x01)) {
            val message = kwp.write(MODULE_ADDRESS, 0x21.toByte(), byteArrayOf(0x05), 300)
            if (message.isValid) {
                val result = mutableListOf<ByteArray>()
                result.add(message.answer.copyOfRange(6, message.answer.size - 1))
                return result
            }
        }
        return null
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        if (moduleInit()) {
            if (securityAccess(0x01)) {
                if (!kwp.write(
                    MODULE_ADDRESS,
                    0x3B.toByte(),
                    byteArrayOf(0x05) + dataToSave[0],
                    50
                ).isValid) return false

                val verify = kwp.write(MODULE_ADDRESS, 0x21.toByte(), byteArrayOf(0x05), 50)
                return verify.isValid &&
                        verify.answer.copyOfRange(6, verify.answer.size - 1).contentEquals(dataToSave[0])
            }
        }
        return false
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        // Jedno zapytanie 21 xx na blok
        for (block in localLiveDataElements.map { it.block }.distinct())
        {
            val message = kwp.write(MODULE_ADDRESS, 0x21.toByte(), byteArrayOf(block.toByte()), 50)
            if (message.isValid) {
                try {
                    result[block] = message.answer.copyOfRange(3, message.answer.size - 1)
                }
                catch (ex: CancellationException) {
                    throw ex
                }
                catch ( ex : Exception)
                {
                    Logger.e {"$tag : $ex, $message"}
                }
            }
        }
        return result.ifEmpty { null }
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        localLiveDataElements = liveDataElements
        return moduleInit() && securityAccess(0x01)
    }
}
