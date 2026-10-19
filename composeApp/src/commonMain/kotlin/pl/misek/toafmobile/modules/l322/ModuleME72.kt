package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import pl.misek.toafmobile.modules.interfaces.AdvanceControlElement
import pl.misek.toafmobile.modules.interfaces.EwsSyncStep
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceControl
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEwsSync
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.r40.ModuleEWS
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Locale

private const val tag = "ModuleME72"
class ModuleME72(private var kwp: ProtocolKWP) :  IModule,
    IModuleControl,
    IModuleLiveData,
    IModuleEwsSync,
    IModuleAdvanceControl
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
        AdvanceControlElement(Res.string.advance_control_ews_sync_title) { userInfo, onShowResult ->
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
                    info.serial = String(answer.copyOfRange(24,34))
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
    private suspend fun readAIF() : Aif?
    {
        var address = 0x81FD54
        var aif = Aif("","")
        for (x in 0..14) {
            address += 0x2E * x
            val data = byteArrayOf(
                ((address shr 16) and 0xFF).toByte(),
                ((address shr 8) and 0xFF).toByte(),
                (address and 0xFF).toByte(),
                0x00,
                0x2E
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

