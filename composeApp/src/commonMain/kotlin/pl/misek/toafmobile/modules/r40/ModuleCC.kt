package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.ModulesFunction
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKWP
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import kotlinx.coroutines.CancellationException

private const val tag = "ModuleCC"
class ModuleCC(protocolKWP: ProtocolKWP?) : IModule, IModuleLiveData {
    private var kwp: IProtocol? = null
    private val moduleAddress = ModuleAddress.MODULE_CC.address

    init {
        if (protocolKWP != null) kwp = protocolKWP
    }
    override fun getKoinName() = KoinNames.ModuleR40Cc

    override fun getModuleProtocol(): IProtocol = kwp!!

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol
    }

    override fun getModuleName(): String = ModuleAddress.MODULE_CC.showName

    override fun getModuleAddress(): ModuleAddress = ModuleAddress.MODULE_CC

    override fun getImageResource() = Res.drawable.module_cc

    override fun getModuleDescription() = Res.string.module_description_cc

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        val message = kwp!!.write(moduleAddress, 0x1A.toByte(), byteArrayOf(0x80.toByte()), 200)
        if (message.isValid) {
            try {
                val answer = message.answer
                info = ModuleInfo()
                info.id1 = String(answer.copyOfRange(6, 16))
                info.hw = (answer[16].toInt() and 0xFF).toByte().toHexString()
                info.ci = (answer[17].toInt() and 0xFF).toByte().toHexString()
                info.di = (answer[18].toInt() and 0xFF).toByte().toHexString()
                info.sw = (answer[26].toInt() and 0xFF).toByte().toHexString()
                info.producer = ModulesFunction.getProducer(answer[25].toInt())
                info.date = ResourceHelper.byteArrayToDate(answer.copyOfRange(21, 25))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getIdentification()], $ex, $message"}
            }
        }
        return info
    }

    override suspend fun getVIN(): String? {
        return null
    }

    override suspend fun getOdometer(): String? {
        return null
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        if (securityAccess03()) {
            val message = kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x04), 500)
            if (message.isValid) {
                try {
                    result = ArrayList()
                    for (i in 0..1) {
                        val n = 0x01
                        for (j in 0..7) {
                            if ((message.answer[6 + i].toInt() and (n shl j)) == (n shl j)) {
                                val errorCode = n shl j
                                val errorDescription: MutableList<Pair<StringResource, Any?>> =
                                    ArrayList()
                                for (k in 1..4) {
                                    ResourceMapper.stringResource(
                                        String.format(
                                            "cc_error_detail_%02X",
                                            k
                                        )
                                    )
                                        ?.let { errorDescription.add(Pair(it, null)) }
                                }
                                ResourceMapper.stringResource(
                                    String.format(
                                        "cc_error_detail_%02X",
                                        0x05 + (message.answer[13].toInt() and 0xFF)
                                    )
                                )
                                    ?.let { errorDescription.add(Pair(it, null)) }
                                result.add(
                                    ModuleError(
                                        ModuleAddress.MODULE_CC,
                                        String.format("%02X", errorCode),
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
                    Logger.e {"$tag [getAllErros()], $ex, $message"}
                }
            }
        }
        return result
    }

    override suspend fun clearAllErrors(): Boolean {
        if (securityAccess03()) {
            return kwp!!.write(
                moduleAddress,
                0x14.toByte(),
                byteArrayOf(0x00, 0x00),
                500
            ).isValid
        }
        return false
    }

    override suspend fun reset(): Boolean {
        return kwp!!.write(moduleAddress, 0x20.toByte(), byteArrayOf()).isValid
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (i in 0x05..0x7) {
            val message =kwp!!.write(moduleAddress, 0x21.toByte(), byteArrayOf(i.toByte()))
            if (message.isValid) {
                try {
                    result[i - 0x05] = message.answer.copyOfRange(6, message.length - 1)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex : Exception)
                {
                    Logger.e {"$tag [readLiveData()], $ex, $message"}
                }
            } else return null
        }
        return result
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>) = securityAccess03()

    override suspend fun getModuleVoltage(count : Int) = Float.NaN

    private suspend fun securityAccess03(): Boolean {
        val level :Byte = 0x03
        val message = kwp!!.write(moduleAddress, 0x27.toByte(), byteArrayOf(level), 500)
        if (message.isValid) {
            try {
                val code =
                    ((message.answer[6].toInt() and 0xFF) shl 8) + (message.answer[7].toInt() and 0xFF)
                if (code == 0x0000) return true
                else {
                    val seed = securityAccessCalculator(code)
                    val data = byteArrayOf(
                        (level + 1).toByte(),
                        ((seed and 0xFF00) shr 8).toByte(), (seed and 0xFF).toByte()
                    )
                    return kwp!!.write(moduleAddress, 0x27.toByte(), data).isValid
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [securityAccess03()], $ex, $message"}
                return false
            }

        }
        return false
    }

    private fun securityAccessCalculator(inKey: Int): Int {
        var key = inKey
        var n = ((((key shr 8) and 0x80) shr 7) xor ((key shr 8) and 0x01)) * 8
        n += ((((key shr 0) and 0x40) shr 6) xor (((key shr 0) and 0x08) shr 3)) * 4
        n += ((((key shr 8) and 0x20) shr 5) xor (((key shr 0) and 0x04) shr 2)) * 2
        n += ((((key shr 8) and 0x04) shr 2) xor (((key shr 0) and 0x10) shr 4)) * 1
        n += 1
        for (i in 0 until n) {
            var x = 0
            if ((key and 0x0100) == 0x0100) x = x xor 1
            if ((key and 0x1000) == 0x1000) x = x xor 1
            if ((key and 0x0020) == 0x0020) x = x xor 1
            if ((key and 0x0080) == 0x0080) x = x xor 1
            val p = key and 0x01
            key = ((key shr 1) and 0xFF00) + ((key and 0xFF) shr 1)
            if (p == 0x01)
                key += 0x8000
            if ((x and 0x01) == 0x01)
                key += 0x80
        }
        key = ((key and 0xFF00) shr 8) + ((key and 0xFF) shl 8)
        return key
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.R40)
    }
}