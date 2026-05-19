package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Message
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_jatco
import toafmobile.composeapp.generated.resources.module_gearbox
private const val tag = "ModuleJATCO"
class ModuleJATCO(private var kwp: ProtocolKWP?) : IModule, IModuleControl, IModuleLiveData {
    private val moduleAddress = ModuleAddress.MODULE_JATCO.address
    private val liveDataBlocksToRead: MutableList<Byte> = ArrayList()

    override fun getKoinName() = KoinNames.ModuleR40Jatco

    override fun getModuleProtocol(): IProtocol {
        return kwp!!
    }

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolKWP
    }

    override fun getModuleName(): String {
        return ModuleAddress.MODULE_JATCO.showName
    }

    override fun getModuleAddress(): ModuleAddress {
        return ModuleAddress.MODULE_JATCO
    }

    override fun getImageResource() = Res.drawable.module_gearbox
    
    override fun getModuleDescription()  = Res.string.module_description_jatco

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        var message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x80.toByte()))
        try {
            if (message.isValid) {
                info = ModuleInfo()
                val answer = message.answer
                info.id1 = String(answer.copyOfRange(6, 9)) + answer.copyOfRange(9, 12)
                    .joinToString("") { "%02X".format(it) }
                info.hw = (answer[13].toInt() and 0xFF).toByte().toHexString()
                info.ci = (answer[14].toInt() and 0xFF).toString()
                info.di = (answer[15].toInt() and 0xFF).toByte().toString()
                info.bi = (answer[16].toInt() and 0xFF).toByte().toHexString()
                info.sw = (answer[22].toInt() and 0xFF).toByte().toHexString()
                info.producer = ModulesFunction.getProducer(answer[21].toInt())
                info.date = String.format(
                    "%02X/%02X/%02X%02X",
                    answer[20].toInt() and 0xFF,
                    answer[19].toInt() and 0xFF,
                    answer[17].toInt() and 0xFF,
                    answer[18].toInt() and 0xFF
                )
            }
            message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x87.toByte()))
            if (message.isValid && info != null) {
                info.id2 = String(message.answer.copyOfRange(26, 29)) +
                        message.answer.copyOfRange(29, 32).joinToString("") { "%02X".format((it)) }
            }
            if (info != null) {
                info.serial = getSerialNumber()
            }
            info?.let {
                var message = kwp!!.write(moduleAddress, 0x1A, byteArrayOf(0x92.toByte()))
                if (message.isValid)
                    it.additionalInfo.add(
                        Pair(
                            Res.string.module_description_jatco,
                            String(message.answer.copyOfRange(6, message.answer.size - 1))
                        )
                    )
                message = kwp!!.write(moduleAddress, 0x1A, byteArrayOf(0x94.toByte()))
                if (message.isValid)
                    it.additionalInfo.add(
                        Pair(
                            Res.string.module_description_jatco,
                            String(message.answer.copyOfRange(6, message.answer.size - 1))
                        )
                    )
                message = kwp!!.write(moduleAddress, 0x1A, byteArrayOf(0x89.toByte()))
                if (message.isValid)
                    it.additionalInfo.add(
                        Pair(
                            Res.string.module_description_jatco,
                            String(message.answer.copyOfRange(6, 9)) + message.answer.copyOfRange(
                                9,
                                12
                            ).joinToString("") { x -> "%02X".format(x) })
                    )
            }
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getIdentification], $ex, $message"}
        }
        return info
    }

    private suspend fun getSerialNumber(): String {
        val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x94.toByte()))
        try {
            if (message.isValid) {
                return String.format(
                    "%02X%02X",
                    message.answer[4].toInt() and 0xFF,
                    message.answer[5].toInt() and 0xFF
                )
            }
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getSErialNumber], $ex, $message"}
        }
        return ""
    }

    override suspend fun getVIN(): String? {
        val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x87.toByte()))
        try {
            if (message.isValid) return String(
                message.answer.copyOfRange(
                    6,
                    17
                )
            ) + message.answer.copyOfRange(18, 21).toHexString()
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getVIN], $ex,$message"}
        }

        return null
    }

    override suspend fun getOdometer(): String? {
        //val message = kwp!!.write(moduleAddress, 0xA2.toByte(), byteArrayOf())
        //if (message.isValid)

        /*
        for (x in 0x00.. 0xFF) {
            val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(x.toByte()))
            if (message.isValid)
        }*/
        return null
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        if (securityAccess(0x81,0x03)) {
            val message = kwp!!.write(moduleAddress, 0x18.toByte(), byteArrayOf(0x00, 0x00, 0x00), 500)
            try {
                if (message.isValid) {
                    result = mutableListOf()
                    for (i in 0 until (message.answer[5].toInt() and 0xFF)) {
                        val errorCode =
                            ((message.answer[6 + 3 * i].toInt() and 0xFF) shl 8) + (message.answer[7 + 3 * i].toInt() and 0xFF)
                        val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                        for (j in 0..7) {
                            if ((message.answer[8 + 3 * i].toInt() and (0x01 shl j)) == (0x01 shl j)) {
                                ResourceMapper.stringResource(
                                    String.format(
                                        "jatco_error_description_%02X",
                                        0x01 shl j
                                    )
                                )
                                    ?.let { errorDescription.add(Pair(it, null)) }
                            }
                        }
                        if ((message.answer[8 + 3 * i].toInt() and 0xFE) == 0xFE) {
                            ResourceMapper.stringResource("jatco_error_description_FE")
                                ?.let { errorDescription.add(Pair(it, null)) }
                        }
                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_JATCO,
                                String.format("%04X", errorCode),
                                errorDescription
                            )
                        )
                    }
                }
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getAllErros], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun clearAllErrors(): Boolean {
        if (securityAccess(0x81,0x03)) return kwp!!.write(
            moduleAddress,
            0x14.toByte(),
            byteArrayOf(0x00, 0x00)
        ).isValid
        return false
    }

    override suspend fun reset(): Boolean {
        return kwp!!.write(moduleAddress, 0x11.toByte(), byteArrayOf(0x01)).isValid
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (b in liveDataBlocksToRead) {
            val message = kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(b), 100)
            try {
                if (message.isValid)
                    result[b.toInt()] = message.answer.copyOfRange(6, message.length - 1)
                else return null
            } catch (ex : Exception)
            {
                Logger.e {"$tag [readLiveData], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        if (securityAccess(0x81,0x03)) {
            val message = kwp!!.write(
                moduleAddress,
                0x3B.toByte(),
                byteArrayOf(
                    0x0C.toByte(),
                    ((controlElement.value and 0xFF00) shr 8).toByte(),
                    (controlElement.value and 0xFF).toByte()
                )
            )
            return message.isValid
        }
        return false
    }

    private suspend fun testerPresent(): Boolean {
        return kwp!!.write(moduleAddress, 0x3E.toByte(), byteArrayOf()).isValid
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        liveDataBlocksToRead.clear()
        for (el in liveDataElements) liveDataBlocksToRead.add(el.block.toByte())
        return securityAccess(0x81,0x03)
    }
    private suspend fun switchToBoot() : Boolean
    {
        return kwp!!.write(moduleAddress,0x31.toByte(), byteArrayOf(0xFB.toByte())).isValid
    }
    private suspend fun securityAccess(diagnosticSession : Int, level : Int): Boolean {
        if (testerPresent()) {
            var message = kwp!!.write(moduleAddress, 0x10.toByte(), byteArrayOf(diagnosticSession.toByte()))
            if (message.isValid) {
                message = kwp!!.write(moduleAddress, 0x27.toByte(), byteArrayOf(level.toByte()), 1000)
                try{
                    if (message.isValid) {
                        val code =
                            ((message.answer[6].toInt() and 0xFF) shl 8) + (message.answer[7].toInt() and 0xFF)
                        if (code == 0x0000) return true
                        else {
                            val seed = securityAccessCalculator(level, code)
                            val data = byteArrayOf(
                                (level + 1).toByte(),
                                ((seed and 0xFF00) shr 8).toByte(), (seed and 0xFF).toByte()
                            )
                            return kwp!!.write(moduleAddress, 0x27.toByte(), data, 1000).isValid
                        }
                    }
                } catch (ex : Exception)
                {
                    Logger.e {"$tag [securityAccess], $ex, $message"}
                }
            }
        }
        return false
    }

    private fun securityAccessCalculator(level : Int, inKey: Int) :Int {
        var key = inKey
        val hiByte = (inKey shr 8) and 0xFF
        var n = when (level) {
            0x01 -> 2
            0x03 -> ((hiByte xor 0xFE) and 0x1D) + 2
            0x05 -> ((hiByte xor 0xFE) and 0x1D) + 3
            else -> 2
        }
        while (n > 0)
        {
            val b10 = (key shr 10) and 0x01
            val b5 = (key shr 5) and 0x01
            val b3 = (key shr 3) and 0x01
            val b1 = (key shr 1) and 0x01
            val b0 = key and 0x01
            val b = b10 xor b5 xor b3 xor b1 xor b0
            key = ((key shl 1) and 0xFFFF) or b
            n--
        }
        key = ((key shl 8) and 0xFF00) or ((key shr 8) and 0x00FF)
        return key
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.R40)
    }
}
