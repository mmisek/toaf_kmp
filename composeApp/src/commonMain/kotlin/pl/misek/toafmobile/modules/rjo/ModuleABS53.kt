package pl.misek.toafmobile.modules.rjo

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolKW1281
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_abs
import toafmobile.composeapp.generated.resources.module_description_abs53

class ModuleABS53(private var kw1281: ProtocolKW1281) : IModule, IModuleLiveData, IModuleControl {
    private val tag = "ModuleABS53"

    private val moduleAddress = ModuleAddress.MODULE_ABS53.address.toInt() and 0xFF

    init {
        kw1281.setSessionSetup(moduleAddress, listOf(TITLE_UPLOAD to ACTUATOR_ROUTINE))
    }

    override fun getKoinName() = KoinNames.ModuleRJOAbs

    override fun getModuleName(): String = "ABS53"

    override fun getModuleAddress(): ModuleAddress = ModuleAddress.MODULE_ABS53

    override fun getImageResource() = Res.drawable.module_abs

    override fun getModuleDescription() = Res.string.module_description_abs53

    override fun getModuleProtocol(): IProtocol = kw1281

    override fun setModuleProtocol(protocol: IProtocol) {
        kw1281 = protocol as ProtocolKW1281
        kw1281.setSessionSetup(moduleAddress, listOf(TITLE_UPLOAD to ACTUATOR_ROUTINE))
    }

    override fun getSupportedCarTypes(): List<CarType> = listOf(CarType.RJO)

    private suspend fun transact(title: Int, data: ByteArray = ByteArray(0), multiBlock: Boolean = false) =
        kw1281.transact(moduleAddress, title, data, multiBlock)

    override suspend fun getIdentification(): ModuleInfo? {
        transact(ProtocolKW1281.ACK) ?: return null
        val ident = kw1281.identification
        if (ident.isEmpty()) return null
        return ModuleInfo().apply {
            id1 = ident.getOrElse(0) { "" }
            id2 = ident.drop(1).joinToString(" ")
        }
    }

    override suspend fun getVIN(): String? = null

    override suspend fun getOdometer(): String? = null

    override suspend fun getAllErrors(): List<ModuleError>? {
        val blocks = transact(TITLE_READ_DTC, multiBlock = true) ?: return null
        val result: MutableList<ModuleError> = ArrayList()
        for ((_, title, data) in blocks) {
            if (title != TITLE_DTC) continue
            for (offset in 0..data.size - DTC_RECORD_SIZE step DTC_RECORD_SIZE) {
                val code = data[offset].toInt() and 0xFF
                if (code == 0x00 || code == 0xFF) continue
                val status = data[offset + 4].toInt() and 0xFF
                val description: MutableList<Pair<StringResource, Any?>> = ArrayList()
                if ((status and 0x04) == 0x04 || (status and 0x02) == 0x02)
                    description.add(ResourceHelper.getErrorDescriptionIsPresent((status and 0x04) == 0x04))
                val errorCode = DTC_NUMBERS[code]?.toString() ?: String.format("x%02X", code)
                result.add(ModuleError(ModuleAddress.MODULE_ABS53, errorCode, description))
            }
        }
        return result
    }

    override suspend fun clearAllErrors(): Boolean = transact(TITLE_CLEAR_DTC, multiBlock = true) != null

    override suspend fun reset(): Boolean = false

    override suspend fun readLiveData(): Map<Int, Any>? {
        try {
            val blocks = transact(TITLE_READ_GROUP, multiBlock = true) ?: return null
            val data = blocks.fold(ByteArray(0)) { acc, block -> acc + block.data }
            if (data.size < 11) return null
            val speeds = ByteArray(12)
            for (index in 0..3) {
                val raw = (data[3 + 2 * index].toInt() and 0xFF) or ((data[4 + 2 * index].toInt() and 0xFF) shl 8)
                val speed = (raw + 10) * 6
                speeds[3 * index] = (speed shr 16).toByte()
                speeds[3 * index + 1] = (speed shr 8).toByte()
                speeds[3 * index + 2] = speed.toByte()
            }
            val states = byteArrayOf(
                if (((data[0].toInt() and 0xFF) shr 4) == 0x0F) 1 else 0,
                if (((data[1].toInt() and 0xFF) shr 4) == 0x0F) 0 else 1,
                if (((data[2].toInt() and 0xFF) shr 4) == 0x0F) 1 else 0
            )
            return mapOf(1 to speeds, 2 to states)
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readLiveData], $ex" }
            return null
        }
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean = true

