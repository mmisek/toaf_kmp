package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.containers.LWSAdjustmentData
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.activities.composeModels.activities.AdvanceControlLws
import pl.misek.toafmobile.modules.interfaces.AdvanceControlElement
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceCoding
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceControl
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLWSCoding
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.advance_control_lws_calibration
import toafmobile.composeapp.generated.resources.module_description_lws
import toafmobile.composeapp.generated.resources.module_lws

class ModuleLWS( protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_LWS, listOf(CarType.L322)),
    IModuleAdvanceCoding, IModuleLiveData, IModuleEepromProgramming, IModuleVIN, IModuleLWSCoding, IModuleAdvanceControl {
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

    override fun getAdvanceControlElements() = listOf(
        AdvanceControlElement(Res.string.advance_control_lws_calibration, isNew = true) { userInfo, onShowResult ->
            AdvanceControlLws(userInfo = userInfo, onShowResult = onShowResult)
        }
    )

    override suspend fun getVIN(): String? = readAdjustmentData()?.Vin

    // LWS przechowuje skrocony VIN: 2 litery + 6 cyfr (zapis hex), wg SGBD ABGLEICH_LESEN/VORGEBEN
    override fun getVinLength(): Int = 8

    override suspend fun writeVIN(newVin: String): Boolean {
        val newVin = newVin.uppercase()
        if (newVin.length != getVinLength() ||
            !newVin.take(2).all { it in 'A'..'Z' } ||
            !newVin.drop(2).all { it in '0'..'9' || it in 'A'..'F' })
            return false
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
        } catch (ex: CancellationException) {
            throw ex
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [getAllErrors], $ex, $message"}
        }
        return result
    }

    private var liveDataElements: List<LiveDataElement> = emptyList()

    // Zapytania wg SGBD: 1B 00/01/02 (STATUS_SG_DIGITAL/ONLINE/VIRTUELL), 0B 00/01 (STATUS_DIGITAL/ANALOG),
    // 06 03 01 FD 01 (STATUS_KL30 - licznik zanikow kl.30); klucz bloku zgodny z lws_live_data.json
    private val liveDataRequests = mapOf(
        0x00 to byteArrayOf(0x1B, 0x00),
        0x01 to byteArrayOf(0x1B, 0x01),
        0x02 to byteArrayOf(0x1B, 0x02),
        0x10 to byteArrayOf(0x0B, 0x00),
        0x11 to byteArrayOf(0x0B, 0x01),
        0x20 to byteArrayOf(0x06, 0x03, 0x01, 0xFD.toByte(), 0x01)
    )

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        this.liveDataElements = liveDataElements
        getIdentification()
        return true
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (block in liveDataElements.map { it.block }.distinct()) {
            val request = liveDataRequests[block] ?: continue
            val message = interfaceWrite(request)
            try {
                // Blad jednego bloku nie uniewaznia pozostalych
                if (message.isValid) {
                    val data = message.answer.copyOfRange(3, message.answer.size)
                    if (block == 0x02 && data.size >= 14)
                        signMagnitudeToU2(data, 12)
                    result[block] = data
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch ( ex : Exception)
            {
                Logger.e {"$tag [readLiveData], $ex, $message"}
            }
        }
        return result.ifEmpty { null }
    }

    private fun signMagnitudeToU2(data: ByteArray, index: Int) {
        val raw = ((data[index].toInt() and 0xFF) shl 8) or (data[index + 1].toInt() and 0xFF)
        if (raw > 0x7FFF) {
            val value = (-(raw - 0x8000)) and 0xFFFF
            data[index] = (value shr 8).toByte()
            data[index + 1] = value.toByte()
        }
    }

    suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()

        for (i in 0..0x6) {
            val message = interfaceWrite(byteArrayOf(0x08, i.toByte()))
            try {
                // Wg SGBD CODIERUNG_LESEN: 16 bajtow od [3]
                if (message.isValid)
                    result.add(message.answer.copyOfRange(3, 3 + 0x10))
                else
                    return null
            } catch (ex: CancellationException) {
                throw ex
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
        // Wg SGBD CODIERUNG_CHECK: 0A - suma kontrolna ze wszystkich 112 bajtow kodowania
        val check = interfaceWrite(byteArrayOf(0x0A))
        if (!check.isValid) return false
        Logger.i { "$tag [writeCoding], coding checksum: ${String.format("%02X%02X", check.answer[3], check.answer[4])}" }
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
            size.toByte()),
            400)
        try {
            return if (message.isValid) message.answer.copyOfRange(0x03, 3 + size) else null
        } catch (ex: CancellationException) {
            throw ex
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
                } catch (ex: CancellationException) {
                    throw ex
                } catch (_: Exception) {
                    return null
                }
            }
        } catch (ex: CancellationException) {
            throw ex
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
            return writeAdjustmentData(data)
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
