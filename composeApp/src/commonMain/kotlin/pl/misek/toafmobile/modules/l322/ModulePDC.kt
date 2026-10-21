package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.protocols.ProtocolDS2
import pl.misek.toafmobile.modules.r40.ModulePDC

private const val tag = "ModulePDC_L322"

class ModulePDC(protocolDS2: ProtocolDS2?) :
    ModulePDC(protocolDS2, ModuleAddress.MODULE_PDC_L322, listOf(CarType.L322)) {

    private val codingBlocks = intArrayOf(0x00, 0x01, 0x02, 0x04, 0x08)

    override fun getKoinName() = KoinNames.ModuleL322Pdc

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()
        for (block in codingBlocks) {
            val message = interfaceWrite(byteArrayOf(0x08, block.toByte()), 300)
            try {
                if (!message.isValid) {
                    Logger.e { "$tag [readCoding]: block $block read failed, $message" }
                    return null
                }
                result.add(message.answer.copyOfRange(3, message.answer.size - 1))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [readCoding], $ex, $message" }
                return null
            }
        }
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        if (dataToSave.size != codingBlocks.size) {
            Logger.e { "$tag [writeCoding]: expected ${codingBlocks.size} blocks, got ${dataToSave.size}" }
            return false
        }
        for (i in dataToSave.indices) {
            val message = interfaceWrite(
                arrayOf(
                    byteArrayOf(0x09, codingBlocks[i].toByte()),
                    dataToSave[i]
                )
            )
            if (!message.isValid) return false
        }
        return true
    }
}
