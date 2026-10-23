package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.activities.composeModels.activities.AdvanceControlEmsEwsSync
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.FlashProgrammingState
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.enums.ModuleFeature
import pl.misek.toafmobile.modules.interfaces.AdvanceControlElement
import pl.misek.toafmobile.modules.interfaces.EwsSyncStep
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceControl
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEwsSync
import pl.misek.toafmobile.modules.interfaces.IModuleFlashProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.r40.ModuleEWS
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Locale

private const val tag = "ModuleME72"
private const val AIF_DEFAULT_ADDRESS = 0x81FD54
private const val AIF_RECORD_SIZE = 0x2E
private const val AIF_MAX_RECORDS = 14
private const val FLASH_START_ADDRESS = 0x800000
private const val FLASH_SIZE = 0x80000
private const val FLASH_BLOCK_SIZE = 0xFE
private const val FLASH_READ_RETRIES = 3
class ModuleME72(private var kwp: ProtocolKWP) :  IModule,
    IModuleControl,
    IModuleLiveData,
    IModuleEwsSync,
    IModuleAdvanceControl,
    IModuleFlashProgramming
{
    private val moduleAddress = ModuleAddress.MODULE_ME72.address
    private val _programmingState =  MutableSharedFlow<FlashProgrammingState>(replay = 10, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val liveDataBlocksToRead: MutableList<Byte> = ArrayList()
    private var _liveDataElements : List<LiveDataElement> = ArrayList()

    override fun getKoinName() = KoinNames.ModuleMe72

    override fun getModuleProtocol(): IProtocol {
        return kwp
    }

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolKWP
    }

    override suspend fun reset(): Boolean {
        //return kwp.Write(MODULE_ADDRESS,MODULE_DDE,new byte[] {(byte)0x82}).isValid();
        return false
    }
    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        _liveDataElements = liveDataElements
        liveDataBlocksToRead.clear()
        for (el in liveDataElements)
            liveDataBlocksToRead.add(el.block.toByte())
        return true
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val requests = _liveDataElements.map { it.service to it.block }.distinct()
        for ((service, block) in requests) {
            val message = when (service) {
                0x23 -> kwp.write(moduleAddress, service.toByte(), byteArrayOf((block shr 16).toByte(), (block shr 8).toByte(), block.toByte(), 0x05, 0x02), 150)
                0x22 -> kwp.write(moduleAddress, service.toByte(), byteArrayOf((block shr 8).toByte(), block.toByte()), 150)
                0x30 -> kwp.write(moduleAddress, service.toByte(), byteArrayOf(block.toByte(), 0x01), 150)
                0x21 -> kwp.write(moduleAddress, service.toByte(), byteArrayOf(block.toByte()), 150)
                else -> continue
            }
            try {
                if (message.isValid)
                    result[block] = message.answer.copyOfRange(0, message.length - 1)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception) {
                Logger.e {"$tag [readLiveData], $ex, $message"}
            }
        }
        return if (result.isEmpty() && requests.isNotEmpty()) null else result
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        kwp.write(moduleAddress, 0x83.toByte(), byteArrayOf(0x03, 0x32, 0x02, 0x6E, 0x83.toByte(), 0x0A))
        val data = if (controlElement.byteInBlock == 0x00)
            byteArrayOf(controlElement.block.toByte(), 0x00)
        else
            byteArrayOf(
                controlElement.block.toByte(),
                controlElement.byteInBlock.toByte(),
                (controlElement.controlSetValue?.value ?: controlElement.value).toByte()
            )
        return kwp.write(moduleAddress, 0x30.toByte(), data).isValid
    }


    override suspend fun syncEws(ews: ModuleEWS?, onStep: (EwsSyncStep) -> Unit): Boolean {
        if (ews == null) return false
        val ecuIdent = getIdentification()
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_ecu_ident, !ecuIdent?.id1.isNullOrEmpty(), ecuIdent?.id1))
        if (ecuIdent?.id1.isNullOrEmpty()) return false
        val ewsId = ews.getIdentification()
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_ews_ident, !ewsId?.id1.isNullOrEmpty(), ewsId?.id1))
        if (ewsId?.id1.isNullOrEmpty()) return false
        val handshake = ews.triggerDmeSync()
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_ews_handshake, handshake))
        if (!handshake) return false
        val enter = kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0x83.toByte(), 0x01)).isValid
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_enter, enter))
        if (!enter) return false
        val statusMsg = kwp.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x06))
        val statusByte = if (statusMsg.isValid) statusMsg.answer.getOrNull(6)?.toInt()?.and(0xFF) else null
        val success = statusByte == 0x00
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_status, success, statusByte?.let { "0x%02X".format(it) }))
        return success
    }

    override fun getAdvanceControlElements() = listOf(
        AdvanceControlElement(Res.string.advance_control_ews_sync_title, isNew = true) { userInfo, onShowResult ->
            AdvanceControlEmsEwsSync(engineModule = this, userInfo = userInfo, onShowResult = onShowResult)
        }
    )

    override fun getImageResource() = Res.drawable.module_engine

    override fun getModuleDescription() = Res.string.module_description_me72

    override fun getModuleName(): String {
        return ModuleAddress.MODULE_ME72.showName
    }

    override fun getModuleAddress(): ModuleAddress {
        return ModuleAddress.MODULE_ME72
    }

    override suspend fun clearAllErrors(): Boolean {
        return kwp.write(moduleAddress, 0x14.toByte(), byteArrayOf(0x00, 0x00)).isValid
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        val message = kwp.write(moduleAddress, 0xA0.toByte(), byteArrayOf(0x00))
        if (message.isValid) {
            try {
                val n = message.answer[5].toInt() and 0xFF
                val errors = ArrayList<ModuleError>()
                result = errors
                for (i in 1..n) {
                    try {
                        readError(i)?.let { errors.add(it) }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Logger.e { "$tag [getAllErrors] error $i, $ex" }
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [getAllErrors], $ex ,$message" }
            }
        }
        return result
    }

    // Uklad odpowiedzi wg SGBD FS_LESEN (indeksy w answer):
    // 7 - kod, 9..11 - bity typu bledu, 15 - licznik kasowan, 45 - czestosc (F_HFK),
    // zestawy UW (1 - pierwsze, 2 - drugie, 3 - aktualne wykrycie): km w 27+6*s (x10), wartosci w 29+6*s..32+6*s
    private suspend fun readError(index: Int): ModuleError? {
        val message = kwp.write(moduleAddress, 0xA0.toByte(), byteArrayOf(index.toByte()))
        if (!message.isValid) return null
        val answer = message.answer
        val locale = Locale.getDefault()
        val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
        val errorCode = answer[7].toInt() and 0xFF
        pCode(errorCode, answer[10].toInt() and 0xFF)?.let { errorDescription.add(Pair(Res.string.me72_error_pcode, it)) }
        val count = answer.getOrNull(45)?.let { it.toInt() and 0xFF }
        count?.let { errorDescription.add(ResourceHelper.getErrorDescriptionCount(it)) }
        errorDescription.add(ResourceHelper.getErrorDescriptionLogisticCounter(answer[15].toInt() and 0xFF))

        val detailMatrix = errorDetailMatrix.firstOrNull { it.errorCode == errorCode }
        if (detailMatrix != null) {
            for (j in 0..2) {
                val bits = answer[9 + j].toInt() and 0xFF
                for (k in 0..7) {
                    val detail = detailMatrix.details[k + j * 8].toInt() and 0xFF
                    if ((bits and (0x01 shl k)) != 0 && detail != 0x00)
                        ResourceMapper.stringResource("me72_error_detail_%02x".format(detail))
                            ?.let { errorDescription.add(Pair(it, null)) }
                }
            }
        }

        val matrix = errorMatrix.firstOrNull { it.errorCode == errorCode }
        if (matrix != null) {
            // SGBD pokazuje tyle zestawow UW, ile wynosi czestosc (max 3)
            val sets = (count ?: 1).coerceIn(1, 3)
            for (set in 0 until sets) {
                val base = 27 + 6 * set
                if (answer.size <= base + 5) break
                val km = (((answer[base].toInt() and 0xFF) shl 8) or (answer[base + 1].toInt() and 0xFF)) * 10
                errorDescription.add(Pair(occurrenceResources[set], "$km km"))
                for (j in 0..3) {
                    val uwCode = matrix.details[j].toInt() and 0xFF
                    val freezeFrame = freezeFrameMatrix.firstOrNull { it.code == uwCode } ?: continue
                    val label = ResourceMapper.stringResource("me72_freeze_frame_detail_%02x".format(uwCode)) ?: continue
                    val raw = answer[base + 2 + j].toInt() and 0xFF
                    if (uwCode == 0x01 || uwCode == 0x02) {
                        // Status regulatora lambda - tekst wg FTYPTEXTE zamiast wartosci liczbowej
                        errorDescription.add(Pair(label, lambdaStatusResource(raw) ?: "0x%02X".format(raw)))
                        continue
                    }
                    // UWF_C = 1 - wartosc ze znakiem
                    val x = if (freezeFrame.factorC == 1f && raw >= 0x80) raw - 0x100 else raw
                    val value = freezeFrame.factorA * x + freezeFrame.factorB
                    val valueString = if (freezeFrame.unit == "") String.format(locale, "%.2f", value) else String.format(locale, "%.2f %s", value, freezeFrame.unit)
                    errorDescription.add(Pair(label, valueString))
                }
            }
        }
        return ModuleError(ModuleAddress.MODULE_ME72, "%02X".format(errorCode), errorDescription)
    }

    private val occurrenceResources = listOf(
        Res.string.me72_error_occurrence_first,
        Res.string.me72_error_occurrence_second,
        Res.string.me72_error_occurrence_last
    )

    /** Kod P wg T4 (GetPcode): pierwszy ustawiony bit z bitow 4..7 bajtu typu wybiera jeden z 4 kodow */
    private fun pCode(errorCode: Int, type: Int): String? {
        val codes = pCodes[errorCode] ?: return null
        val bit = (0..3).firstOrNull { (type shr (4 + it)) and 0x01 != 0 } ?: return null
        return codes[bit].ifEmpty { null }
    }

    private fun lambdaStatusResource(value: Int): StringResource? = when (value) {
        0x01 -> Res.string.me72_lambda_status_21
        0x02 -> Res.string.me72_lambda_status_22
        0x04 -> Res.string.me72_lambda_status_23
        0x08 -> Res.string.me72_lambda_status_24
        0x10 -> Res.string.me72_lambda_status_25
        else -> null
    }

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        val message = kwp.write(moduleAddress, 0xA2.toByte(), byteArrayOf(), 300)
        val aif = readAIF()
        try {
            if (message.isValid) {
                try {
                    info = ModuleInfo()
                    val answer = message.answer
                    info.additionalInfo = ArrayList()
                    info.id1 = String(answer.copyOfRange(5, 12))
                    if (aif!= null)
                        info.id2 = aif.id2
                    info.hw = String(answer.copyOfRange(12, 14))
                    info.ci = String(answer.copyOfRange(14, 16))
                    info.di = String(answer.copyOfRange(16, 18))
                    info.bi = String(answer.copyOfRange(18, 20))
                    info.date = String(answer.copyOfRange(20, 22)) + "/" + String(answer.copyOfRange(22, 24))
                    // SGBD IDENT: [24..33] ID_LIEF_NR, [34..35] ID_SW_NR, [38..46] ID_PROD_NR
                    info.serial = asciiField(answer, 38, 47) ?: ""
                    asciiField(answer, 24, 34)?.let { info.additionalInfo.add(Pair(Res.string.me72_info_supplier_number, it)) }
                    asciiField(answer, 34, 36)?.let { info.additionalInfo.add(Pair(Res.string.me72_info_software_number, it)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Logger.e { "$tag : ${ex.toString()}" }
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getIdentification], $ex, $message"}
        }

        return info
    }
    /** Pole ASCII z odpowiedzi; null gdy poza ramka lub niezaprogramowane (0xFF) */
    private fun asciiField(answer: ByteArray, from: Int, to: Int): String? {
        if (answer.size <= to) return null
        val field = answer.copyOfRange(from, to)
        if (field.any { (it.toInt() and 0xFF) !in 0x20..0x7E }) return null
        return String(field).trim().ifEmpty { null }
    }

    /** Adres AIF wg T4/SGBD: bajty 0x3C..0x3E danych odpowiedzi 21 01 ([0x42..0x44] ramki) */
    private suspend fun getAifAddress(): Int {
        val message = kwp.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x01))
        try {
            if (message.isValid && message.answer.size > 0x45) {
                val a = message.answer
                return ((a[0x42].toInt() and 0xFF) shl 16) or ((a[0x43].toInt() and 0xFF) shl 8) or (a[0x44].toInt() and 0xFF)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getAifAddress], $ex, $message" }
        }
        return AIF_DEFAULT_ADDRESS
    }

    /** AIF: do 14 rekordow po 0x2E bajtow, koniec na 0xFF - wazny jest ostatni zapisany */
    private suspend fun readAIF() : Aif?
    {
        var address = getAifAddress()
        var aif = Aif("","")
        for (x in 0 until AIF_MAX_RECORDS) {
            if (x > 0) address += AIF_RECORD_SIZE
            val data = byteArrayOf(
                ((address shr 16) and 0xFF).toByte(),
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                0x00,
                AIF_RECORD_SIZE.toByte()
            )
            val message = kwp.write(moduleAddress, 0x23, data)
            try {
                if (message.isValid && (message.length == 0x2F + 0x05)) {
                    if (message.answer[5] == 0xFF.toByte())
                        break
                    val vin = String(message.answer.copyOfRange(5, 16)) + hexExpand(message.answer, 16, 19)
                    val id2 = String(message.answer.copyOfRange(25, 28)) + hexExpand(message.answer, 28, 31)
                    aif = Aif(vin, id2)
                }
                else
                    break
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [getAIF], $ex, $message" }
                return null
            }
        }
        return aif
    }

    override suspend fun getVIN(): String?{
        val aif = readAIF()
        return aif?.vin
    }

    override suspend fun getOdometer(): String? {
        return null
    }

    private suspend fun startDiagnosticSession(level: Int): Boolean {
        //if (kwp is ProtocolKWPStarEMS2K) testerPresent()
        return kwp.write(moduleAddress, 0x10.toByte(), byteArrayOf(level.toByte()), 200).isValid
    }

    private suspend fun endDiagnosticSession(): Boolean {
        //if (kwp is ProtocolKWPStarEMS2K) testerPresent()
        return kwp.write(moduleAddress, 0x20.toByte(), byteArrayOf()).isValid
    }

    private suspend fun testerPresent(): Boolean {
        kwp.write(moduleAddress, 0x81.toByte(), byteArrayOf(), 500)
        return kwp.write(moduleAddress, 0x3E.toByte(), byteArrayOf(), 500).isValid
    }

    private suspend fun securityAccess(level: Int): Boolean {
        var result = false
        val message = kwp.write(moduleAddress, 0x27.toByte(), byteArrayOf(level.toByte()), 500)
        try {
            if (message.isValid) {
                val seed =
                //if (kwp is ProtocolKWPStarEMS2K)
                //        ((message.answer[5].toInt() and 0xFF) shl 8) + (message.answer[6].toInt() and 0xFF)
                    //else
                    ((message.answer[6].toInt() and 0xFF) shl 8) + (message.answer[7].toInt() and 0xFF)
                if (seed == 0x0000) result = true
                else {
                    val key = securityAccessCalc(seed, level)
                    result = kwp.write(
                        moduleAddress,
                        0x27.toByte(),
                        byteArrayOf(
                            (level + 1).toByte(),
                            ((key and 0xFF00) shr 8).toByte(),
                            (key and 0x00FF).toByte()
                        ),
                        200
                    ).isValid
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [securityAccess], $ex, $message"}
        }


        return result
    }

    private fun securityAccessCalc(seed: Int, level: Int): Int {
        Logger.i{"$tag : Security Access Seed - ${String.format("%04X", seed)}"}
        var key = seed
        val n = when (level) {
            0x01 -> 3
            0x03 -> ((((key shr 8) and 0xFF) xor 0x34) and 0x0F ) + 1
            0x05 -> (((key and 0xFF) xor 0x34) and 0x13) + 2
            0x07 -> ((((key and 0xFF00) shr 8 ) xor 0x34) and 0x0F) + 5
            0x09 -> (((key and 0xFF) xor 0x34) and 0x0B) +3
            else -> 0
        }
        for (i in 0 until n) {
            var x = 0
            if ((key and 0x4000) == 0x4000) x = x xor 1
            if ((key and 0x0040) == 0x0040) x = x xor 1
            if ((key and 0x0020) == 0x0020) x = x xor 1
            if ((key and 0x0002) == 0x0002) x = x xor 1
            if ((key and 0x0001) == 0x0001) x = x xor 1
            key = ((key shl 1) and 0xFFFE) xor x
        }
        return key
    }

    override val programmingState = _programmingState.asSharedFlow()

    override val flashProgrammingAvailable: Boolean = false

    override fun getNewFeatures() = super.getNewFeatures() + ModuleFeature.FlashProgramming

    override suspend fun writeFlash(data: ByteArray) {
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_main_function_not_supported_yet))
    }

    /**
     * Odczyt flash 0x800000-0x87FFFF (512 KB) przez ReadMemoryByAddress (23), bez security access.
     * Bloki 0xFE od 0x800000 - zgodne bajt w bajt ze zrzutem K-TAG. Odczyt zaczynajacy sie dokladnie
     * od adresu rekordu AIF zwraca AIF (podstawiany przez ECU), stad stale wyrownanie blokow.
     */
    override suspend fun readFlash() {
        var result = ByteArray(0)
        val steps = (FLASH_SIZE + FLASH_BLOCK_SIZE - 1) / FLASH_BLOCK_SIZE
        val startTime = System.currentTimeMillis()
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_read_identification))
        val moduleInfo = getIdentification()
        val vin = getVIN()
        _programmingState.tryEmit(FlashProgrammingState.Message("ID1 : " + moduleInfo?.id1))
        _programmingState.tryEmit(FlashProgrammingState.Message("ID2 : " + moduleInfo?.id2))
        _programmingState.tryEmit(FlashProgrammingState.Message("VIN : $vin"))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_flash_reading))

        var step = 0
        var offset = 0
        while (offset < FLASH_SIZE) {
            _programmingState.tryEmit(FlashProgrammingState.Progress(step++ * 100 / steps))
            val size = minOf(FLASH_BLOCK_SIZE, FLASH_SIZE - offset)
            val data = readFlashData(FLASH_START_ADDRESS + offset, size)
            if (data == null) {
                _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_read_error))
                return
            }
            result += data
            offset += size
        }

        val totalSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
            Res.string.activity_flash_programming_read_time,
            String.format(Locale.getDefault(), "%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds / 60) % 60, totalSeconds % 60)))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_end))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))
        _programmingState.tryEmit(FlashProgrammingState.FlashData(result.toList(), moduleInfo, vin))
    }

    private suspend fun readFlashData(address: Int, size: Int): ByteArray? {
        repeat(FLASH_READ_RETRIES) {
            val message = kwp.write(
                moduleAddress, 0x23.toByte(),
                byteArrayOf((address shr 16).toByte(), (address shr 8).toByte(), address.toByte(), 0x00, size.toByte()),
                (300 + size * 3).toLong()
            )
            try {
                if (message.isValid && message.length == size + 6)
                    return message.answer.copyOfRange(5, 5 + size)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [readFlashData], $ex, $message" }
            }
        }
        return null
    }

    private suspend fun bootMode(): Boolean {
        return kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0xFB.toByte())).isValid
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.L322)
    }

    private fun hexExpand(data : ByteArray, fromIndex : Int, toIndex :Int) : String
    {
        return data.copyOfRange(fromIndex,toIndex).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }

    data class Aif ( val vin : String, val id2 : String)

    data class ErrorDetail(val errorCode : Int, val details : ByteArray)
    data class ErrorFrameDetail(val errorCode : Int, val details : ByteArray)
    data class FreezeFrameDetail(val code : Int, val unit : String, val factorA : Float, var factorB : Float, val factorC :Float)

    /** T4 (DAVU0003): kod bledu DME -> kody P dla bitow 4..7 bajtu typu (rekord A0 [4]), "" = brak */
    private val pCodes: Map<Int, Array<String>> = mapOf(
        0x02 to arrayOf("P0448", "P0447", "P2450", ""),
        0x03 to arrayOf("", "", "", "P0040"),
        0x04 to arrayOf("P0058", "P0057", "P0056", "P0161"),
        0x05 to arrayOf("P0052", "P0051", "P0050", "P0155"),
        0x0A to arrayOf("P0132", "P0131", "P0134", "P0130"),
        0x0C to arrayOf("P0138", "P0137", "P0140", "P0136"),
        0x0D to arrayOf("P0032", "P0031", "P0030", "P0135"),
        0x0E to arrayOf("P0038", "P0037", "P0036", "P0141"),
        0x0F to arrayOf("P0133", "P0133", "", ""),
        0x10 to arrayOf("P2A00", "P2A00", "", ""),
        0x11 to arrayOf("P0139", "P0139", "", "P0139"),
        0x12 to arrayOf("P0152", "P0151", "P0154", "P0150"),
        0x14 to arrayOf("P0158", "P0157", "P0160", "P0156"),
        0x15 to arrayOf("P0153", "P0153", "", ""),
        0x16 to arrayOf("P2A03", "P2A03", "", ""),
        0x17 to arrayOf("P0159", "P0159", "", "P0159"),
        0x18 to arrayOf("P0171", "P0172", "", ""),
        0x19 to arrayOf("P0174", "P0175", "", ""),
        0x1A to arrayOf("P0171", "P0172", "", ""),
        0x1B to arrayOf("P0174", "P0175", "", ""),
        0x1C to arrayOf("P1171", "P1172", "", ""),
        0x1D to arrayOf("P1174", "P1175", "", ""),
        0x1E to arrayOf("P1162", "P1161", "", ""),
        0x1F to arrayOf("P1164", "P1163", "", ""),
        0x20 to arrayOf("P0507", "P0506", "", ""),
        0x21 to arrayOf("", "P0011", "P0010", "P0012"),
        0x22 to arrayOf("", "P0021", "P0020", "P0022"),
        0x23 to arrayOf("P0634", "", "", ""),
        0x27 to arrayOf("P1693", "P1673", "P1694", "P1672"),
        0x28 to arrayOf("P0420", "", "", ""),
        0x2D to arrayOf("P0430", "", "", ""),
        0x32 to arrayOf("P0301", "P0301", "", "P0301"),
        0x33 to arrayOf("P0305", "P0305", "", "P0305"),
        0x34 to arrayOf("P0304", "P0304", "", "P0304"),
        0x35 to arrayOf("P0308", "P0308", "", "P0308"),
        0x36 to arrayOf("P0306", "P0306", "", "P0306"),
        0x37 to arrayOf("P0303", "P0303", "", "P0303"),
        0x38 to arrayOf("P0307", "P0307", "", "P0307"),
        0x39 to arrayOf("P0302", "P0302", "", "P0302"),
        0x3E to arrayOf("P1315", "P0300", "", "P0316"),
        0x50 to arrayOf("", "P0491", "", ""),
        0x51 to arrayOf("", "P0492", "", ""),
        0x54 to arrayOf("P1414", "P1413", "P0418", ""),
        0x55 to arrayOf("P0413", "P0414", "P0412", ""),
        0x5D to arrayOf("", "P0440", "", ""),
        0x62 to arrayOf("P0444", "P0445", "P0443", ""),
        0x65 to arrayOf("", "", "", "P1659"),
        0x66 to arrayOf("P1570", "P1568", "P1569", "P1564"),
        0x67 to arrayOf("", "", "", "P1660"),
        0x69 to arrayOf("", "", "", "P0604"),
        0x6A to arrayOf("", "", "", "P0571"),
        0x6B to arrayOf("", "", "", "P0605"),
        0x6C to arrayOf("", "", "", "P0606"),
        0x6D to arrayOf("P0563", "P0562", "", "P0561"),
        0x6E to arrayOf("P061A", "", "", ""),
        0x6F to arrayOf("", "", "P0335", ""),
        0x70 to arrayOf("", "", "", "P0370"),
        0x71 to arrayOf("P0343", "P0342", "", "P0340"),
        0x72 to arrayOf("P0348", "P0347", "", "P0345"),
        0x73 to arrayOf("P0103", "P0102", "", ""),
        0x75 to arrayOf("", "", "", "P0120"),
        0x76 to arrayOf("P0123", "P0122", "", "P0121"),
        0x77 to arrayOf("P0223", "P0222", "", "P0221"),
        0x78 to arrayOf("", "", "P0500", ""),
        0x79 to arrayOf("P0501", "", "P0503", ""),
        0x7A to arrayOf("", "", "", "P0071"),
        0x7B to arrayOf("P0118", "P0117", "P0125", "P0116"),
        0x7C to arrayOf("P0113", "P0112", "", ""),
        0x7D to arrayOf("P2184", "P2185", "", "P2183"),
        0x7F to arrayOf("", "", "P2771", "P2772"),
        0x82 to arrayOf("", "", "", "P061F"),
        0x83 to arrayOf("P2112", "P2111", "", ""),
        0x84 to arrayOf("", "", "", "P2101"),
        0x85 to arrayOf("P2119", "", "", ""),
        0x86 to arrayOf("", "", "", "P2176"),
        0x87 to arrayOf("", "", "", "P2107"),
        0x88 to arrayOf("", "", "", "P2108"),
        0x8B to arrayOf("", "", "", "P0128"),
        0x8C to arrayOf("P1616", "P1615", "P1614", ""),
        0x94 to arrayOf("", "P1666", "P1621", "P1674"),
        0x96 to arrayOf("P0262", "P0261", "P0201", ""),
        0x97 to arrayOf("P0274", "P0273", "P0205", ""),
        0x98 to arrayOf("P0271", "P0270", "P0204", ""),
        0x99 to arrayOf("P0283", "P0282", "P0208", ""),
        0x9A to arrayOf("P0277", "P0276", "P0206", ""),
        0x9B to arrayOf("P0268", "P0267", "P0203", ""),
        0x9C to arrayOf("P0280", "P0279", "P0207", ""),
        0x9D to arrayOf("P0265", "P0264", "P0202", ""),
        0xA3 to arrayOf("P2175", "P2173", "", ""),
        0xA4 to arrayOf("P0108", "P0107", "", "P0106"),
        0xA5 to arrayOf("P0077", "P0076", "P0075", ""),
        0xA6 to arrayOf("P0083", "P0082", "P0081", ""),
        0xA7 to arrayOf("P0232", "P0231", "P0233", ""),
        0xAA to arrayOf("P0533", "P0532", "P0530", ""),
        0xB7 to arrayOf("P0455", "P0442", "", ""),
        0xB8 to arrayOf("", "", "", "P0441"),
        0xB9 to arrayOf("P0453", "P0452", "", "P0450"),
        0xBA to arrayOf("P2402", "P2401", "P2400", ""),
        0xBB to arrayOf("", "P0456", "", ""),
        0xBC to arrayOf("P0455", "P0442", "", ""),
        0xBD to arrayOf("P2406", "P2405", "P2407", "P2404"),
        0xC9 to arrayOf("P240C", "P240B", "P240A", ""),
        0xCC to arrayOf("", "P1624", "P1623", ""),
        0xD2 to arrayOf("P0328", "P0327", "", ""),
        0xD3 to arrayOf("P1328", "P1327", "", ""),
        0xD4 to arrayOf("P0333", "P0332", "", ""),
        0xD5 to arrayOf("P1333", "P1332", "", ""),
        0xD6 to arrayOf("", "", "", "P0324"),
        0xD7 to arrayOf("", "", "", "P0324"),
        0xD8 to arrayOf("", "", "", "P0324"),
        0xDB to arrayOf("", "", "U0102", ""),
        0xDC to arrayOf("", "", "U0101", ""),
        0xDD to arrayOf("", "", "U0121", ""),
        0xDE to arrayOf("", "", "U0155", ""),
        0xE0 to arrayOf("", "", "", "P1522"),
        0xE2 to arrayOf("P0463", "P0462", "P0464", "P0461"),
        0xE5 to arrayOf("", "P1619", "", ""),
        0xE6 to arrayOf("", "", "", "P2135"),
        0xE7 to arrayOf("P2123", "P2122", "", "P2121"),
        0xE8 to arrayOf("P2128", "P2127", "", "P2126"),
        0xE9 to arrayOf("P0617", "P0616", "P0615", ""),
        0xEA to arrayOf("", "", "P1620", ""),
        0xED to arrayOf("P0512", "P0512", "P0512", "P0512"),
        0xEE to arrayOf("P2177", "P2178", "P2187", "P2188"),
        0xEF to arrayOf("P2179", "P2180", "P2189", "P2190"),
        0xF0 to arrayOf("P0111", "P0111", "", "P0111")
    )

    val errorMatrix: List<ErrorDetail> = listOf(
        ErrorDetail(0x01, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x0B.toByte())),  // Leakage Diagnosis pump Output
        ErrorDetail(0x02, byteArrayOf(0x0A.toByte(), 0x14.toByte(), 0x24.toByte(), 0x0B.toByte())),  // Solenoid Valve DMTL Output
        ErrorDetail(0x03, byteArrayOf(0x29.toByte(), 0x8C.toByte(), 0x06.toByte(), 0x08.toByte())),  // switched Oxygen sensors
        ErrorDetail(0x04, byteArrayOf(0x79.toByte(), 0x30.toByte(), 0x7D.toByte(), 0x34.toByte())),  // Oxygen sensors-Heater rear Kat (Bank2)
        ErrorDetail(0x05, byteArrayOf(0x7B.toByte(), 0x2E.toByte(), 0x7F.toByte(), 0x32.toByte())),  // Oxygen sensors-Heater for Kat (Bank2)
        ErrorDetail(0x0A, byteArrayOf(0x29.toByte(), 0x8C.toByte(), 0x31.toByte(), 0x16.toByte())),  // Lambda probe for cat.
        ErrorDetail(0x0C, byteArrayOf(0x2B.toByte(), 0x8C.toByte(), 0x33.toByte(), 0x17.toByte())),  // Lambda probe rear cat.
        ErrorDetail(0x0D, byteArrayOf(0x7A.toByte(), 0x2D.toByte(), 0x7E.toByte(), 0x31.toByte())),  // Oxygen sensors-Heater for cat.
        ErrorDetail(0x0E, byteArrayOf(0x78.toByte(), 0x2F.toByte(), 0x7C.toByte(), 0x33.toByte())),  // Oxygen sensors-Heater rear cat.
        ErrorDetail(0x0F, byteArrayOf(0x0A.toByte(), 0x29.toByte(), 0x31.toByte(), 0x36.toByte())),  // Lambda-Sensor ageing Period duration
        ErrorDetail(0x10, byteArrayOf(0x0A.toByte(), 0x29.toByte(), 0x31.toByte(), 0x36.toByte())),  // Lambda-Sensor ageing TV
        ErrorDetail(0x11, byteArrayOf(0x0A.toByte(), 0x2B.toByte(), 0x33.toByte(), 0x17.toByte())),  // Lambda-Sensor ageing after Cat.
        ErrorDetail(0x12, byteArrayOf(0x2A.toByte(), 0x8C.toByte(), 0x32.toByte(), 0x18.toByte())),  // Lambda-Probe2 for cat.
        ErrorDetail(0x14, byteArrayOf(0x2C.toByte(), 0x8C.toByte(), 0x34.toByte(), 0x19.toByte())),  // Lambda-Probe2 rear cat.
        ErrorDetail(0x15, byteArrayOf(0x0A.toByte(), 0x2A.toByte(), 0x32.toByte(), 0x37.toByte())),  // Lambda-Sensor ageing Period duration Bank2
        ErrorDetail(0x16, byteArrayOf(0x0A.toByte(), 0x2A.toByte(), 0x32.toByte(), 0x37.toByte())),  // Lambda-Sensor ageing TV Bank2
        ErrorDetail(0x17, byteArrayOf(0x0A.toByte(), 0x2C.toByte(), 0x34.toByte(), 0x19.toByte())),  // Lambda-Sensor ageing h. cat. Bank2
        ErrorDetail(0x18, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x13.toByte(), 0x3C.toByte())),  // LR-Adaption multipl. Area2
        ErrorDetail(0x19, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x13.toByte(), 0x3C.toByte())),  // LR-Adaption multipl. Area2 (Bank2)
        ErrorDetail(0x1A, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x13.toByte(), 0x3C.toByte())),  // LR-Adaption multipl. Area1
        ErrorDetail(0x1B, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x13.toByte(), 0x3C.toByte())),  // LR-Adaption multipl. Area1 (Bank2)
        ErrorDetail(0x1C, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x3C.toByte(), 0x05.toByte())),  // LR-Adaption additiv per Time
        ErrorDetail(0x1D, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x3C.toByte(), 0x07.toByte())),  // LR-Adaption additiv per Time Bank 2
        ErrorDetail(0x1E, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x3C.toByte(), 0x05.toByte())),  // LR-Adaption additiv per Ignition
        ErrorDetail(0x1F, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x3C.toByte(), 0x07.toByte())),  // LR-Adaption additiv per Ignition Bank 2
        ErrorDetail(0x20, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x15.toByte())),  // idle-speed control faulty
        ErrorDetail(0x21, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x6C.toByte(), 0x6E.toByte())),  // Camshaft control
        ErrorDetail(0x22, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x6D.toByte(), 0x6F.toByte())),  // Camshaft control Bank 2
        ErrorDetail(0x27, byteArrayOf(0x0A.toByte(), 0x14.toByte(), 0x00.toByte(), 0x00.toByte())),  // EWS3.3 Manipulation protection
        ErrorDetail(0x28, byteArrayOf(0x88.toByte(), 0x89.toByte(), 0x06.toByte(), 0x3C.toByte())),  // Catalytic-converter conversion
        ErrorDetail(0x2D, byteArrayOf(0x87.toByte(), 0x8A.toByte(), 0x08.toByte(), 0x3C.toByte())),  // Catalytic-converter conversion (Bank2)
        ErrorDetail(0x32, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x12.toByte(), 0x3C.toByte())),  // Misfire detection Zyl.1,
        ErrorDetail(0x33, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x12.toByte(), 0x3C.toByte())),  // Misfire detection Zyl.5,
        ErrorDetail(0x34, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x12.toByte(), 0x3C.toByte())),  // Misfire detection Zyl.4,
        ErrorDetail(0x35, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x12.toByte(), 0x3C.toByte())),  // Misfire detection Zyl.8,
        ErrorDetail(0x36, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x12.toByte(), 0x3C.toByte())),  // Misfire detection Zyl.6,
        ErrorDetail(0x37, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x12.toByte(), 0x3C.toByte())),  // Misfire detection Zyl.3,
        ErrorDetail(0x38, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x12.toByte(), 0x3C.toByte())),  // Misfire detection Zyl.7,
        ErrorDetail(0x39, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x12.toByte(), 0x3C.toByte())),  // Misfire detection Zyl.2,
        ErrorDetail(0x3E, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x12.toByte(), 0x3C.toByte())),  // Misfire detection, sum error,
        ErrorDetail(0x50, byteArrayOf(0x85.toByte(), 0x82.toByte(), 0x8B.toByte(), 0x84.toByte())),  // Secondary air system
        ErrorDetail(0x51, byteArrayOf(0x86.toByte(), 0x83.toByte(), 0x8B.toByte(), 0x84.toByte())),  // Secondary air system Bank2
        ErrorDetail(0x52, byteArrayOf(0x0A.toByte(), 0x11.toByte(), 0x14.toByte(), 0x05.toByte())),  // Secondary air valve
        ErrorDetail(0x54, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x13.toByte(), 0x14.toByte())),  // Output Relay Secondary air pump
        ErrorDetail(0x55, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x13.toByte(), 0x14.toByte())),  // Secondary air valve Output
        ErrorDetail(0x5D, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x24.toByte(), 0x35.toByte())),  // Tank-ventilation functional check
        ErrorDetail(0x62, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x0B.toByte())),  // Tank ventilation valve Output
        ErrorDetail(0x65, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x20.toByte(), 0x21.toByte())),  // monitoring torque comparison level 2
        ErrorDetail(0x66, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x0B.toByte())),  // interface MFL
        ErrorDetail(0x67, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x1F.toByte(), 0x1E.toByte())),  // monitoring Computer funktiom
        ErrorDetail(0x68, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x0B.toByte(), 0x14.toByte())),  // Switch Clutch
        ErrorDetail(0x69, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x1F.toByte(), 0x22.toByte())),  // RAM-Test faulty
        ErrorDetail(0x6A, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x0B.toByte(), 0x14.toByte())),  // Switch Brake
        ErrorDetail(0x6B, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x1F.toByte(), 0x22.toByte())),  // ROM-Test faulty
        ErrorDetail(0x6C, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x1F.toByte(), 0x22.toByte())),  // Reset from Computer monitoring
        ErrorDetail(0x6D, byteArrayOf(0x0A.toByte(), 0x14.toByte(), 0x24.toByte(), 0x12.toByte())),  // Battery Voltage
        ErrorDetail(0x6E, byteArrayOf(0x0A.toByte(), 0x23.toByte(), 0x1A.toByte(), 0x12.toByte())),  // torque limitation level 1
        ErrorDetail(0x6F, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x24.toByte(), 0x14.toByte())),  // Crankshaft sensor
        ErrorDetail(0x70, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x24.toByte(), 0x14.toByte())),  // Ref. marking generator
        ErrorDetail(0x71, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x24.toByte(), 0x14.toByte())),  // Camshaft sensor
        ErrorDetail(0x72, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x24.toByte(), 0x14.toByte())),  // Camshaft sensor Bank 2
        ErrorDetail(0x73, byteArrayOf(0x0A.toByte(), 0x13.toByte(), 0x15.toByte(), 0x71.toByte())),  // Hot film air mass meter
        ErrorDetail(0x75, byteArrayOf(0x0A.toByte(), 0x15.toByte(), 0x26.toByte(), 0x27.toByte())),  // DK-Potentiometer
        ErrorDetail(0x76, byteArrayOf(0x0A.toByte(), 0x28.toByte(), 0x24.toByte(), 0x11.toByte())),  // Throttle-valve potentiometer 1
        ErrorDetail(0x77, byteArrayOf(0x0A.toByte(), 0x28.toByte(), 0x24.toByte(), 0x11.toByte())),  // Throttle-valve potentiometer 2
        ErrorDetail(0x78, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x70.toByte(), 0x14.toByte())),  // Vehicle speed
        ErrorDetail(0x79, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x0B.toByte(), 0x8C.toByte())),  // Wheel sensor failure
        ErrorDetail(0x7B, byteArrayOf(0x25.toByte(), 0x13.toByte(), 0x24.toByte(), 0x72.toByte())),  // Engine temperature
        ErrorDetail(0x7C, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x24.toByte(), 0x73.toByte())),  // Intake air temperature
        ErrorDetail(0x7D, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x24.toByte(), 0x74.toByte())),  // Temperature sensor Radiator outlet
        ErrorDetail(0x82, byteArrayOf(0x14.toByte(), 0x13.toByte(), 0x15.toByte(), 0x28.toByte())),  // DK Position monitoring
        ErrorDetail(0x83, byteArrayOf(0x14.toByte(), 0x13.toByte(), 0x15.toByte(), 0x28.toByte())),  // DK-controller Regulator area
        ErrorDetail(0x84, byteArrayOf(0x14.toByte(), 0x12.toByte(), 0x15.toByte(), 0x28.toByte())),  // DK-controller activation
        ErrorDetail(0x85, byteArrayOf(0x14.toByte(), 0x13.toByte(), 0x15.toByte(), 0x64.toByte())),  // Suspension check DK-controller
        ErrorDetail(0x86, byteArrayOf(0x14.toByte(), 0x13.toByte(), 0x26.toByte(), 0x65.toByte())),  // Test lower at stop
        ErrorDetail(0x87, byteArrayOf(0x14.toByte(), 0x13.toByte(), 0x26.toByte(), 0x65.toByte())),  // DK-controller Error to Amplifiers adjustment
        ErrorDetail(0x88, byteArrayOf(0x14.toByte(), 0x13.toByte(), 0x64.toByte(), 0x76.toByte())),  // Test emergency air position
        ErrorDetail(0x8B, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x13.toByte(), 0x74.toByte())),  // Thermostat jammed
        ErrorDetail(0x8C, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x13.toByte(), 0x14.toByte())),  // Output Thermostat Map cooling
        ErrorDetail(0x8D, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x6B.toByte())),  // Output Engine fan
        ErrorDetail(0x8E, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x0B.toByte())),  // Output Exhaust flap
        ErrorDetail(0x94, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x8C.toByte())),  // EWS3.3 interface DME-EWS
        ErrorDetail(0x96, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x05.toByte())),  // EV1 from cyl. 1
        ErrorDetail(0x97, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x07.toByte())),  // EV2 from cyl. 5
        ErrorDetail(0x98, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x05.toByte())),  // EV3 from cyl. 4
        ErrorDetail(0x99, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x07.toByte())),  // EV4 from cyl. 8
        ErrorDetail(0x9A, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x07.toByte())),  // EV5 from cyl. 6
        ErrorDetail(0x9B, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x05.toByte())),  // EV6 from cyl. 3
        ErrorDetail(0x9C, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x07.toByte())),  // EV7 from cyl. 7
        ErrorDetail(0x9D, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x05.toByte())),  // EV8 from cyl. 2
        ErrorDetail(0xA3, byteArrayOf(0x11.toByte(), 0x15.toByte(), 0x13.toByte(), 0x35.toByte())),  // Diagnosis DK/HFM-Adjustment
        ErrorDetail(0xA4, byteArrayOf(0x0A.toByte(), 0x0B.toByte(), 0x24.toByte(), 0x75.toByte())),  // Pressure sensor Ambient
        ErrorDetail(0xA5, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x6C.toByte())),  // Output VANOS
        ErrorDetail(0xA6, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x6D.toByte())),  // Output VANOS Bank 2
        ErrorDetail(0xA7, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x0B.toByte())),  // Output Fuel pumps-Relay
        ErrorDetail(0xA8, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x0B.toByte())),  // Output MIL
        ErrorDetail(0xAA, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x0B.toByte())),  // Output Air-conditioning compressor eable A/C control unit
        ErrorDetail(0xB7, byteArrayOf(0x0A.toByte(), 0x35.toByte(), 0x24.toByte(), 0x14.toByte())),  // LDP Diagnosis
        ErrorDetail(0xB8, byteArrayOf(0x0A.toByte(), 0x35.toByte(), 0x24.toByte(), 0x14.toByte())),  // LDP System
        ErrorDetail(0xB9, byteArrayOf(0x0A.toByte(), 0x35.toByte(), 0x24.toByte(), 0x14.toByte())),  // LDP Modul
        ErrorDetail(0xBA, byteArrayOf(0x0A.toByte(), 0x14.toByte(), 0x24.toByte(), 0x0B.toByte())),  // DM-TL pump motor output-stage
        ErrorDetail(0xBB, byteArrayOf(0x96.toByte(), 0x97.toByte(), 0x98.toByte(), 0x9A.toByte())),  // DM-TL Leak (0,5mm)
        ErrorDetail(0xBC, byteArrayOf(0x96.toByte(), 0x97.toByte(), 0x98.toByte(), 0x9A.toByte())),  // DM-TL Leak (1,0mm)
        ErrorDetail(0xBD, byteArrayOf(0x98.toByte(), 0x96.toByte(), 0x97.toByte(), 0x99.toByte())),  // DM-TL Modul
        ErrorDetail(0xCC, byteArrayOf(0x0A.toByte(), 0x14.toByte(), 0x00.toByte(), 0x00.toByte())),  // EWS3.3 Exchange Code-stored
        ErrorDetail(0xD2, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x13.toByte())),  // Knock sensor1
        ErrorDetail(0xD3, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x13.toByte())),  // Knock sensor2
        ErrorDetail(0xD4, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x13.toByte())),  // Knock sensor3
        ErrorDetail(0xD5, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x13.toByte())),  // Knock sensor4
        ErrorDetail(0xD6, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x80.toByte())),  // Knock regulation Zero test
        ErrorDetail(0xD7, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x81.toByte())),  // Knock regulation Offset
        ErrorDetail(0xD8, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x77.toByte(), 0x81.toByte())),  // Knock regulation Test impulse
        ErrorDetail(0xDB, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x8C.toByte())),  // CAN-Timeout TCU
        ErrorDetail(0xDC, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x8C.toByte())),  // CAN-Timeout EGS
        ErrorDetail(0xDD, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x8C.toByte())),  // CAN-Timeout ASC/DSC
        ErrorDetail(0xDE, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x8C.toByte())),  // CAN-Timeout Instrument cluster
        ErrorDetail(0xDF, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x8C.toByte())),  // CAN-Timeout ACC
        ErrorDetail(0xE0, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x0B.toByte())),  // Plausibility MSR-Intervention
        ErrorDetail(0xE1, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x0B.toByte())),  // Plausibility ACC-Intervention
        ErrorDetail(0xE2, byteArrayOf(0x0A.toByte(), 0x3C.toByte(), 0x14.toByte(), 0x0B.toByte())),  // Plausibility gas level
        ErrorDetail(0xE5, byteArrayOf(0x00.toByte(), 0x00.toByte(), 0x1B.toByte(), 0x1D.toByte())),  // comparison supply PWG
        ErrorDetail(0xE6, byteArrayOf(0x0A.toByte(), 0x23.toByte(), 0x1B.toByte(), 0x1D.toByte())),  // Pedal-travel
        ErrorDetail(0xE7, byteArrayOf(0x0A.toByte(), 0x23.toByte(), 0x1B.toByte(), 0x1D.toByte())),  // Pedal-travel Potentiometer1
        ErrorDetail(0xE8, byteArrayOf(0x0A.toByte(), 0x23.toByte(), 0x1B.toByte(), 0x1D.toByte())),  // Pedal-travel Potentiometer2
        ErrorDetail(0xE9, byteArrayOf(0x0A.toByte(), 0x12.toByte(), 0x14.toByte(), 0x0B.toByte())),  // Starter automatic Output
        ErrorDetail(0xEA, byteArrayOf(0x0A.toByte(), 0x24.toByte(), 0x14.toByte(), 0x12.toByte())),  // Starter automatic Input
        ErrorDetail(0xED, byteArrayOf(0x0A.toByte(), 0x1A.toByte(), 0x14.toByte(), 0x0B.toByte()))   // Starter automatic
    )

    final val errorDetailMatrix: List<ErrorFrameDetail> = listOf(
        ErrorFrameDetail(0x00, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x01, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x02, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x03, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x04, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x3D.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x4C.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x05, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x3E.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x4C.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x0A, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x02.toByte(), 0x7F.toByte(), 0x81.toByte(), 0x49.toByte(), 0x06.toByte(), 0x80.toByte(), 0x82.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x0C, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x02.toByte(), 0x7F.toByte(), 0x83.toByte(), 0x49.toByte(), 0x06.toByte(), 0x80.toByte(), 0x84.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x0D, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x3E.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x4D.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x0E, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x3E.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x4D.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x0F, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x85.toByte(), 0x87.toByte(), 0x03.toByte(), 0x04.toByte(), 0x86.toByte(), 0x88.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x10, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x85.toByte(), 0x87.toByte(), 0x03.toByte(), 0x04.toByte(), 0x86.toByte(), 0x88.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x11, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x12, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x02.toByte(), 0x7F.toByte(), 0x81.toByte(), 0x49.toByte(), 0x06.toByte(), 0x80.toByte(), 0x82.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x14, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x02.toByte(), 0x7F.toByte(), 0x83.toByte(), 0x49.toByte(), 0x06.toByte(), 0x80.toByte(), 0x84.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x15, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x85.toByte(), 0x87.toByte(), 0x03.toByte(), 0x04.toByte(), 0x86.toByte(), 0x88.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x16, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x85.toByte(), 0x87.toByte(), 0x03.toByte(), 0x04.toByte(), 0x86.toByte(), 0x88.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x17, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x18, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3F.toByte(), 0x40.toByte(), 0x03.toByte(), 0x04.toByte(), 0x4E.toByte(), 0x4F.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x19, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3F.toByte(), 0x40.toByte(), 0x03.toByte(), 0x04.toByte(), 0x4E.toByte(), 0x4F.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x1A, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3F.toByte(), 0x40.toByte(), 0x03.toByte(), 0x04.toByte(), 0x4E.toByte(), 0x4F.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x1B, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3F.toByte(), 0x40.toByte(), 0x03.toByte(), 0x04.toByte(), 0x4E.toByte(), 0x4F.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x1C, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3F.toByte(), 0x40.toByte(), 0x03.toByte(), 0x04.toByte(), 0x4E.toByte(), 0x4F.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x1D, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3F.toByte(), 0x40.toByte(), 0x03.toByte(), 0x04.toByte(), 0x4E.toByte(), 0x4F.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x1E, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x1F, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x20, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x89.toByte(), 0x8B.toByte(), 0x03.toByte(), 0x04.toByte(), 0x8A.toByte(), 0x8C.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x21, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x29.toByte(), 0x2B.toByte(), 0x2A.toByte(), 0x05.toByte(), 0x2C.toByte(), 0x2E.toByte(), 0x2D.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x22, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x29.toByte(), 0x2B.toByte(), 0x2A.toByte(), 0x05.toByte(), 0x2C.toByte(), 0x2E.toByte(), 0x2D.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x27, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1F.toByte(), 0x1C.toByte(), 0x1E.toByte(), 0x1D.toByte(), 0x28.toByte(), 0x25.toByte(), 0x27.toByte(), 0x26.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x28, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x2D, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x32, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x31.toByte(), 0x30.toByte(), 0x03.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x33.toByte(), 0x07.toByte(), 0x32.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x33, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x31.toByte(), 0x30.toByte(), 0x03.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x33.toByte(), 0x07.toByte(), 0x32.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x34, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x31.toByte(), 0x30.toByte(), 0x03.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x33.toByte(), 0x07.toByte(), 0x32.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x35, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x31.toByte(), 0x30.toByte(), 0x03.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x33.toByte(), 0x07.toByte(), 0x32.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x36, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x31.toByte(), 0x30.toByte(), 0x03.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x33.toByte(), 0x07.toByte(), 0x32.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x37, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x31.toByte(), 0x30.toByte(), 0x03.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x33.toByte(), 0x07.toByte(), 0x32.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x38, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x31.toByte(), 0x30.toByte(), 0x03.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x33.toByte(), 0x07.toByte(), 0x32.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x39, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x31.toByte(), 0x30.toByte(), 0x03.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x33.toByte(), 0x07.toByte(), 0x32.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x3E, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x31.toByte(), 0x30.toByte(), 0x03.toByte(), 0x2F.toByte(), 0x34.toByte(), 0x33.toByte(), 0x07.toByte(), 0x32.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x50, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x51, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x52, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x73.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x74.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x54, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x55, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x5D, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x62, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x65, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x75.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x76.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x66, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x53.toByte(), 0x54.toByte(), 0x55.toByte(), 0x56.toByte(), 0x57.toByte(), 0x58.toByte(), 0x59.toByte(), 0x5A.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x67, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x77.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x78.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x68, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x69, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x79.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x7A.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x6A, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x5B.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x5C.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x6B, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x7B.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x7C.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x6C, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x7D.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x7E.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x6D, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x89.toByte(), 0x8B.toByte(), 0x37.toByte(), 0x04.toByte(), 0x8A.toByte(), 0x8C.toByte(), 0x46.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x6E, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x6F, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x70, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x71, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x72, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x73, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x35.toByte(), 0x36.toByte(), 0x03.toByte(), 0x04.toByte(), 0x44.toByte(), 0x45.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x75, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x35.toByte(), 0x36.toByte(), 0x03.toByte(), 0x04.toByte(), 0x44.toByte(), 0x45.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x76, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x5D.toByte(), 0x5E.toByte(), 0x03.toByte(), 0x5F.toByte(), 0x60.toByte(), 0x61.toByte(), 0x07.toByte(), 0x62.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x77, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x63.toByte(), 0x64.toByte(), 0x03.toByte(), 0x5F.toByte(), 0x65.toByte(), 0x66.toByte(), 0x07.toByte(), 0x62.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x78, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x79, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x7B, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x41.toByte(), 0x42.toByte(), 0x03.toByte(), 0x43.toByte(), 0x50.toByte(), 0x51.toByte(), 0x07.toByte(), 0x52.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x7C, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x41.toByte(), 0x42.toByte(), 0x03.toByte(), 0x04.toByte(), 0x50.toByte(), 0x51.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x7D, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x41.toByte(), 0x42.toByte(), 0x03.toByte(), 0x04.toByte(), 0x50.toByte(), 0x51.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x7F, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x82, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x6B.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x6C.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x83, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x84, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x67.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x68.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x85, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x69.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x6A.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x86, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x6F.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x70.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x87, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x71.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x72.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x88, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x6D.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x6E.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x8B, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x8C, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x8D, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x8E, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x94, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x19.toByte(), 0x1B.toByte(), 0x1A.toByte(), 0x05.toByte(), 0x22.toByte(), 0x24.toByte(), 0x23.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x96, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x97, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x98, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x99, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x9A, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x9B, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x9C, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0x9D, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xA3, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xA4, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xA5, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xA6, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xA7, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xA8, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xAA, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xB7, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x11.toByte(), 0x12.toByte(), 0x03.toByte(), 0x04.toByte(), 0x13.toByte(), 0x14.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xB8, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x0F.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x10.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xB9, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x03.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x07.toByte(), 0x0E.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xBA, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xBB, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xBC, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x11.toByte(), 0x12.toByte(), 0x03.toByte(), 0x04.toByte(), 0x13.toByte(), 0x14.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xBD, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xC9, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xCC, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x17.toByte(), 0x18.toByte(), 0x04.toByte(), 0x05.toByte(), 0x20.toByte(), 0x21.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xD2, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x38.toByte(), 0x39.toByte(), 0x03.toByte(), 0x04.toByte(), 0x47.toByte(), 0x48.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xD3, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x38.toByte(), 0x39.toByte(), 0x03.toByte(), 0x04.toByte(), 0x47.toByte(), 0x48.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xD4, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x38.toByte(), 0x39.toByte(), 0x03.toByte(), 0x04.toByte(), 0x47.toByte(), 0x48.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xD5, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x38.toByte(), 0x39.toByte(), 0x03.toByte(), 0x04.toByte(), 0x47.toByte(), 0x48.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xD6, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xD7, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xD8, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xDC, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xDB, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xDD, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xDE, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xDF, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xE0, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xE1, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xE2, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xE5, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xE6, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xE7, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x63.toByte(), 0x64.toByte(), 0x03.toByte(), 0x04.toByte(), 0x65.toByte(), 0x66.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xE8, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x63.toByte(), 0x64.toByte(), 0x03.toByte(), 0x04.toByte(), 0x65.toByte(), 0x66.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xE9, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x3A.toByte(), 0x3B.toByte(), 0x3C.toByte(), 0x04.toByte(), 0x49.toByte(), 0x4A.toByte(), 0x4B.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xEA, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte())),
        ErrorFrameDetail(0xED, byteArrayOf(0xF1.toByte(), 0xF2.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0xF9.toByte(), 0xFA.toByte(), 0xFB.toByte(), 0xFC.toByte(), 0xFD.toByte(), 0xFE.toByte(), 0x00.toByte(), 0x00.toByte()))
    )

    val freezeFrameMatrix: List<FreezeFrameDetail> = listOf(
        FreezeFrameDetail(0x01, "", 0f, 0f, 0f),  // Regulator status Bank1
        FreezeFrameDetail(0x02, "", 0f, 0f, 0f),  // Regulator status Bank2
        FreezeFrameDetail(0x03, "%", 0.3906f, 0f, 0f),  // Calculated Load
        FreezeFrameDetail(0x04, "Grd C", 1f, -40f, 0f),  // Engine temperature
        FreezeFrameDetail(0x05, "", 0.0078f, 0f, 0f),  // Control factor Bank1
        FreezeFrameDetail(0x06, "", 0.0078f, 0f, 0f),  // Adaption factor Bank1
        FreezeFrameDetail(0x07, "", 0.0078f, 0f, 0f),  // Control factor Bank2
        FreezeFrameDetail(0x08, "", 0.0078f, 0f, 0f),  // Adaption factor Bank2
        FreezeFrameDetail(0x0A, "/min", 40f, 0f, 0f),  // Engine speed
        FreezeFrameDetail(0x0B, "km/h", 1f, 0f, 0f),  // Vehicle speed
        FreezeFrameDetail(0x11, "kg/h", 4f, 0f, 0f),  // air mass meter
        FreezeFrameDetail(0x12, "Grd C", 0.75f, -48f, 0f),  // Engine temperature
        FreezeFrameDetail(0x13, "Grd C", 0.75f, -48f, 0f),  // Intake air temperature
        FreezeFrameDetail(0x14, "V", 0.0942f, 0f, 0f),  // Supply voltage
        FreezeFrameDetail(0x15, "% DK", 0.3922f, 0f, 0f),  // angle DK oriented to DK-at stop
        FreezeFrameDetail(0x16, "V", 0.00522f, -0.2f, 0f),  // Sensor voltage v. Cat. Bank1
        FreezeFrameDetail(0x17, "V", 0.00522f, -0.2f, 0f),  // Sensor voltage h. Cat. Bank1
        FreezeFrameDetail(0x18, "V", 0.00522f, -0.2f, 0f),  // Sensor voltage v. Cat. Bank2
        FreezeFrameDetail(0x19, "V", 0.00522f, -0.2f, 0f),  // Sensor voltage h. Cat. Bank2
        FreezeFrameDetail(0x1A, "%", 0.75f, 0f, 0f),  // relative air charged (rl)
        FreezeFrameDetail(0x1B, "V", 0.0195f, 0f, 0f),  // voltage PWG Potentiometer 1
        FreezeFrameDetail(0x1C, "V", 0.0195f, 0f, 0f),  // voltage PWG Potentiometer 2
        FreezeFrameDetail(0x1D, "V", 0.0195f, 0f, 0f),  // doubled voltage PWG 2
        FreezeFrameDetail(0x1E, "bin", 1f, 0f, 0f),  // ska-path
        FreezeFrameDetail(0x1F, "bin", 1f, 0f, 0f),  // egas-path
        FreezeFrameDetail(0x20, "bin", 1f, 0f, 0f),  // m-path
        FreezeFrameDetail(0x21, "%", 0.3906f, 0f, 0f),  // mi_duf
        FreezeFrameDetail(0x22, "bin", 1f, 0f, 0f),  // rst-path
        FreezeFrameDetail(0x23, "%", 0.3922f, 0f, 0f),  // standardizes accelerator-pedal angle
        FreezeFrameDetail(0x24, "Grd C", 0.75f, -48f, 0f),  // Ambient temperature
        FreezeFrameDetail(0x25, "Grd C", 0.75f, -48f, 0f),  // Engine temperature replacement value from model
        FreezeFrameDetail(0x26, "V", 0.0195f, 0f, 0f),  // voltage DK-Potentiometer 1
        FreezeFrameDetail(0x27, "V", 0.0195f, 0f, 0f),  // voltage DK-Potentiometer 2
        FreezeFrameDetail(0x28, "%", 0.3906f, 0f, 0f),  // Setpoint DK-angle oriented to lower stop
        FreezeFrameDetail(0x29, "Grd C", 5f, -50f, 0f),  // exhaust temperature befor Cat. from model
        FreezeFrameDetail(0x2A, "Grd C", 5f, -50f, 0f),  // exhaust temperature from Bank2 before cat. taken out of the Model
        FreezeFrameDetail(0x2B, "Grd C", 5f, -50f, 0f),  // Cat.-temperature from model
        FreezeFrameDetail(0x2C, "Grd C", 5f, -50f, 0f),  // Cat.-temperature Bank 2 from model
        FreezeFrameDetail(0x2D, "V", 0.0195f, 0f, 0f),  // uhsv
        FreezeFrameDetail(0x2E, "V", 0.0195f, 0f, 0f),  // uhsv2
        FreezeFrameDetail(0x2F, "V", 0.0195f, 0f, 0f),  // uhsh
        FreezeFrameDetail(0x30, "V", 0.0195f, 0f, 0f),  // uhsh2
        FreezeFrameDetail(0x31, "Ohm", 64f, 0f, 0f),  // rinv
        FreezeFrameDetail(0x32, "Ohm", 64f, 0f, 0f),  // rinv2
        FreezeFrameDetail(0x33, "Ohm", 64f, 0f, 0f),  // rinh
        FreezeFrameDetail(0x34, "Ohm", 64f, 0f, 0f),  // rinh2
        FreezeFrameDetail(0x35, "hPa", 5f, 0f, 0f),  // Ambient-pressure
        FreezeFrameDetail(0x36, "s", 0.04f, 0f, 0f),  // tpsvkmf
        FreezeFrameDetail(0x37, "s", 0.04f, 0f, 0f),  // tpsvkmf2
        FreezeFrameDetail(0x38, "", 0.0078f, 0f, 0f),  // fr
        FreezeFrameDetail(0x39, "", 0.0078f, 0f, 0f),  // fra
        FreezeFrameDetail(0x3A, "", 0.0078f, 0f, 0f),  // fr2
        FreezeFrameDetail(0x3B, "", 0.0078f, 0f, 0f),  // fra2
        FreezeFrameDetail(0x3C, "l", 1.0f, 0f, 0f),  // gas level
        FreezeFrameDetail(0x3D, "%", 0.75f, 0f, 0f),  // rl
        FreezeFrameDetail(0x64, "%", 0.3922f, 0f, 0f),  // wdknlp
        FreezeFrameDetail(0x65, "V", 0.0195f, 0f, 0f),  // udkp1a_u
        FreezeFrameDetail(0x66, "V/s", 23.844f, 0f, 1f),  // igokr_u
        FreezeFrameDetail(0x67, "V", 0.0195f, 0f, 0f),  // uusvk_u
        FreezeFrameDetail(0x68, "V", 0.0195f, 0f, 0f),  // uusvk2_u
        FreezeFrameDetail(0x69, "V", 0.0195f, 0f, 0f),  // uushk_u
        FreezeFrameDetail(0x6A, "V", 0.0195f, 0f, 0f),  // uushk2_u
        FreezeFrameDetail(0x6B, "%", 0.3922f, 0f, 0f),  // taml
        FreezeFrameDetail(0x6C, "Grad KW", 1f, 0f, 1f),  // wnwi1_u
        FreezeFrameDetail(0x6D, "Grad KW", 1f, 0f, 1f),  // wnwi2_u
        FreezeFrameDetail(0x6E, "% TV", 0.3922f, 0f, 0f),  // tanwrhf_0_A
        FreezeFrameDetail(0x6F, "% TV", 0.3922f, 0f, 0f),  // tanwrhf_1_A
        FreezeFrameDetail(0x70, "km/h", 1.25f, 0f, 0f),  // vfzg2
        FreezeFrameDetail(0x71, "V", 0.0196f, 0f, 0f),  // ADC HFM
        FreezeFrameDetail(0x72, "V", 0.0196f, 0f, 0f),  // ADC TM
        FreezeFrameDetail(0x73, "V", 0.0196f, 0f, 0f),  // ADC TA
        FreezeFrameDetail(0x74, "V", 0.0196f, 0f, 0f),  // ADC TKA
        FreezeFrameDetail(0x75, "V", 0.0196f, 0f, 0f),  // ADC DSU
        FreezeFrameDetail(0x76, "% DK", 0.3921f, 0f, 0f),  // Setpoint DK-angle at NLP-position
        FreezeFrameDetail(0x77, "V", 0.0196f, 0f, 0f),  // ikrmet
        FreezeFrameDetail(0x78, "Grd C", 5f, -50f, 0f),  // tkatmf
        FreezeFrameDetail(0x79, "Grd C", 5f, -50f, 0f),  // tkatmf2
        FreezeFrameDetail(0x7A, "Grd C", 5f, -50f, 0f),  // tabgmf
        FreezeFrameDetail(0x7B, "Grd C", 5f, -50f, 0f),  // tabgmf2
        FreezeFrameDetail(0x7C, "", 0.01f, 0f, 0f),  // phlsnv
        FreezeFrameDetail(0x7D, "", 0.01f, 0f, 0f),  // phlsnv2
        FreezeFrameDetail(0x7E, "", 0.01f, 0f, 0f),  // phlsnh
        FreezeFrameDetail(0x7F, "", 0.01f, 0f, 0f),  // phlsnh2
        FreezeFrameDetail(0x80, "V/s", 23.844f, 0f, 1f),  // igod
        FreezeFrameDetail(0x81, "V", 0.0196f, 0f, 0f),  // ikrma
        FreezeFrameDetail(0x82, "", 0.0625f, 0f, 0f),  // lamsons
        FreezeFrameDetail(0x83, "", 0.0625f, 0f, 0f),  // lamsons2
        FreezeFrameDetail(0x84, "Grd C", 0.75f, -48f, 0f),  // tmst
        FreezeFrameDetail(0x85, "", 0.0078f, 0f, 0f),  // frm
        FreezeFrameDetail(0x86, "", 0.0078f, 0f, 0f),  // frm2
        FreezeFrameDetail(0x87, "", 0.0039f, 0f, 0f),  // mean value Probe after Cat. (ahkat)
        FreezeFrameDetail(0x88, "", 0.0039f, 0f, 0f),  // mean value of sensor behind cat. bank2(ahkat2)
        FreezeFrameDetail(0x89, "s", 0.01f, 0f, 1f),  // I-share delaies regulator switching(tvlrhi)
        FreezeFrameDetail(0x8A, "s", 0.01f, 0f, 1f),  // I-share delaies regulator switching(tvlrhi2)
        FreezeFrameDetail(0x8B, "", 0.0078f, 0f, 0f),  // factor airtight TANS height (frhol_u)
        FreezeFrameDetail(0x8C, "s", 25.6f, 0f, 0f)  // Time after start (tnse_u)
    )
}

