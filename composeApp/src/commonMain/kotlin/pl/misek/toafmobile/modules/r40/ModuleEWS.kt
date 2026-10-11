package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.activities.composeModels.activities.AdvanceControlEwsKeys
import pl.misek.toafmobile.coding.ZcsCoding
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.containers.EwsKey
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.AdvanceControlElement
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceControl
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleEwsKeys
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleZCS
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.advance_control_ews_keys_title
import toafmobile.composeapp.generated.resources.module_description_ews
import toafmobile.composeapp.generated.resources.module_ews
import kotlinx.coroutines.CancellationException

private const val tag = "ModuleEWS"
class ModuleEWS(protocolDS2: ProtocolDS2?) :
    ModuleDS2(
        protocolDS2,
        ModuleAddress.MODULE_EWS,
        listOf(CarType.R40, CarType.L322)
    ),
    IModuleZCS, IModuleCoding, IModuleLiveData, IModuleAdvanceControl, IModuleEwsKeys {
    override fun getKoinName() = KoinNames.ModuleR40Ews

    override fun getImageResource() = Res.drawable.module_ews

    override fun getModuleDescription() = Res.string.module_description_ews

    override suspend fun readZCS(): ZcsCoding? {
        var message = interfaceWrite(byteArrayOf(0x69, 0x04))
        try {
            if (message.isValid) {
                var data = message.answer.copyOfRange(4, message.length - 1)
                message = interfaceWrite(byteArrayOf(0x69, 0x05))
                if (message.isValid) {
                    data += message.answer.copyOfRange(4, 8)
                    return ZcsCoding(data)
                }
            }
            return null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [readZCS], $ex, $message"}
            return null
        }
    }

    override suspend fun writeZCS(data: ZcsCoding): Boolean {
        var result: Boolean
        val d = data.bytes
        var dataToSend = byteArrayOf(0x6A, 0x04, 0x00) + d.copyOfRange(0, 16)
        var message = interfaceWrite(dataToSend)
        result = message.isValid
        message = interfaceWrite(byteArrayOf(0x69, 0x05))
        if (message.isValid) {
            val d2 = message.answer.copyOfRange(4, message.length - 1)
            d2[0] = d[16]
            d2[1] = d[17]
            d2[2] = d[18]
            d2[3] = d[19]
            dataToSend = byteArrayOf(0x6A, 0x05, 0x00) + d2
            message = interfaceWrite(dataToSend)
            result = result xor message.isValid
        } else result = false
        return result
    }

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        val message = interfaceWrite(byteArrayOf(0x08))
        try {
            if (message.isValid) result.add(
                message.answer.copyOfRange(3, message.answer.size - 1)
            )
            else return null
            return result
        } catch (ex: CancellationException) {
            throw ex
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [readCoding], $ex, $message"}
            return null
        }
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        if (dataToSave.size != 0x01) return false
        val message = interfaceWrite(
            arrayOf(
                byteArrayOf(0x09),
                dataToSave[0]
            )
        )
        return message.isValid
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        for (j in 0..1) {
            val message = interfaceWrite(byteArrayOf(0x04, j.toByte()))
            try {
                if (message.isValid && message.length > 5) {
                    for (i in 0 until (message.answer[3].toInt() and 0xFF)) {
                        val errorCode = String.format("%02X", message.answer[4 + 2 * i])
                        val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                        errorDescription.add(ResourceHelper.getErrorDescriptionCount((message.answer[5 + 2 * i].toInt() and 0xFF) and 0x1F))
                        errorDescription.add(ResourceHelper.getErrorDescriptionIsStatic((message.answer[5 + 2 * i].toInt() and 0x20) == 0x20))
                        if (result == null) result = ArrayList()
                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_EWS,
                                errorCode,
                                errorDescription
                            )
                        )
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e { "$tag [getAllErros], $ex, $message" }
            }
        }
        return result
    }


    override suspend fun getVIN(): String? {
        val message =  interfaceWrite(byteArrayOf(0x69, 0x01))
        try {
            return if (message.isValid) {
                ModulesFunction.hexToVinDecoder(
                    message.answer.copyOfRange(4, message.answer.size)
                )
            } else null
        } catch (ex: CancellationException) {
            throw ex
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [getVIN], $ex,$message"}
            return null
        }
    }

    @Suppress("DefaultLocale")
    override suspend fun getOdometer(): String? {
        val message =  interfaceWrite(byteArrayOf(0x69, 0x00))
        try {
            if (message.isValid) {
                val odometer =
                    ((message.answer[8].toInt() and 0xFF) shl 16) or ((message.answer[7].toInt() and 0xFF) shl 8) or (message.answer[6].toInt() and 0xFF)
                return String.format("%d", odometer)
            } else return null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [getOdometer],$ex, $message"}
            return null
        }
    }

    override suspend fun readLiveData(): Map<Int, Any>? {
        val message = interfaceWrite(byteArrayOf(0x6B))
        try {
            if (message.isValid) {
                val result: MutableMap<Int, Any> = HashMap()
                result[0x00] = message.answer.copyOfRange(3, message.answer.size)
                result[0x01] = message.answer.copyOfRange(3 + 0x04, 0x0D)
                    .joinToString("") { "%02X".format(it) }
                result[0x02] = message.answer.copyOfRange(3 + 0x0E, 0x19)
                    .joinToString("") { "%02X".format(it) }
                return result
            }
            return null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex :Exception)
        {
            Logger.e {"$tag [readLiveData], $ex, $message"}
            return null
        }
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        return interfaceWrite(byteArrayOf(0x00)).isValid
    }

    override suspend fun readKeys(): List<EwsKey>? {
        val result: MutableList<EwsKey> = ArrayList()
        for (i in 0..9) {
            val message = interfaceWrite(byteArrayOf(0x65, i.toByte()))
            if (message.isValid && message.answer.size > 13) {
                try {
                    val id = message.answer.copyOfRange(4, 12).joinToString("") { "%02X".format(it) }
                    val ewsKeyData = message.answer[12].toInt() and 0xFF
                    result.add(
                        EwsKey(
                            number = i,
                            id = id,
                            status = ewsKeyData and 0x03,
                            selfInitDone = (ewsKeyData and 0x40) == 0x40,
                            selfInit = (ewsKeyData and 0x80) == 0x80
                        )
                    )
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Logger.e { "$tag [readKeys], $ex, $message" }
                }
            }
        }
        return if (result.isEmpty()) null else result
    }

    override suspend fun readIsn(): String? {
        val message = interfaceWrite(byteArrayOf(0x60))
        return try {
            if (message.isValid && message.answer.size > 5)
                String.format("%02X%02X", message.answer[3], message.answer[4])
            else null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readIsn], $ex, $message" }
            null
        }
    }

    override suspend fun readPassword(): String? {
        val message = interfaceWrite(byteArrayOf(0x63))
        return try {
            if (message.isValid && message.answer.size > 9)
                message.answer.copyOfRange(3, 9).joinToString("") { "%02X".format(it) }
            else null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readPassword], $ex, $message" }
            null
        }
    }

    suspend fun triggerDmeSync(): Boolean =
        interfaceWrite(byteArrayOf(0x67)).isValid

    override fun getAdvanceControlElements() = listOf(
        AdvanceControlElement(Res.string.advance_control_ews_keys_title) { userInfo, onShowResult ->
            AdvanceControlEwsKeys(userInfo = userInfo, onShowResult = onShowResult)
        }
    )
}
