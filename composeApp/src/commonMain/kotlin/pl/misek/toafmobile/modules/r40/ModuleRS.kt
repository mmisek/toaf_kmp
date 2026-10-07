package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_rs
import toafmobile.composeapp.generated.resources.module_rs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import pl.misek.toafmobile.protocols.OBDMessage
import kotlin.time.Duration.Companion.milliseconds

private const val tag = "ModuleRS"
class ModuleRS( protocolDS2: ProtocolDS2?) : ModuleDS2( protocolDS2, ModuleAddress.MODULE_RS, listOf(CarType.R40)), IModuleControl, IModuleLiveData {

    override fun getKoinName() = KoinNames.ModuleR40Rs

    override fun getImageResource() = Res.drawable.module_rs

    override fun getModuleDescription() = Res.string.module_description_rs

    /**
     * STATUS_LESEN (1B), dane od answer[3] bez sumy kontrolnej:
     * [0] STAT_RSK (0 - wycieraczka w pozycji spoczynkowej), [1] STAT_VERSTAERKUNG [dB] w BCD,
     * [2] / [3] STAT_UEBERWISCHZEITPUNKT_1 / _2. Wzmocnienie przeliczane z BCD na liczbe.
     */
    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = mutableMapOf()
        val message = writeWithBusyRetry(byteArrayOf(0x1B))
        try {
            if (message.isValid) {
                val data = message.answer.copyOfRange(3, message.answer.size - 1)
                if (data.size > 1) {
                    val bcd = data[1].toInt() and 0xFF
                    data[1] = ((bcd shr 4) * 10 + (bcd and 0x0F)).toByte()
                }
                result[0x00] = data
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

    override suspend fun control(controlElement: ControlElement): Boolean {
        return controlElement.block == 0x00 && moduleInit()
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = true

    override suspend fun getAllErrors(): List<ModuleError> {
        val result: MutableList<ModuleError> = mutableListOf()
        val message = interfaceWrite(byteArrayOf(0x04))
        try {
            if (message.isValid) {
                val location = message.answer[3].toInt() and 0xFF
                if (location != 0x00) {
                    val errorCode = String.format("%02X", location)
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    // FS_LESEN: licznik dla bledu 03 - answer[4], dla 04 - answer[5], pozostale zawsze 1
                    val count = when (location) {
                        0x03 -> message.answer[4].toInt() and 0xFF
                        0x04 -> message.answer[5].toInt() and 0xFF
                        else -> 1
                    }
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount(count))
                    result.add(ModuleError(ModuleAddress.MODULE_RS, errorCode, errorDescription))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getAllErros], $ex, $message"}
        }
        return result
    }

    /** Ponawia telegram, gdy sterownik odpowiada BUSY (A1) - jak STATUS_LESEN (max 5 prob) */
    private suspend fun writeWithBusyRetry(data: ByteArray): OBDMessage {
        var message = interfaceWrite(data)
        var attempts = 1
        while (message.answerCode == ANSWER_BUSY && attempts < MAX_ATTEMPTS) {
            delay(BUSY_RETRY_DELAY_MS.milliseconds)
            message = interfaceWrite(data)
            attempts++
        }
        return message
    }

    private companion object {
        const val ANSWER_BUSY: Byte = 0xA1.toByte()
        const val MAX_ATTEMPTS = 5
        const val BUSY_RETRY_DELAY_MS = 100L
    }

    private suspend fun moduleInit(): Boolean {
        val tab = byteArrayOf(0x01, 0x04, 0x05)
        for (b in tab) {
            if (!interfaceWrite(byteArrayOf(0x07, 0x01, b, 0xFF.toByte())).isValid) return false
        }
        return true
    }
}
