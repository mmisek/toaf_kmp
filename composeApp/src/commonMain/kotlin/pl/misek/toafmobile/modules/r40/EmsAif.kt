package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_info_aif_assembly_number
import toafmobile.composeapp.generated.resources.module_info_aif_dealer_code
import toafmobile.composeapp.generated.resources.module_info_aif_odometer
import toafmobile.composeapp.generated.resources.module_info_aif_part_number
import toafmobile.composeapp.generated.resources.module_info_aif_program_number
import toafmobile.composeapp.generated.resources.module_info_aif_tester_serial
import toafmobile.composeapp.generated.resources.module_info_aif_vin
import toafmobile.composeapp.generated.resources.module_info_programing_date
import java.util.Calendar

/**
 * Rekord AIF EMS2K - 46 bajtow danych odpowiedzi 1A 87 (AIF_LESEN), zapisywany przez 3B 87 (AIF_SCHREIBEN).
 * Ten sam rekord jest w odpowiedzi 1A 80 od bajtu 7.
 * Uklad Rover (VIN i numer czesci w ASCII + BCD), od pola numeru zespolu offsety jak w IDENT_AIF z ems2k.b2v
 * (offset w telegramie = offset w rekordzie + 6 dla 1A 87, + 7 dla 1A 80).
 */
data class EmsAif(
    val vin: String,
    val programmingDate: String,
    val partNumber: String,
    val assemblyNumber: Int,
    val testerSerial: String,
    val dealerCode: Int,
    val odometerKm: Int,
    val programNumber: String
) {
    fun toAdditionalInfo(): List<Pair<StringResource, Any?>> = listOf(
        Pair(Res.string.module_info_aif_vin, vin),
        Pair(Res.string.module_info_programing_date, programmingDate),
        Pair(Res.string.module_info_aif_part_number, partNumber),
        Pair(Res.string.module_info_aif_assembly_number, assemblyNumber),
        Pair(Res.string.module_info_aif_tester_serial, testerSerial),
        Pair(Res.string.module_info_aif_dealer_code, dealerCode),
        Pair(Res.string.module_info_aif_odometer, "$odometerKm km"),
        Pair(Res.string.module_info_aif_program_number, programNumber)
    )

    override fun toString(): String =
        "AIF: VIN=$vin, programming date=$programmingDate, part number=$partNumber, " +
            "assembly number=$assemblyNumber, tester serial=$testerSerial, dealer code=$dealerCode, " +
            "odometer=$odometerKm km, program number=$programNumber"

    companion object {
        private const val tag = "EmsAif"
        const val RECORD_SIZE = 0x2E

        private const val OFFSET_VIN = 0x00
        private const val VIN_ASCII_LENGTH = 11
        private const val OFFSET_VIN_BCD = 0x0B
        private const val OFFSET_DATE = 0x0F

        fun parse(record: ByteArray): EmsAif? {
            if (record.size < RECORD_SIZE) {
                Logger.e { "$tag : record too short (${record.size})" }
                return null
            }
            return EmsAif(
                // jak getVIN w ModuleEMS: 11 znakow ASCII + 3 bajty BCD
                vin = ascii(record, OFFSET_VIN, VIN_ASCII_LENGTH) + hex(record, OFFSET_VIN_BCD, 3),
                // BCD dd mm rrrr
                programmingDate = String.format("%s/%s/%s", hex(record, OFFSET_DATE, 1), hex(record, OFFSET_DATE + 1, 1), hex(record, OFFSET_DATE + 2, 2)),
                // 3 znaki ASCII + 3 bajty BCD (np. NNW004580)
                partNumber = ascii(record, 0x14, 3) + hex(record, 0x17, 3),
                // AIF_ZB_NR
                assemblyNumber = uint(record, 0x1B, 3),
                // AIF_PROGG_NR - 5 znakow do bajtu 00
                testerSerial = ascii(record, 0x1F, 5),
                // AIF_WERKSCODE
                dealerCode = uint(record, 0x25, 2),
                // AIF_KM_STAND - jednostka 600 km
                odometerKm = (record[0x28].toInt() and 0xFF) * 600,
                // AIF_PROG_NR
                programNumber = hex(record, 0x2A, 3)
            )
        }

        /**
         * Kopia rekordu z nowym VIN (11 znakow ASCII + 6 ostatnich cyfr w BCD) i data programowania [date],
         * pozostale pola bez zmian.
         */
        fun withVin(record: ByteArray, vin: String, date: Calendar): ByteArray? {
            if (record.size < RECORD_SIZE || vin.length != VIN_ASCII_LENGTH + 6) return null
            val digits = vin.substring(VIN_ASCII_LENGTH)
            if (!digits.all { it in '0'..'9' }) return null
            val result = record.copyOf(RECORD_SIZE)
            vin.substring(0, VIN_ASCII_LENGTH).encodeToByteArray().copyInto(result, OFFSET_VIN)
            bcd(digits).copyInto(result, OFFSET_VIN_BCD)
            val dateText = String.format(
                "%02d%02d%04d",
                date.get(Calendar.DAY_OF_MONTH),
                date.get(Calendar.MONTH) + 1,
                date.get(Calendar.YEAR)
            )
            bcd(dateText).copyInto(result, OFFSET_DATE)
            return result
        }

        private fun bcd(digits: String): ByteArray =
            ByteArray(digits.length / 2) { (((digits[2 * it] - '0') shl 4) or (digits[2 * it + 1] - '0')).toByte() }

        private fun ascii(data: ByteArray, offset: Int, length: Int): String =
            data.copyOfRange(offset, offset + length)
                .takeWhile { it != 0x00.toByte() }
                .filter { it in 0x20..0x7E }
                .map { it.toInt().toChar() }
                .joinToString("")
                .trim()

        private fun hex(data: ByteArray, offset: Int, length: Int): String =
            (offset until offset + length).joinToString("") { String.format("%02X", data[it].toInt() and 0xFF) }

        private fun uint(data: ByteArray, offset: Int, length: Int): Int =
            (offset until offset + length).fold(0) { acc, i -> (acc shl 8) or (data[i].toInt() and 0xFF) }
    }
}
