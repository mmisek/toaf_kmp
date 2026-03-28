package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.misek.toafmobile.interfaces.IKWPInterfaceDevice
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceCoding
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWPStar
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

class ModuleSRS2( private val protocolKWPStar: ProtocolKWPStar?) : IModule, IModuleCoding, IModuleVIN,
    IModuleAdvanceCoding {
    private val tag = "ModuleSRS2"
    private var kwp: ProtocolKWPStar? = null
    private val moduleAddress = ModuleAddress.MODULE_SRS.address
    private var lastMessageTime = 0L
    private var testerPresentJob : Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var testerPresentJobSuspend = false

    init {
        if (protocolKWPStar != null) kwp = protocolKWPStar
        lastMessageTime = 0L
    }
    override fun getKoinName() = KoinNames.ModuleR40Srs2

    override fun getModuleProtocol() = kwp!!

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolKWPStar
    }

    private suspend fun testerPresent(): Boolean {
        var testerPresentResult = true
        if (lastMessageTime == 0L) {
            (protocolKWPStar?.getDevice() as? IKWPInterfaceDevice)?.kwpFastInit()
            testerPresentResult =
                kwp!!.write(moduleAddress, 0x81.toByte(), byteArrayOf(), 200).isValid
        }

        if (lastMessageTime == 0L && testerPresentResult) {
            lastMessageTime = System.currentTimeMillis()
            testerPresentJob?.cancel()
            testerPresentJob = scope.launch {
                while (System.currentTimeMillis() - lastMessageTime < 30_000) {
                    delay(3000)
                    if (!testerPresentJobSuspend) {
                        kwp!!.write(moduleAddress, 0x81.toByte(), byteArrayOf(), 300)
                    }
                }
                lastMessageTime = 0L
            }
        }
        else
        {
            //lastMessageTime = 0L
        }
        return testerPresentResult
    }

    override fun getModuleName() = "SRS"

    override fun getModuleAddress() =  ModuleAddress.MODULE_SRS2

    override fun getImageResource() = Res.drawable.module_srs

    override fun getModuleDescription() = Res.string.module_description_srs2

    override suspend fun getIdentification(): ModuleInfo? {
        var info : ModuleInfo? = null
        if (testerPresent())
        {
            testerPresentJobSuspend = true
            val message =
                kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x80.toByte()), 200)
            if (message.isValid) {
                info = ModuleInfo()
                info.id1 = "YWC001160"
                info.hw = String(message.answer.copyOfRange( 5, 10))
                info.ci = "02"
                info.di = (message.answer[18].toInt() and 0xFF).toByte().toHexString()
                info.producer = "Autoliv"
            }
            val seriaNumber = getSerialNumber()
            if (seriaNumber.isNotEmpty())
            {
                if (info == null)
                    info = ModuleInfo()
                info.serial = getSerialNumber()
            }
            testerPresentJobSuspend = false
            lastMessageTime = System.currentTimeMillis()
        }
        return info
    }

    private suspend fun getSerialNumber(): String {
        var result = ""
        testerPresentJobSuspend = true
        val message = kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x81.toByte()), 200)
        if (message.isValid) {
            result = String.format(
                "%02x%02X%02X%02X",
                message.answer[5].toInt() and 0xFF,
                message.answer[6].toInt() and 0xFF,
                message.answer[7].toInt() and 0xFF,
                message.answer[8].toInt() and 0xFF
            )
        }
        lastMessageTime = System.currentTimeMillis()
        testerPresentJobSuspend = false
        return result
    }

    override suspend fun getVIN(): String? {
        var result : String? = null
        testerPresentJobSuspend = true
        if (!testerPresent()) return null
        val message =
            kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x8F.toByte()), 200)
        if (message.isValid) {
            result =  String(message.answer.copyOfRange(5, 22))
            lastMessageTime = System.currentTimeMillis()
        }

        testerPresentJobSuspend = false
        return result
    }

    override suspend fun getOdometer(): String? {
        return null
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        testerPresentJobSuspend = true
        if (!testerPresent()) return null
        val message =
            kwp!!.write(moduleAddress, 0x17.toByte(), byteArrayOf(0xFF.toByte(), 0x00), 200)
        if (message.isValid) {
            result = ArrayList()
            var n = message.answer[4].toInt()
            if (n > 12) n = 12
            for (i in 0 until n) {
                val errorCode =
                    ((message.answer[5 + 2 * i].toInt() and 0x0F) shl 8) + (message.answer[6 + 2 * i].toInt() and 0xFF)
                val error = ModuleError(
                    ModuleAddress.MODULE_SRS2,
                    String.format("%03X", errorCode),
                    ArrayList()
                )
                if (error.description != null) {
                    result.add(error)
                }
            }
        }
        testerPresentJobSuspend = false
        lastMessageTime = System.currentTimeMillis()
        return result
    }

    override suspend fun clearAllErrors(): Boolean {
        testerPresentJobSuspend = true
        if (!testerPresent()) return false
        val result = kwp!!.write(
            moduleAddress,
            0x14.toByte(),
            byteArrayOf(0xFF.toByte(), 0x00),
            200
        ).isValid
        testerPresentJobSuspend = false
        lastMessageTime = System.currentTimeMillis()
        return result
    }

    override suspend fun reset(): Boolean {
        lastMessageTime = System.currentTimeMillis()
        return kwp!!.write(moduleAddress, 0x11.toByte(), byteArrayOf(0x01)).isValid
    }

    override suspend fun readCoding(): List<ByteArray>? {
        var result: MutableList<ByteArray>? = null
        testerPresentJobSuspend = true
        if (!testerPresent()) return null
        delay(200)
        if (securityAccess()) {
            testerPresentJobSuspend = false
            return null
        }
        delay(200)
        val message =
            kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0xD0.toByte()), 200)
        testerPresentJobSuspend = false
        if (message.isValid) {
            result = ArrayList()
            result.add(message.answer.copyOfRange(5, 10))
            lastMessageTime = System.currentTimeMillis()
        }
        lastMessageTime = System.currentTimeMillis()
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        lastMessageTime = System.currentTimeMillis()
        testerPresentJobSuspend = true
        if (!testerPresent()) return false
        if (!securityAccess()){
            testerPresentJobSuspend = false
            return false
        }
        val data = byteArrayOf(0xD0.toByte()) + dataToSave[0]
        val result =  kwp!!.write(moduleAddress, 0x3B.toByte(), data, 200).isValid
        testerPresentJobSuspend = false
        return result
    }

    private suspend fun securityAccess(): Boolean {
        var result = false
        val message = kwp!!.write(moduleAddress, 0x27.toByte(), byteArrayOf(), 200)
        if (message.isValid) {
            val code =
                ((message.answer[4].toInt() and 0xFF) shl 8) + (message.answer[5].toInt() and 0xFF)
            if (code == 0x0000) return true
            val seed = securityAccessCalculator(code)
            val data = byteArrayOf(((seed and 0xFF00) shr 8).toByte(), (seed and 0xFF).toByte())
            result = kwp!!.write(moduleAddress, 0x27.toByte(), data, 200).isValid
        }
        lastMessageTime = System.currentTimeMillis()

        return result
    }

    private fun securityAccessCalculator(inKey: Int): Int {
        var key = inKey
        val n = ((((key and 0xFF00) shr 8) xor 0x53) and 0x1E) + 4
        for (i in 0 until n) {
            var x = 0
            if ((key and 0x0800) == 0x0800) x = x xor 1
            if ((key and 0x0020) == 0x0020) x = x xor 1
            if ((key and 0x0002) == 0x0002) x = x xor 1
            key = key shl 1
            key = key and 0xFFFE
            key = key xor x
        }
        return key
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.R40)
    }

    override fun getVinLength(): Int = 17

    override suspend fun writeVIN(newVin: String): Boolean {
        testerPresentJobSuspend = true
        if (!testerPresent()) return false
        if (!securityAccess()) {
            testerPresentJobSuspend = false
            return false
        }
        delay(200)
        val result = kwp!!.write(
            moduleAddress,
            0x3B.toByte(),
            byteArrayOf(0x8F.toByte()) + newVin.toByteArray()
        ).isValid
        testerPresentJobSuspend = false
        lastMessageTime = System.currentTimeMillis()
        return result
    }
}
