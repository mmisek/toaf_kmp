package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_ac
import toafmobile.composeapp.generated.resources.module_description_hevacv8
import kotlinx.coroutines.CancellationException

private const val tag = "ModuleHevacX12"

/**
 * Sterownik klimatyzacji HEVAC w Rover 75 / MG ZT V8 (projekt MG Rover "X12").
 * Protokol DS2, adres 0x5D.
 *
 * Kody usterek zwracane sa jako POJEDYNCZY BAJT, w ktorym kazdy ustawiony bit to osobna usterka
 * (maski 0x01..0x80). Odpowiedz: `5D <LEN> A0 <bajt usterek> <CS>` - bajt lezy na answer[3].
 *
 * `SetEcuState` (IModuleControl) steruje blokada sterownika - inhibit nalezy wykonac przed
 * diagnostyka ECU/Gateway, a po zakonczeniu przywrocic enable.
 */
class ModuleHevacX12(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_HEVAC_X12, listOf(CarType.R40)), IModuleControl {

    override fun getKoinName() = KoinNames.ModuleR40HevacX12

    override fun getImageResource() = Res.drawable.module_ac

    override fun getModuleDescription() = Res.string.module_description_hevacv8

    override suspend fun getAllErrors(): List<ModuleError>? {
        val message = interfaceWrite(byteArrayOf(READ_ERRORS), 500)
        if (!message.isValid) return null
        try {
            if (message.answer.size <= ERROR_BYTE) return null
            val faults = message.answer[ERROR_BYTE].toInt() and 0xFF
            val result = ArrayList<ModuleError>()
            for (bit in 0 until 8) {
                val mask = 1 shl bit
                if (faults and mask == 0) continue
                result.add(
                    ModuleError(
                        ModuleAddress.MODULE_HEVAC_X12,
                        "%02X".format(mask),
                        ArrayList()
                    )
                )
            }
            return result
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getAllErrors], $ex, $message" }
        }
        return null
    }

    override suspend fun clearAllErrors(): Boolean =
        interfaceWrite(byteArrayOf(CLEAR_ERRORS), 500).isValid

    override suspend fun control(controlElement: ControlElement): Boolean =
        interfaceWrite(byteArrayOf(SET_ECU_STATE, controlElement.value.toByte()), 500).isValid

    companion object {
        private const val READ_ERRORS: Byte = 0x04
        private const val CLEAR_ERRORS: Byte = 0x05
        private const val SET_ECU_STATE: Byte = 0x0C

        /** Bajt usterek w odpowiedzi DS2: `5D LEN A0 <tu> CS`. */
        private const val ERROR_BYTE = 3
    }
}
