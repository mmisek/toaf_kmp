package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Locale
import kotlinx.coroutines.CancellationException
import pl.misek.toafmobile.modules.enums.ModuleFeature
import kotlin.time.Duration.Companion.milliseconds

private const val tag = "ModulePDC"
open class ModulePDC protected constructor(
    protocolDS2: ProtocolDS2?,
    moduleAddress: ModuleAddress,
    carTypeList: List<CarType>
) :
    ModuleDS2( protocolDS2, moduleAddress, carTypeList),
    IModuleCoding, IModuleLiveData, IModuleEepromProgramming {

    constructor(protocolDS2: ProtocolDS2?) : this(protocolDS2, ModuleAddress.MODULE_PDC, listOf(CarType.R40, CarType.RJO))

    private val errorsMatrix = arrayOf(
        intArrayOf(0x01, 0x02, 0x03, 0x04, 0xFF, 0xFF, 0x05, 0x06),
        intArrayOf(0x01, 0x02, 0x03, 0x04, 0xFF, 0xFF, 0x05, 0x06),
        intArrayOf(0x01, 0x02, 0x03, 0x04, 0xFF, 0xFF, 0x05, 0x06),
        intArrayOf(0x01, 0x02, 0x03, 0x04, 0xFF, 0xFF, 0x05, 0x06),
        intArrayOf(0x01, 0x02, 0x03, 0x04, 0xFF, 0xFF, 0x05, 0x06),
        intArrayOf(0x01, 0x02, 0x03, 0x04, 0xFF, 0xFF, 0x05, 0x06),
        intArrayOf(0x01, 0x02, 0x03, 0x04, 0xFF, 0xFF, 0x05, 0x06),
        intArrayOf(0x07, 0x08, 0x09, 0xFF, 0xFF, 0xFF, 0x0A, 0x0B),
        intArrayOf(0x07, 0x08, 0x09, 0xFF, 0xFF, 0xFF, 0x0A, 0x0B),
        intArrayOf(0x07, 0x08, 0x09, 0xFF, 0xFF, 0xFF, 0x0A, 0x0B),
        intArrayOf(0x07, 0x08, 0x09, 0xFF, 0xFF, 0xFF, 0x0A, 0x0B),
        intArrayOf(0x07, 0x08, 0x09, 0xFF, 0xFF, 0xFF, 0x0A, 0x0B),
        intArrayOf(0x07, 0x08, 0x09, 0xFF, 0xFF, 0xFF, 0x0A, 0x0B),
        intArrayOf(0x07, 0x08, 0x09, 0xFF, 0xFF, 0xFF, 0x0A, 0x0B),
        intArrayOf(0x07, 0x08, 0x09, 0xFF, 0xFF, 0xFF, 0x0A, 0x0B),
        intArrayOf(0x07, 0x08, 0x09, 0xFF, 0xFF, 0xFF, 0x0A, 0x0B),
        intArrayOf(0x0C, 0x0D, 0x0E, 0xFF, 0xFF, 0xFF, 0x0F, 0x10)
    )
    override fun getKoinName() = KoinNames.ModuleR40Pdc

    override fun getImageResource() = Res.drawable.module_pdc

    override fun getModuleDescription() = Res.string.module_description_pdc

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val message = interfaceWrite(byteArrayOf(0x1B))
        try {
            if (message.isValid) {
                result[0x00] = message.answer.copyOfRange(3, message.answer.size)
                return result
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readLiveData], $ex, $message"}
        }
        return null
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = true

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        for (i in 0..0x7) {
            val message = interfaceWrite(byteArrayOf(0x08, i.toByte()), 300)
            try {
                if (message.isValid) result.add(
                    message.answer.copyOfRange(3, message.answer.size - 1)
                )
                else return null
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [readCoding], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        for (i in dataToSave.indices) {
            val message = interfaceWrite(
                arrayOf(
                    byteArrayOf(
                        0x09,
                        i.toByte()
                    ), dataToSave[i]
                )
            )
            if (!message.isValid) return false
        }
        return true
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        for (j in 0..1) {
            val message = interfaceWrite(byteArrayOf(0x04, (j + 1).toByte()))
            try {
                if (message.isValid) {
                    for (i in 0..6) {
                        if ((message.answer[6 + 3 * i].toInt() and 0xFF) > 0x00) {
                            val errorCode = String.format(Locale.getDefault(), "%02d", (j + 1) * 10 + i)
                            val errorDescription: MutableList<Pair<StringResource, Any?>> =
                                ArrayList()
                            errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[6 + 3 * i].toInt() and 0xFF)))
                            for (k in errorsMatrix[8 * j + i].indices) {
                                if ((message.answer[5 + 3 * i].toInt() and (0x01 shl k)) == (0x01 shl k)) {
                                    ResourceMapper
                                        .stringResource(
                                            String.format(
                                                "pdc_error_desc_%02d",
                                                errorsMatrix[8 * j + i][k]
                                            )
                                        )
                                        ?.let { errorDescription.add(Pair(it, null)) }
                                }
                            }
                            if (result == null) result = ArrayList()
                            result.add(
                                ModuleError(
                                    ModuleAddress.MODULE_PDC,
                                    errorCode,
                                    errorDescription
                                )
                            )
                        }
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getAllErrors], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        val message = interfaceWrite(
            byteArrayOf(
                0x06,
                0x03,
                0x00,
                (0xB6 + (address shr 8)).toByte(),
                (address and 0xFF).toByte(),
                size.toByte()
            ),
            500
        )
        try {
            return if (message.isValid) message.answer.copyOfRange(0x03, 3 + size) else null
        } catch (ex: CancellationException) {
            throw ex
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [readEeprom], $ex,$message"}
            return null
        }
    }

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val dataToWrite =
            byteArrayOf(
                0x07,
                0x03,
                0x00,
                (0xB6 + (address shr 8)).toByte(),
                (address and 0xFF).toByte(),
                (data.size).toByte()) + data
        return interfaceWrite(dataToWrite).isValid
    }

    override fun getEepromSize(): Int {
        return 0x0200
    }

    override fun getEepromReadSize(): Int {
        return 0x20
    }

    override fun getEepromWriteSize(): Int {
        return 0x08
    }

    override fun getNewFeatures(): Set<ModuleFeature> {
        return super.getNewFeatures() + ModuleFeature.EepromProgramming
    }
}
