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
        //byte[] fakeAnswer = new byte[] {(byte)0x6B,(byte)0x0D,(byte)0xA0,(byte)0x12,(byte)0x44,(byte)0xAA,(byte)0xC4,(byte)0x64,(byte)0x65,(byte)0xAB,(byte)0xBC,(byte)0x40};
        val result: MutableMap<Int, ByteArray> = HashMap()
        var message = interfaceWrite(byteArrayOf(0x0B))
        //message.isValid() = true;
        //message.answer = fakeAnswer;
        try {
            if (message.isValid) {
                result[0x00] = message.answer.copyOfRange(3, message.answer.size)
            }

            message = interfaceWrite(byteArrayOf(0x06, 0x04, 0x02, 0xA5.toByte(), 0x03))
            if (message.isValid) {
                result[0x01] = message.answer.copyOfRange(3, message.answer.size)
            }
            message = interfaceWrite(byteArrayOf(0x06, 0x04, 0x02, 0xAC.toByte(), 0x03))
            if (message.isValid) {
                result[0x02] = message.answer.copyOfRange(3, message.answer.size)
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

            0x0C -> return interfaceWrite(byteArrayOf(0x0C.toByte(), 0x00)).isValid
            0x9E -> {
                if (interfaceWrite(byteArrayOf(0x9E.toByte())).isValid) return interfaceWrite(
                    byteArrayOf(0x0C.toByte(), 0xE0.toByte())
                ).isValid
                return false
            }

            0x94 -> return interfaceWrite(byteArrayOf(0x94.toByte(), 0x02)).isValid
        }

        return false
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>)= true

    override suspend fun getAllErrors(): List<ModuleError>? {
        val errorDescriptionCodes = byteArrayOf(0x01, 0x02, 0x04, 0x08, 0x40, 0x80.toByte())

        var result: MutableList<ModuleError>? = null
        for (i in 1..3) {
            val message = interfaceWrite(byteArrayOf(0x04, i.toByte()))
            if (message.isValid) {
                try {
                    if (result == null) result = ArrayList()
                    if (message.answer[3].toInt() != 0x00 && message.answer.size > 5) {
                        val errorCode = String.format("%02X", message.answer[4])
                        val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                        errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[6].toInt() and 0xFF)))
                        errorDescription.add(
                            Pair(
                                Res.string.webasto_error_detail_cooling_temperature,
                                (message.answer[8].toInt() and 0xFF)
                            )
                        )
                        val statusByte = message.answer[7].toInt() and 0xFF
                        val webastoFreezeFrameData = WebastoFreezeFrameData().FreezeFrameData[0x00]
                        if (webastoFreezeFrameData != null && webastoFreezeFrameData.containsKey(
                                statusByte
                            )
                        ) {
                            val status = webastoFreezeFrameData[statusByte]
                            ResourceMapper.stringResource("webasto_error_status_$status")
                                ?.let {
                                    errorDescription.add(
                                        Pair(
                                            Res.string.webasto_error_detail_status,
                                            it
                                        )
                                    )
                                }
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
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex : Exception)
                {
                    Logger.e {"$tag [getAllErrors], $ex, $message"}
                }
            }
        }
        return result
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
            byteArrayOf(0x06, 0x03, 0x00, (address and 0xFF).toByte(), size.toByte())
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
