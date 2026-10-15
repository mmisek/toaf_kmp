package pl.misek.toafmobile.modules.r40

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Przyklad czystego testu jednostkowego (bez mockow) - algorytm seed-key EMS2000.
 * Pary zweryfikowane na realnym sterowniku (patrz pamiec ems2000-seedkey).
 */
class Ems2kSeedKeyTest {

    @Test
    fun key_odtwarza_znane_pary_seed_key() {
        assertEquals(0xA81E270A.toInt(), Ems2kSeedKey.key(0x09AE2C40.toInt()))
        assertEquals(0x94BA39BD.toInt(), Ems2kSeedKey.key(0x82590390.toInt()))
    }
}
