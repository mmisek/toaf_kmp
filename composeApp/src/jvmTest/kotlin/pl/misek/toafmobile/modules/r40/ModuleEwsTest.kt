package pl.misek.toafmobile.modules.r40

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolDS2
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ModuleEwsTest {
    private val address = ModuleAddress.MODULE_EWS.address
    private val ds2 = mockk<ProtocolDS2>()

    @Test
    fun getVinTest() = runTest {
        data class VinTestCase(
            val response : ByteArray,
            val exceptedVin : String)

        val testCases = listOf(
            VinTestCase(
                response = byteArrayOf(
                    0x44, 0x15, 0xa0.toByte(), 0x01, 0x1c, 0x29,
                    0xb6.toByte(), 0xd3.toByte(), 0x85.toByte(), 0x53, 0xdd.toByte(),
                    0x08, 0xd0.toByte(), 0x83.toByte(), 0x1c, 0x00, 0x86.toByte(),
                    0x55, 0xff.toByte(), 0xff.toByte(), 0xc3.toByte()),
                exceptedVin = "SARRJXLFT2D237026"
            ),
        VinTestCase(
                response = byteArrayOf(
                    0x44, 0x15, 0xA0.toByte(), 0x01, 0x1C, 0x29,
                    0xB6.toByte(), 0xD3.toByte(), 0x71, 0x54, 0x4C,
                    0x89.toByte(), 0x60, 0x40, 0x24, 0x91.toByte(),
                    0x44, 0x42, 0xFF.toByte(), 0xFF.toByte(), 0xC3.toByte()),
                exceptedVin = "SARRJSLHCYM109954"
            )
        )
        val request = byteArrayOf(0x44,0x05,0x69,0x01,0x29)
        testCases.forEach { case ->
            val fakeAnswer = OBDMessage().apply {
                answerCode = 0xA0.toByte()
                wholeMessage = request + case.response
                answer = case.response
                isValid = true
                length = answer.size
            }
            coEvery { ds2.write(eq(address), any<ByteArray>()) } returns fakeAnswer
            val vin = ModuleEWS(ds2).getVIN()
            assertNotNull(vin)
            assertEquals(case.exceptedVin,vin)
        }
    }
}