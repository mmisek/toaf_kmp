package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.coding.ZcsCoding
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceCoding
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.modules.interfaces.IModuleZCS
import pl.misek.toafmobile.modules.interfaces.ITemperatureGauge
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_ipk
import toafmobile.composeapp.generated.resources.module_ipk

private const val tag = "ModuleIPK"
class ModuleIPK(protocolDS2: ProtocolDS2?) :
    ModuleDS2( protocolDS2, ModuleAddress.MODULE_IPK, listOf(CarType.R40)),
    IModuleZCS, IModuleVIN, IModuleControl, IModuleCoding, IModuleAdvanceCoding,
    IModuleEepromProgramming, ITemperatureGauge {

    enum class ServiceType {
        Oil,
        Time,
        Sia,
        All
    }
    override fun getKoinName() = KoinNames.ModuleR40Ipk

    override fun getImageResource() = Res.drawable.module_ipk

    override fun getModuleDescription() = Res.string.module_description_ipk

    override suspend fun readZCS(): ZcsCoding? {
        var result: ZcsCoding? = null
        val message = interfaceWrite(byteArrayOf(0x02, 0x04))
        try {
            if (message.isValid) result =
                ZcsCoding(message.answer.copyOfRange(4, message.length))
            return result
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readZCS], $ex, $message"}
            return null
        }
    }

    override suspend fun writeZCS(data: ZcsCoding): Boolean {
        val dataToSave =
            byteArrayOf(
                0x07,
                0x03,
                0x00,
                0x00,
                (0x8C / 2).toByte(),
                (data.bytes.size / 2).toByte()) + data.bytes
        val message = interfaceWrite(dataToSave)
        return message.isValid
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        when (controlElement.block) {
            0 -> return interfaceWrite(byteArrayOf(0x30)).isValid
            1 -> {
                val data = byteArrayOf(0x0C, 0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                data[controlElement.byteInBlock + 2] = controlElement.value.toByte()
                return interfaceWrite(data).isValid
            }

            3 -> {
                val data3 = byteArrayOf(
                    0x0C,
                    controlElement.value.toByte(),
                    controlElement.controlSetValue!!.value.toByte()
                )
                return interfaceWrite(data3).isValid
            }

            4 -> {
                val data4 = byteArrayOf(0x0C, 0x0F, controlElement.value.toByte())
                return interfaceWrite(data4).isValid
            }

            5 -> {
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xDA.toByte(),0xDA.toByte(),0xDA.toByte(),0xDA.toByte(),0xDA.toByte(),0xDA.toByte(),0x00,0x00,0x00,0x00,0x00))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xDA.toByte(),0xDA.toByte(),0xDA.toByte(),0xDA.toByte(),0x00)                )
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x00,0x00,0xDA.toByte(),0xDA.toByte(),0xDA.toByte(),0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x67,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x67,0x10,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0xB6.toByte(),0x10,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x78,0x0B,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x00,0x09,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x6D,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x79,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x03,0x0B,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x6F,0x2D,0x67,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x10))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x40))
                delay(1000)
                interfaceWrite(byteArrayOf(0x13,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x20))
                delay(1000)
            }

            6 -> return serviceIntervalReset(ServiceType.All)

            else -> {}
        }
        return false
    }


    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        val data = ByteArray(256)
        for (i in 0..15) {
            val message = interfaceWrite(byteArrayOf(0x06, 0x03, 0x00, 0x00, (i * 8).toByte(), 0x08))
            try {
                if (message.isValid) {
                    if (message.answer.size - 4 >= 0) System.arraycopy(
                        message.answer,
                        3,
                        data,
                        16 * i,
                        message.answer.size - 4
                    )
                } else {
                    return null
                }
            } catch (ex : Exception)
            {
                Logger.e {"$tag [readCoding], $ex, $message"}
            }
        }
        result.add(data)
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        val dataToSaveWithChecksum = calculateEepromChecksum(dataToSave)
        for (i in 0 until dataToSaveWithChecksum[0].size / 8) {
            val data =
                byteArrayOf(0x07, 0x03, 0x00, 0x00, ((i * 8) / 2).toByte(), 0x04) +
                dataToSaveWithChecksum[0].copyOfRange( i * 8, 8 * (i + 1))
            interfaceWrite(data)
        }
        return reset()
    }

    private fun calculateEepromChecksum(dataToSave: List<ByteArray>): List<ByteArray> {
        var crcStart = 0x5A
        var crc: Byte = 0
        try {
            do {
                crc = (crc.toInt() xor dataToSave[0][crcStart++].toInt()).toByte()
            } while (crcStart < dataToSave[0].size)
            dataToSave[0][0x59] = crc
            return dataToSave
        } catch (ex: Exception) {
            return dataToSave
        }
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        val message = interfaceWrite(byteArrayOf(0x04, 0x01))
        try {
            if (message.isValid) {
                for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                    val errorCode = String.format("%02X", message.answer[4 + 3 * i])
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[6 + 3 * i].toInt() and 0xFF)))
                    for (j in 0..6) {
                        val x = (message.answer[5 + 3 * i].toInt() and (1 shl j))
                        if (x == (1 shl j)) {
                            ResourceMapper
                                .stringResource(String.format("ipk_error_desc_%02X", x))
                                ?.let { errorDescription.add(Pair(it, null)) }
                        }
                    }
                    if (result == null) result = ArrayList()
                    result.add(ModuleError(ModuleAddress.MODULE_IPK, errorCode, errorDescription))
                }
            }
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getAllErrors], $ex, $message"}
        }

        return result
    }

    override suspend fun getVIN(): String? {
        val message = interfaceWrite(byteArrayOf(0x02, 0x02))
        try {
            return if (message.isValid) {
                String.format(
                    "%c%c%02X%02X%02X",
                    message.answer[4],
                    message.answer[5],
                    message.answer[6],
                    message.answer[7],
                    message.answer[8]
                )
            } else null
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getVIN], $ex, $message"}
            return null
        }
    }

    override fun getVinLength(): Int = 7

    override suspend fun writeVIN(newVin: String): Boolean {
        val vinBytes = newVin.toByteArray()
        val vinData = byteArrayOf(vinBytes[0], vinBytes[1], 0x00, 0x00, 0x00)
        for (i in 0..2) {
            val x = newVin.substring(2 + 2 * i, 4 + 2 * i).toInt(16)
            vinData[i + 2] = x.toByte()
        }
        return interfaceWrite(byteArrayOf(0x03) + vinData).isValid
    }

    override suspend fun getOdometer(): String? {
        val message = interfaceWrite(byteArrayOf(0x02, 0x01))
        try {
            return if (message.isValid) {
                String.format(
                    "%02X%02X%02X",
                    message.answer[4], message.answer[5], message.answer[6]
                )
            } else null
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getOdometer],$ex, $message"}
            return null
        }
    }

    private suspend fun serviceIntervalReset(serviceType: ServiceType): Boolean {
        if (serviceType == ServiceType.All) {
            for (i in 0..2) {
                if (!interfaceWrite(byteArrayOf(0x0C, 0x12, (2 * i).toByte())).isValid) return false
            }
            return true
        } else {
            return when (serviceType) {
                ServiceType.Oil -> interfaceWrite(byteArrayOf(0x0C, 0x12, 0x01)).isValid
                ServiceType.Time -> interfaceWrite(byteArrayOf(0x0C, 0x12, 0x02)).isValid
                ServiceType.Sia ->  interfaceWrite(byteArrayOf(0x0C, 0x12, 0x04)).isValid
            }
        }
    }

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        val adr = (address / 2).toByte()
        val message = interfaceWrite(byteArrayOf(0x06, 0x03, 0x00, 0x00, adr, (size / 2).toByte()))
        try {
            return if (message.isValid) message.answer.copyOfRange(0x03, 0x03 + size) else null
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readEeprom], $ex, $message"}
            return null
        }
    }

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val adr = (address / 2).toByte()
        val dataToWrite =
            byteArrayOf(0x07, 0x03, 0x00, 0x00, adr, (data.size / 2).toByte()) +
            data
        return interfaceWrite(dataToWrite).isValid
    }

    override fun getEepromSize(): Int {
        return 256
    }

    override fun getEepromReadSize(): Int {
        return 16
    }

    override fun getEepromWriteSize(): Int {
        return 16
    }
}
