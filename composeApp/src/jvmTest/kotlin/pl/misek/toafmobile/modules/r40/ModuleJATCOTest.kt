package pl.misek.toafmobile.modules.r40

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolKWP
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Przyklad testu modulu z zamockowanym protokolem (mockk + coroutines-test).
 * Wzorzec do testowania funkcji suspend, ktore rozmawiaja przez ProtocolKWP/ProtocolDS2.
 */
class ModuleJATCOTest {

    @Test
    fun getVIN_parsuje_odpowiedz_1A87() = runTest {
        val kwp = mockk<ProtocolKWP>()
        // Odpowiedz 1A 87: B8 F7 19 <len> 5A 87 <11 ASCII VIN> <3 BCD> ...
        val answer = byteArrayOf(
            0xB8.toByte(), 0xF7.toByte(), 0x19, 0x30,
            0x5A, 0x87.toByte(),
            0x53, 0x41, 0x52, 0x52, 0x4A, 0x5A, 0x4C, 0x4C, 0x4D, 0x32, 0x44, // "SARRJZLLM2D"
            0x22, 0x57, 0x98.toByte(),                                         // -> "225798"
            0x00
        )
        val response = OBDMessage().apply {
            this.answer = answer
            isValid = true
            length = answer.size
        }
        coEvery { kwp.write(any(), any(), any<ByteArray>()) } returns response

        assertEquals("SARRJZLLM2D225798", ModuleJATCO(kwp).getVIN())
    }
}
