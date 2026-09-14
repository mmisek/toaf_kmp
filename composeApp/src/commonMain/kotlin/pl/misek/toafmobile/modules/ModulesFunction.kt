package pl.misek.toafmobile.modules

import co.touchlab.kermit.Logger
import pl.misek.toafmobile.protocols.OBDMessage
import java.util.Locale
import kotlinx.coroutines.CancellationException

object ModulesFunction {
    private var tag = "ModulesFunctions"
    private val PartNamePrefix : Map<Int,String> = mapOf(
        0xDA0 to "AMR",
        0xDA1 to "HHF",
        0xDA2 to "JFC",
        0xDA3 to "MKC",
        0xDA4 to "SCB",
        0xDA5 to "SRB",
        0xDA6 to "XQC",
        0xDA7 to "XQD",
        0xDA8 to "XQE",
        0xDA9 to "XVD",
        0xDAA to "YAC",
        0xDAB to "YDB",
        0xDAC to "YFC",
        0xDAD to "YUB",
        0xDAE to "YWC",
        0xDAF to "YWQ",
        0xDB0 to "EGQ",
        0xDB1 to "YIB",
        0xDB2 to "YIC",
        0xDB3 to "YIE"
    )

    private val PartProducer: Map<Int, String> = mapOf(
        0x00 to "Unknown",
        0x01 to "Reinshagen",
        0x02 to "Kostal",
        0x03 to "Hella",
        0x04 to "Siemens",
        0x05 to "Eaton",
        0x06 to "UTA",
        0x07 to "Helbako",
        0x08 to "Bosch",
        0x09 to "Loewe",
        0x10 to "VDO",
        0x11 to "Valeo",
        0x12 to "MBB",
        0x13 to "Kammerer",
        0x14 to "SWF",
        0x15 to "Blaupunkt",
        0x16 to "Philips",
        0x17 to "Alpine",
        0x18 to "Teves",
        0x19 to "Elektromatik Suedafrika",
        0x20 to "Becker",
        0x21 to "Preh",
        0x22 to "Alps",
        0x23 to "Motorola",
        0x24 to "Temic",
        0x25 to "Webasto",
        0x26 to "MotoMeter",
        0x27 to "Delphi PHI",
        0x28 to "DODUCO => BERU",
        0x29 to "DENSO",
        0x30 to "NEC",
        0x31 to "DASA",
        0x32 to "Pioneer",
        0x33 to "Jatco",
        0x34 to "Fuba",
        0x35 to "UK-NSI",
        0x36 to "AABG",
        0x37 to "Dunlop",
        0x38 to "Sachs",
        0x39 to "ITT",
        0x40 to "FTE",
        0x41 to "Megamos",
        0x42 to "TRW",
        0x43 to "Wabco",
        0x44 to "ISAD Electronic Systems",
        0x45 to "HEC (Hella Electronics Corporation)",
        0x46 to "Gemel",
        0x47 to "ZF",
        0x48 to "GMPT",
        0x49 to "Harman Kardon",
        0x50 to "Remes",
        0x51 to "ZF Lenksysteme",
        0x52 to "Magneti Marelli",
        0x53 to "Borg Instruments",
        0x54 to "GETRAG",
        0x55 to "BHTC (Behr Hella Thermocontrol)",
        0x56 to "Siemens VDO Automotive",
        0x57 to "Visteon",
        0x58 to "Autoliv",
        0x59 to "Haberl",
        0x60 to "Magna Steyr",
        0x61 to "Marquardt",
        0x62 to "AB-Elektronik",
        0x63 to "Siemens VDO Borg",
        0x64 to "Hirschmann Electronics",
        0x65 to "Hoerbiger Electronics",
        0x66 to "Thyssen Krupp Automotive Mechatronics",
        0x67 to "Gentex GmbH",
        0x68 to "Atena GmbH",
        0x69 to "Magna-Donelly",
        0x70 to "Koyo Steering Europe",
        0x71 to "NSI B.V",
        0x72 to "AISIN AW CO.LTD",
        0x73 to "Shorlock",
        0x74 to "Schrader",
        0x75 to "BERU Electronics GmbH",
        0x76 to "CEL",
        0x77 to "Audio Mobil",
        0x78 to "rd electronic",
        0x79 to "iSYS RTS GmbH",
        0x80 to "Westfalia Automotive GmbH",
        0x81 to "Tyco Electronics",
        0x82 to "Paragon AG",
        0x83 to "IEE S.A",
        0x84 to "TEMIC AUTOMOTIVE of NA",
        0x85 to "AKsys GmbH",
        0x86 to "META System",
        0x87 to "Hülsbeck & Fürst GmbH & Co KG",
        0x88 to "Mann & Hummel Automotive GmbH",
        0x89 to "Brose Fahrzeugteile GmbH & Co",
        0x90 to "Keihin",
        0x91 to "Vimercati S.p.A.",
        0x92 to "CRH",
        0x93 to "TPO Display Corp.",
        0x94 to "KÜSTER Automotive Control",
        0x95 to "Hitachi Automotive",
        0x96 to "Continental Automotive",
        0x97 to "TI-Automotive",
        0x98 to "Hydro",
        0x99 to "Johnson Controls")

