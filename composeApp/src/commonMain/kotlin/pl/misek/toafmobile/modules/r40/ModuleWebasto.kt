package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.containers.WebastoFreezeFrameData
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import kotlinx.coroutines.CancellationException

private const val tag = "ModuleWebasto"
class ModuleWebasto( protocolDS2: ProtocolDS2?) :
    ModuleDS2( protocolDS2, ModuleAddress.MODULE_WEBASTO, listOf(CarType.R40, CarType.L322)),
    IModuleControl, IModuleLiveData, IModuleEepromProgramming /*,IModuleCoding */ {

    override fun getKoinName() = KoinNames.ModuleR40Webasto

    override fun getImageResource() = Res.drawable.module_webasto

    override fun getModuleDescription()  = Res.string.module_description_webasto

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        var message = interfaceWrite(byteArrayOf(0x0B))
        try {
            if (message.isValid) {
                result[0x00] = message.answer.copyOfRange(3, message.answer.size)
            }

            message = interfaceWrite(byteArrayOf(0x06, 0x04, 0x02, 0xA5.toByte(), 0x03))
            if (message.isValid) {
                result[0x01] = message.answer.copyOfRange(3, message.answer.size)
            }
            message = interfaceWrite(byteArrayOf(0x06, 0x04, 0x02, 0xAC.toByte(), 0x02))
            if (message.isValid) {
                result[0x02] = message.answer.copyOfRange(3, message.answer.size)
            }
            message = interfaceWrite(byteArrayOf(0x06, 0x03, 0x01, 0xFC.toByte(), 0x02))
            if (message.isValid) {
                result[0x03] = message.answer.copyOfRange(3, message.answer.size)
            }
            val counter = result[0x02]?.let { readWord(it) }
            val copy = result[0x03]?.let { readWord(it) }
            if (counter != null && copy != null) {
                val difference = counter - copy
                result[0x04] = byteArrayOf((difference shr 8).toByte(), difference.toByte())
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception)
        {
            Logger.e {"$tag [readLiveData()], $ex, $message"}
        }

        return result.ifEmpty { null }
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        when (controlElement.block) {
            0x34 -> return interfaceWrite(
                byteArrayOf(
                    0x34,
                    (controlElement.controlSetValue!!.value / 2).toByte()
                )
            ).isValid

            0x0C -> {
                interfaceWrite(byteArrayOf(0x9E.toByte()))
                return interfaceWrite(byteArrayOf(0x0C.toByte(), 0x00)).isValid
            }
            0x9E -> {
                return interfaceWrite(byteArrayOf(0x9E.toByte())).isValid &&
                    interfaceWrite(byteArrayOf(0x0C.toByte(), 0xE0.toByte())).isValid
            }

            0x94 -> return interfaceWrite(byteArrayOf(0x94.toByte(), 0x02)).isValid
        }

        return false
    }

    private fun readWord(data: ByteArray): Int? =
        if (data.size >= 2) ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF) else null

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>)= true

    override suspend fun getAllErrors(): List<ModuleError>? {
        val errorDescriptionCodes = byteArrayOf(0x01, 0x02, 0x04, 0x08, 0x40, 0x80.toByte())
        val first = interfaceWrite(byteArrayOf(0x04, 0x01))
        if (!first.isValid) return null
        val result: MutableList<ModuleError> = ArrayList()
        val count = (first.answer.getOrNull(3)?.toInt() ?: 0) and 0xFF
        for (i in 1..count.coerceAtMost(3)) {
            val message = if (i == 1) first else interfaceWrite(byteArrayOf(0x04, i.toByte()))
            if (!message.isValid || message.answer.size <= 8) continue
            try {
                val errorCode = String.format("%02X", message.answer[4])
                val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[6].toInt() and 0xFF)))
                errorDescription.add(
                    Pair(
                        Res.string.webasto_error_detail_cooling_temperature,
                        (message.answer[8].toInt() and 0xFF)
                    )
                )
                operatingStateResource(message.answer[7].toInt() and 0xFF)?.let {
                    errorDescription.add(Pair(Res.string.webasto_error_detail_status, it))
                }

                for (errorDescriptionCode in errorDescriptionCodes) {
                    if ((message.answer[5].toInt() and errorDescriptionCode.toInt()) == errorDescriptionCode.toInt()) {
                        ResourceMapper
                            .stringResource(
                                String.format(
                                    "webasto_error_detail_%02X",
                                    errorDescriptionCode
                                )
                            )
                            ?.let { errorDescription.add(Pair(it, null)) }
                    }
                }

                result.add(
                    ModuleError(
                        ModuleAddress.MODULE_WEBASTO,
                        errorCode,
                        errorDescription
                    )
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getAllErrors], $ex, $message"}
            }
        }
        return result
    }

    private fun operatingStateResource(state: Int): StringResource? {
        val data = WebastoFreezeFrameData().FreezeFrameData
        val name = data[0x00]?.get(state) ?: data[0x01]?.get(state) ?: return null
        return ResourceMapper.stringResource("webasto_error_status_${name.lowercase()}")
    }

    private suspend fun moduleInit(): Boolean {
        val tab = byteArrayOf(0x01, 0x04, 0x05)
        for (b in tab) {
            if (!interfaceWrite(byteArrayOf(0x07, 0x01, b, 0xFF.toByte())).isValid) return false
        }
        return true
    }

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        val message = interfaceWrite(
            byteArrayOf(0x06, 0x03, 0x00, (address and 0xFF).toByte(), size.toByte()), 500
        )
        try {
            return if (message.isValid) message.answer.copyOfRange(0x03, 3 + size) else null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readEeprom()], $ex, $message"}
            return null
        }
    }

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val dataToWrite =  byteArrayOf(0x07, 0x03, 0x00, (address and 0xFF).toByte(), (data.size).toByte()) + data
        return interfaceWrite(dataToWrite).isValid
    }

    override fun getEepromSize(): Int {
        return 256
    }

    override fun getEepromReadSize(): Int {
        return 8
    }

    override fun getEepromWriteSize(): Int {
        return 8 / 2
    }
}
