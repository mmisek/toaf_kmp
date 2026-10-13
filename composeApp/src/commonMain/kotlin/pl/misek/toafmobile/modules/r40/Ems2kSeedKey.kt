package pl.misek.toafmobile.modules.r40

/**
 * Algorytm seed-key EMS2000 dla 4-bajtowego poziomu 0xFD/0xFE (SecurityAccess).
 * Odtworzony z procedury Galletto (NEW_SERIAL.exe @ 0x53CE20), zweryfikowany na 572 parach seed/key.
 *
 * seed: 32-bitowy (s0 - najstarszy bajt). Zwraca 32-bitowy klucz.
 * Rdzen: dwa CRC-16/MODBUS (wielomian 0xA001, init 0xFFFF) nad buforami z seeda i stalych,
 * XOR z buforem bufA (bajty seeda, warunkowo ror(seed,1), potem permutacja bajtow wg (s0 % 3)).
 */
object Ems2kSeedKey {

    private fun crc16Modbus(data: IntArray): Int {
        var crc = 0xFFFF
        for (b in data) {
            crc = crc xor (b and 0xFF)
            repeat(8) {
                crc = if ((crc and 1) == 1) (crc ushr 1) xor 0xA001 else crc ushr 1
            }
        }
        return crc and 0xFFFF
    }

    fun key(seed: Int): Int {
        val s0 = (seed ushr 24) and 0xFF
        val s1 = (seed ushr 16) and 0xFF
        val s2 = (seed ushr 8) and 0xFF
        val s3 = seed and 0xFF

        val crc1 = crc16Modbus(intArrayOf(s0, s1, s2, s3, 0xAC, 0x53))
        val crc2 = crc16Modbus(intArrayOf(0x65, 0x96, s0, s1, s2, s3))

        // bufA: bajty seeda odwrocone; przy nieparzystej liczbie bitow seeda - bajty ror(seed,1)
        var bufA = if ((seed.countOneBits() and 1) == 1) {
            val r = (seed ushr 1) or (seed shl 31)
            intArrayOf(r and 0xFF, (r ushr 8) and 0xFF, (r ushr 16) and 0xFF, (r ushr 24) and 0xFF)
        } else {
            intArrayOf(s3, s2, s1, s0)
        }

        // permutacja bajtow bufA wg (s0 % 3)
        bufA = when (s0 % 3) {
            0 -> intArrayOf(bufA[2], bufA[3], bufA[0], bufA[1])   // zamiana polowek
            1 -> intArrayOf(bufA[3], bufA[2], bufA[1], bufA[0])   // odwrocenie
            else -> intArrayOf(bufA[1], bufA[0], bufA[3], bufA[2]) // zamiana par
        }

        val o3 = (crc1 and 0xFF) xor bufA[0]
        val o2 = (crc1 ushr 8) xor bufA[1]
        val o1 = (crc2 and 0xFF) xor bufA[2]
        val o0 = (crc2 ushr 8) xor bufA[3]
        return (o0 shl 24) or (o1 shl 16) or (o2 shl 8) or o3
    }
}
