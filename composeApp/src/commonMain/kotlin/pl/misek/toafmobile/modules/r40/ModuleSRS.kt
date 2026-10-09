package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceCoding
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleVIN
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import java.util.Locale
import kotlinx.coroutines.CancellationException

class ModuleSRS( protocolKWP: ProtocolKWP?) : IModule, IModuleCoding, IModuleAdvanceCoding, IModuleVIN, IModuleLiveData
{
    private val tag = "ModuleSRS"
    private var kwp: IProtocol? = null
    private val moduleAddress = ModuleAddress.MODULE_SRS.address
    private var liveDataElements: List<LiveDataElement> = emptyList()

    init {
        if (protocolKWP != null) kwp = protocolKWP
    }

    override fun getKoinName() = KoinNames.ModuleR40Srs

    override fun getModuleProtocol(): IProtocol {
        return kwp!!
    }

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol
    }

    override fun getModuleName() = "SRS"

    override fun getModuleAddress(): ModuleAddress {
        return ModuleAddress.MODULE_SRS
    }

    override fun getImageResource() = Res.drawable.module_srs
    
    override fun getModuleDescription() = Res.string.module_description_srs

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        if (securityAccess(0x05)) {
            val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x80.toByte()))
            try{
            if (message.isValid) {
                info = ModuleInfo()
                val answer = message.answer
                info.id1 = String(answer.copyOfRange(6, 16))
                info.hw = (answer[16].toInt() and 0xFF).toByte().toHexString()
                info.ci = (answer[17].toInt() and 0xFF).toByte().toHexString()
                info.di = (answer[18].toInt() and 0xFF).toByte().toHexString()
                info.sw = (answer[26].toInt() and 0xFF).toByte().toHexString()
                info.producer = ModulesFunction.getProducer(answer[25].toInt())
                info.date = String.format(
                    Locale.getDefault(),
                    "%02X/%02X/%02X%02X",
                    message.answer[24], message.answer[23], message.answer[21], message.answer[22])
                info.serial = getSerialNumber().toString()
                getProgrammingDate()?.let {
                    info.additionalInfo = ArrayList()
                    info.additionalInfo.add(Pair(Res.string.module_info_programing_date, it))
                }
            }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e{"$tag [getIdentification], $ex, $message"}
            }

        }
        return info
    }

    private suspend fun getProgrammingDate(): String? {
        val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x99.toByte()))
        try {
            if (message.isValid) {
                return String.format(
                    Locale.getDefault(),
                    "%02X/%02X/%02X%02X",
                    message.answer[6], message.answer[7], message.answer[8], message.answer[9]
                )
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getProgrammingDate()], $ex, $message"}
        }
        return null
    }
    private suspend fun setProgrammingDate() : Boolean
    {
        val currentDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val dataToWrite = byteArrayOf(0x99.toByte(),
            currentDate.day.toBcd(), (currentDate.month.ordinal +1).toBcd(), (currentDate.year/ 100).toBcd(), (currentDate.year %100).toBcd())
        return kwp?.write(moduleAddress, 0x3B, dataToWrite)?.isValid ?: false
    }

    fun Int.toBcd(): Byte {
        require(this in 0..99) { "Must be in 0..99" }
        return (((this / 10) shl 4) or (this % 10)).toByte()
    }
    private suspend fun getSerialNumber(): String? {
        val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x89.toByte()))
        try {
            if (message.isValid) {
                return String(message.answer.copyOfRange(6, 16))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getSerialNumber], $ex, $message"}
        }
        return null
    }

    override suspend fun getVIN(): String? {
        if (securityAccess(0x05)) {
            val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x90.toByte()))
            try {
                if (message.isValid) {
                    return String(message.answer.copyOfRange(6, 23))
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getVIN], $ex, $message"}
            }
        }
        return null
    }

    override suspend fun getOdometer(): String? {
        return null
    }

    // Wg SGBD FS_LESEN: 18 00 00, liczba wpisow w [5], rekordy 3-bajtowe od [6]: kod (2 bajty) + status.
    // Status: 01 powyzej progu, 02 ponizej progu, 04 przerwa, 08 zwarcie do masy, 10 zwarcie do Ubat,
    // 20 sporadyczny, 40 obecny, 80 blad aktywny. Wpisy ze statusem 00 SGBD tez pokazuje.
    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        if (securityAccess(0x05)) {
            val message = kwp!!.write(moduleAddress, 0x18.toByte(), byteArrayOf(0x00, 0x00), 250)
            try {
                if (message.isValid) {
                    val errors = ArrayList<ModuleError>()
                    result = errors
                    for (i in 0 until (message.answer[5].toInt() and 0xFF)) {
                        val errorCode =
                            ((message.answer[6 + 3 * i].toInt() and 0xFF) shl 8) + (message.answer[7 + 3 * i].toInt() and 0xFF)
                        val status = message.answer[8 + 3 * i].toInt() and 0xFF
                        val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                        if (status == 0x00)
                            errorDescription.add(Pair(Res.string.srs_error_detail_00, null))
                        else {
                            for (k in 0..7) {
                                val number = status and (0x01 shl k)
                                if (number != 0)
                                    ResourceMapper.stringResource("srs_error_detail_%02X".format(number))
                                        ?.let { errorDescription.add(Pair(it, null)) }
                            }
                        }
                        errors.add(
                            ModuleError(
                                ModuleAddress.MODULE_SRS,
                                String.format("%04X", errorCode),
                                errorDescription
                            )
                        )
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getAllErros], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun clearAllErrors(): Boolean {
        if (securityAccess(0x05)) {
            val message = kwp!!.write(moduleAddress, 0x14.toByte(), byteArrayOf(0x00, 0x00), 1500)
            reset()
            return message.isValid
        }
        return false
    }

    override suspend fun reset(): Boolean {
        return kwp!!.write(moduleAddress, 0x11.toByte(), byteArrayOf(0x01)).isValid
    }

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        if (securityAccess(0x05)) {
            val message = kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x04), 250)
            try {
                if (message.isValid) {
                    // 21 04: 4 bajty kodowania [6..9] + kopia DUP [10..13] (SGBD, tabela DIGITAL)
                    val coding = message.answer.copyOfRange(6, 10)
                    if (message.answer.size >= 14 && !coding.contentEquals(message.answer.copyOfRange(10, 14)))
                        Logger.w { "$tag [readCoding], DUP copy differs from coding: ${message.answer.copyOfRange(10, 14).toHexString()} / ${coding.toHexString()}" }
                    result.add(coding)
                    return result
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag, [readCoding], $ex, $message"}
            }
        }
        return null
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        if (securityAccess(0x05)) {
            val data = byteArrayOf(0x17)+ dataToSave[0]+ dataToSave[0]
            val message = kwp!!.write(moduleAddress, 0x3B.toByte(), data, 250)
            if (message.isValid)
                setProgrammingDate()
            reset()
            return message.isValid
        }
        return false
    }

    private suspend fun testerPresent(): Boolean {
        return kwp!!.write(moduleAddress, 0x3E.toByte(), byteArrayOf(), 250).isValid
    }

    private suspend fun securityAccess(level: Int): Boolean {
        if (testerPresent()) {
            var message = kwp!!.write(moduleAddress, 0x10.toByte(), byteArrayOf(0x85.toByte()))
            try {
                if (message.isValid) {
                    message =
                        kwp!!.write(moduleAddress, 0x27.toByte(), byteArrayOf(level.toByte()), 500)
                    if (message.isValid) {
                        val code =
                            ((message.answer[6].toInt() and 0xFF) shl 8) + (message.answer[7].toInt() and 0xFF)
                        if (code == 0x0000) return true
                        else {
                            val seed = securityAccessCalculator(code, level)
                            val data = byteArrayOf(
                                (level + 1).toByte(),
                                ((seed and 0xFF00) shr 8).toByte(), (seed and 0xFF).toByte()
                            )
                            return kwp!!.write(moduleAddress, 0x27.toByte(), data).isValid
                        }
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [securityAccess], $ex, $message"}
            }
        }
        return false
    }

    private fun securityAccessCalculator(seed: Int, level : Int): Int {
        var key = seed
        val seedHigh = (seed shr 8) and 0xFF
        val seedLow = seed and 0xFF
        val n = when (level) {
            0x01 -> seedHigh + 4
            0x03 -> ((seedHigh xor 0x57) and 0x07) + 2
            0x05 -> ((seedHigh xor 0x57) and 0x07) + 3
            0x07 -> ((seedLow xor 0x57) and 0x1E) + 4
            else -> 0
        }
        for (i in 0 until n) {
            var x = 0
            if ((key and 0x2000) == 0x2000) x = x xor 1
            if ((key and 0x1000) == 0x1000) x = x xor 1
            if ((key and 0x0020) == 0x0020) x = x xor 1
            if ((key and 0x0004) == 0x0004) x = x xor 1
            if ((key and 0x0001) == 0x0001) x = x xor 1
            key = key shl 1
            key = key and 0xFFFE
            key = key xor x
        }
        return key
    }
    override fun getVinLength(): Int = 17
    override suspend fun writeVIN(newVin: String): Boolean {
        // SGBD VIN_SCHREIBEN akceptuje tylko 17 znakow
        if (newVin.length != getVinLength()) return false
        try {
            if (securityAccess(0x05)) {
                return kwp!!.write(
                    moduleAddress,
                    0x3B.toByte(),
                    byteArrayOf(0x90.toByte())+ newVin.toByteArray(),
                    250
                ).isValid
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e{"$tag [writeVin]: ${ex.toString()}"}
        }
        return false
    }

    // Live data wg SGBD: 21 04 (konfiguracja), 21 10 (status OC/RFIS), 21 0F (lock byte)
    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        this.liveDataElements = liveDataElements
        securityAccess(0x05)
        return true
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (block in liveDataElements.map { it.block }.distinct()) {
            val message = kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(block.toByte()), 250)
            try {
                if (message.isValid)
                    result[block] = message.answer.copyOfRange(0, message.length - 1)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [readLiveData], $ex, $message" }
            }
        }
        return result.ifEmpty { null }
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.R40)
    }
}