    override suspend fun getModuleVoltage(count : Int) = Float.NaN

    override suspend fun control(controlElement: ControlElement): Boolean {
        val command = CONTROL_COMMANDS[controlElement.byteInBlock] ?: return false
        return transact(TITLE_RAM, command) != null
    }

    companion object {
        private const val TITLE_UPLOAD = 0x02
        private const val TITLE_CLEAR_DTC = 0x05
        private const val TITLE_READ_DTC = 0x07
        private const val TITLE_READ_GROUP = 0x08
        private const val TITLE_RAM = 0x27
        private const val TITLE_DTC = 0xFC
        private const val DTC_RECORD_SIZE = 5

        private val DTC_NUMBERS = mapOf(
            0x01 to 1,
            0x04 to 2,
            0x05 to 3,
            0x06 to 4,
            0x07 to 5,
            0x0E to 6,
            0x0F to 7,
            0x14 to 8,
            0x15 to 9,
            0x18 to 10,
            0x19 to 11,
            0x1E to 12,
            0x1F to 13,
            0x20 to 14,
            0x21 to 15,
            0x2F to 16,
            0x30 to 17,
            0x31 to 18,
            0x32 to 19,
            0x33 to 20,
            0x34 to 21,
            0x35 to 22,
            0x36 to 23
        )

        private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

        private fun command(state: Int, element: Int, length: Int) =
            ByteArray(length).also { it[0] = state.toByte(); it[1] = element.toByte() }

        private fun lampTest(element: Int) =
            bytes(0x00, element, 0x00, 0x00, LAMP_TEST_MS and 0xFF, LAMP_TEST_MS shr 8, 0xFF, element, 0x00, 0x00)

        private const val LAMP_TEST_MS = 3000

        private val CONTROL_COMMANDS = mapOf(
            0x00 to command(0xFF, 0x22, 10),
            0x01 to command(0x00, 0x22, 10),
            0x02 to command(0xFF, 0x30, 12),
            0x03 to command(0x00, 0x30, 12),
            0x04 to command(0xFF, 0x34, 12),
            0x05 to command(0x00, 0x34, 12),
            0x06 to command(0xFF, 0x3C, 12),
            0x07 to command(0x00, 0x3C, 12),
            0x08 to command(0xFF, 0x38, 12),
            0x09 to command(0x00, 0x38, 12),
            0x0A to lampTest(0x02),
            0x0B to lampTest(0x0E)
        )

        private val ACTUATOR_ROUTINE = bytes(
            0x04, 0x00, 0x00, 0xBB, 0x07, 0x07, 0x64, 0x07, 0x00, 0xB1, 0x02, 0x24, 0xA3, 0x01, 0xA2, 0x20,
            0x20, 0xA2, 0x79, 0x22, 0xA2, 0x21, 0x1E, 0x88, 0x00, 0x1E, 0xDF, 0x34, 0x51, 0xFE, 0x1E, 0x1D,
            0x78, 0x23, 0x1D, 0xD7, 0xEF, 0x51, 0xF8, 0x1F, 0x1C, 0x08, 0x02, 0x1C, 0x67, 0x01, 0xAC, 0x20,
            0x1C, 0xA2, 0x1C, 0x1C, 0x38, 0x1E, 0x16, 0xA1, 0x01, 0x01, 0x20, 0x71, 0x07, 0x1F, 0x09, 0x1F,
            0x20, 0x92, 0x1C, 0x20, 0x3A, 0x22, 0x03, 0x94, 0x21, 0x20, 0xB0, 0x20, 0x22, 0xC6, 0x1C, 0x22,
            0xE0, 0x24, 0xB9, 0x07, 0x40, 0x9A, 0x40, 0x00, 0xDF, 0x15, 0xA2, 0x79, 0x7C, 0x88, 0x00, 0x7C,
            0xD7, 0x04, 0x07, 0x40, 0x27, 0xA3, 0x6B, 0x01, 0xA6, 0x20, 0x7C, 0xD9, 0x02, 0x07, 0x40, 0xF0
        )
    }
}
