package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.containers.LWSAdjustmentData
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceCoding
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLWSCoding
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_lws
import toafmobile.composeapp.generated.resources.module_lws

class ModuleLWS( protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_LWS, listOf(CarType.L322)),
    IModuleAdvanceCoding, IModuleLiveData, IModuleEepromProgramming, IModuleVIN, IModuleLWSCoding {
    private val tag = "ModuleLWS"
    private val standardCoding = arrayOf(
        byteArrayOf(0x06,0x01,0x3F,0xC7.toByte(),0x6C,0x8B.toByte(),0x3A,0xEB.toByte(),0xED.toByte(),0xFA.toByte(),0x45,0x32,0x20,0x00,0x40,0x93.toByte()),
        byteArrayOf(0x33,0x33,0x3A,0xC0.toByte(),0xAD.toByte(),0x04,0x00,0x30,0x0F,0xA0.toByte(),0xFC.toByte(),0x18,0x4E,0x20,0x4E,0x20),
        byteArrayOf(0x0F,0x6B,0x25,0xAB.toByte(),0x00,0xC8.toByte(),0x00,0x64,0x00,0x20,0x00,0x60,0x14,0x00,0x73,0x0E),
        byteArrayOf(0x01,0x13,0x88.toByte(),0x45,0x53,0x0B,0xB8.toByte(),0x1E,0x15,0x15,0xAB.toByte(),0x8D.toByte(),0x02,0x02,0x10,0x00),
        byteArrayOf(0x3E,0xA8.toByte(),0x0A,0x0A,0x03,0x32,0x5A,0x64,0x64,0x50,0x8C.toByte(),0x50,0x64,0x64,0x64,0x3C),
        byteArrayOf(0x4B,0x0F,0x03,0x50,0xFE.toByte(),0xF0.toByte(),0xFE.toByte(),0xC8.toByte(),0xC8.toByte(),0x05,0x1E,0x55,0x7F,0xFF.toByte(),0xC8.toByte(),0xFF.toByte()),
        byteArrayOf(0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0xFF.toByte(),0x2E,0x2D)
    )
    override fun getKoinName() = KoinNames.ModuleL322Lws

    override fun getImageResource() = Res.drawable.module_lws

    override fun getModuleDescription()= Res.string.module_description_lws

    override suspend fun getVIN(): String? = readAdjustmentData()?.Vin

    override fun getVinLength(): Int = 17

    override suspend fun writeVIN(newVin: String): Boolean {
        val data = readAdjustmentData()
        if (data != null) {
            data.Vin = newVin
            return writeAdjustmentData(data)
        }
        return false
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        getIdentification()
        var result: MutableList<ModuleError>? = null
        val message = interfaceWrite(byteArrayOf(0x04, 0x01))
        try {
            if (message.isValid && message.length > 5) {
                result = ArrayList()
                for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                    val errorCode = String.format("%02X", message.answer[4 + 3 * i])
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5 + 3 * i].toInt() and 0xFF) and 0x1F))
                    errorDescription.add(ResourceHelper.getErrorDescriptionIsStatic((message.answer[5 + 3 * i].toInt() and 0x20) == 0x20))
                    result.add(ModuleError(ModuleAddress.MODULE_LWS, errorCode, errorDescription))
                }
            }
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [getAllErrors], $ex, $message"}
        }
        return result
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        getIdentification()
        return true
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        getIdentification()
        for (i in 0..2) {
            val message = interfaceWrite(byteArrayOf(0x1B, i.toByte()))
            try {
                if (message.isValid)
                    result[i] = message.answer.copyOfRange(3, message.answer.size)
                else
                    return null
            } catch ( ex : Exception)
            {
                Logger.e {"$tag [readLiveDAta], $ex, $message"}
            }
        }
        return result
    }

    suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()

        for (i in 0..0x6) {
            val message = interfaceWrite(byteArrayOf(0x08, i.toByte()))
            try {
                if (message.isValid)
                    result.add(message.answer.copyOfRange(4, message.answer.size - 1))
                else
                    return null
            } catch (ex : Exception)
            {
                Logger.e {"$tag [readCoding], $ex, $message"}
            }
        }
        return result
    }

    private suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        if (dataToSave.size != 0x07)
            return false
        for (i in dataToSave.indices) {
            val message = interfaceWrite(0x09, byteArrayOf(i.toByte()) + dataToSave[i])
            if (!message.isValid)
                return false
        }
        return true
    }

    override suspend fun writeDefaultCoding(): Boolean =  writeCoding(ArrayList(listOf(*standardCoding)))

    override fun getEepromSize(): Int  = 256

    override fun getEepromReadSize(): Int  = 0x10

    override fun getEepromWriteSize(): Int  = 0x10

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        val message = interfaceWrite(
        byteArrayOf(
            0x06,
            0x03,
            0x01,
            (address and 0xFF).toByte(),
            size.toByte()))
        try {
            return if (message.isValid) message.answer.copyOfRange(0x03, 3 + size) else null
        } catch (ex : Exception) {
            Logger.e {"$tag [readEeprom], $ex, $message"}
            return null
        }
    }

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val size = data.size
        val dataToWrite =
        byteArrayOf(
            0x07,
            0x03,
            0x01,
            (address and 0xFF).toByte(),
            (size / 2).toByte()) + data
        return interfaceWrite(dataToWrite).isValid
    }

    private suspend fun readAdjustmentData(): LWSAdjustmentData? {
        getIdentification()
        val message = interfaceWrite(byteArrayOf(0x40))
        try {
            if (message.isValid) {
                try {
                    val answer = message.answer
                    val oldOffset =
                        ((answer[3].toInt() and 0xFF) shl 8) + (answer[4].toInt() and 0xFF)
                    val oldId = answer[5].toInt() and 0xFF
                    val oldVin = String.format(
                        "%c%c%02X%02X%02X",
                        answer[10], answer[9], answer[8], answer[7], answer[6]
                    )
                    return LWSAdjustmentData(oldOffset, oldId, oldVin)
                } catch (_: Exception) {
                    return null
                }
            }
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readAdjustmentData], $ex, $message"}
        }
        return null
    }

    private suspend fun writeAdjustmentData(lwsAdjustmentData: LWSAdjustmentData?): Boolean {
        if (lwsAdjustmentData != null) {
            getIdentification()
            val vinBytes = lwsAdjustmentData.Vin!!.toByteArray()
            val vinData = byteArrayOf(0x00, 0x00, 0x00, vinBytes[1], vinBytes[0])
            for (i in 0..2) {
                val x = lwsAdjustmentData.Vin!!.substring(2 + 2 * i, 4 + 2 * i).toInt(16)
                vinData[2 - i] = x.toByte()
            }
            var data = byteArrayOf(((lwsAdjustmentData.Adjustment shr 8) and 0xFF).toByte(), (lwsAdjustmentData.Adjustment and 0xFF).toByte(), (lwsAdjustmentData.Id and 0xFF).toByte())
            data += vinData
            interfaceWrite(0x41.toByte(), data)
                .isValid
                .let { return interfaceWrite(0x42,byteArrayOf()).isValid}
        }
        return false
    }

    override suspend fun writeId(id: Int): Boolean {
        val data = readAdjustmentData()
        if (data != null) {
            data.Id = id
            return writeAdjustmentData(data)
         }
        return false
    }

    override suspend fun readId(): Int {
        val data = readAdjustmentData()
        if (data != null) {
            return data.Id
        }
        return -1
    }

    override suspend fun writeAdjustment(adjustment: Int): Boolean {
        val data = readAdjustmentData()
        if (data != null) {
            data.Adjustment = adjustment
            return writeAdjustmentData(data) && interfaceWrite(byteArrayOf(0x42)).isValid
        }
        return false
    }

    override suspend fun readAdjustment(): Int {
        val data = readAdjustmentData()
        if (data != null) {
            return data.Adjustment
        }
        return -1
    }
}