    private val VINBase = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
        'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
        'U', 'V', 'W', 'X', 'Y', 'Z'
    )

    fun decodeModuleID(answer: ByteArray): String {
        val i = (answer[0].toInt() and 0xFF) shl 4 or ((answer[1].toInt() and 0xFF) shr 4)
        if (PartNamePrefix.containsKey(i)) {
            try {
                return PartNamePrefix[i] + String.format(Locale.getDefault(),
                    "%06d",
                    ((answer[1].toInt() and 0x0F) shl 16) or ((answer[2].toInt() and 0xFF) shl 8) or (answer[3].toInt() and 0xFF)
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e{"$tag, ${ex.toString()}"}
                return "--"
            }
        } else {
            return String.format(
                "%01X.%02X%01X.%01X%02X", (answer[0].toInt() and 0x0F),
                answer[1], (answer[2].toInt() and 0xF0) shr 4, answer[2].toInt() and 0x0F, answer[3]
            )
        }
    }

    fun getModuleInfo(message: OBDMessage): ModuleInfo? {
        var moduleInfo: ModuleInfo? = null
        if (!message.isValid || message.answer.size < 3)
            return null
        val answer = message.answer.copyOfRange( 3, message.answer.size)
        if (message.isValid) {
            try {
                moduleInfo = ModuleInfo()
                moduleInfo.id1 = decodeModuleID(answer)
                moduleInfo.hw = String.format("%02X", answer[4].toInt() and 0xFF)
                moduleInfo.sw = String.format("%02X", answer[11].toInt() and 0xFF)
                moduleInfo.ci = String.format("%02X", answer[5].toInt() and 0xFF)
                moduleInfo.di = String.format("%02X", answer[6].toInt() and 0xFF)
                moduleInfo.bi = String.format("%02X", answer[7].toInt() and 0xFF)
                moduleInfo.date = String.format(
                    "%02X/%02X",
                    answer[8].toInt() and 0xFF,
                    answer[9].toInt() and 0xFF
                )
                moduleInfo.producer = getProducer(answer[10].toInt() and 0xFF).toString()
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e{"$tag, ${ex.toString()}"}
            }
        }
        return moduleInfo
    }

    fun getProducer(producer: Int): String? {
        return if (PartProducer.containsKey(producer)) PartProducer[producer] else PartProducer[0x00]
    }

    fun hexToVinDecoder(message: ByteArray): String {
        val vin =
            StringBuilder(VINBase[(message[0].toInt() and 0xFF) % 0x40].toString())
        var i = 1
        while (i < 13) {
            val x1 =
                ((message[i].toInt() and 0xFF) shl 8 or (message[i + 1].toInt() and 0xFF)) shr 4
            val x2 =
                ((message[i + 1].toInt() and 0xFF) shl 8 or (message[i + 2].toInt() and 0xFF)) and 0x0FFF
            vin.append(VINBase[x1 / 0x40])
            vin.append(VINBase[x1 % 0x40])
            vin.append(VINBase[x2 / 0x40])
            vin.append(VINBase[x2 % 0x40])
            i += 3
        }
        return vin.toString()
    }
}
