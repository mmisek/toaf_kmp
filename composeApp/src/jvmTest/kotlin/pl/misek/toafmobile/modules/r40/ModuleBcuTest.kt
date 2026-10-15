package pl.misek.toafmobile.modules.r40

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert
import org.junit.Test
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.ipk_error_desc_80
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModuleBcuTest {
    private val address = ModuleAddress.MODULE_BCU.address
    private val ds2 = mockk<ProtocolDS2>()

    @Test
    fun getIdentificationTest() = runTest{
        val requestSecurityAccess = byteArrayOf(0x00,0x04,0x0E,0x0A)
        val responseSecurityAccess = byteArrayOf(0x00, 0x06, 0xA0.toByte(),0x77, 0x14, 0xC5.toByte())
        val requestKey = byteArrayOf(0x00,0x06,0x64,0x14,0x0A,0x7C.toByte())
        val responseKey = byteArrayOf(0x00,0x04,0xA0.toByte(),0xA4.toByte())
        val requestIdent = byteArrayOf(0x00,0x04,0x00,0x04)
        val responseIdent = byteArrayOf(0x00,0x10,0xA0.toByte(),0xDA.toByte(), 0xE1.toByte(),0xA2.toByte()
            ,0x66,0x12,0x02,0x01,0x00,0x42,0x99.toByte(), 0x06,0x52,0xD1.toByte())
        val fakeSecurityAnswer = OBDMessage().apply {
            this.answerCode = 0xA0.toByte()
            this.wholeMessage = requestSecurityAccess + responseSecurityAccess
            this.answer = responseSecurityAccess
            isValid = true
            length = answer.size
        }
        val fakeKeyAnswer = OBDMessage().apply {
            this.answerCode = 0xA0.toByte()
            this.wholeMessage = requestKey + responseKey
            this.answer = responseKey
            isValid = true
            length = answer.size
        }
        val fakeIdentAnswer = OBDMessage().apply {
            this.answerCode = 0xA0.toByte()
            this.wholeMessage = requestIdent + responseIdent
            this.answer = responseIdent
            isValid = true
            length = answer.size
        }
        coEvery { ds2.write(eq(address), any<ByteArray>(), any()) } returnsMany listOf(fakeSecurityAnswer, fakeKeyAnswer)
        coEvery { ds2.write(eq(address), any<ByteArray>()) } returns fakeIdentAnswer
        val expect = ModuleInfo().apply {
            id1 = "YWC107110"
            hw = "12"
            sw = "52"
            ci = "02"
            bi = "00"
            di = "01"
            date = "42/99"
            producer = "UTA"
        }
        val ident = ModuleBCU(ds2).getIdentification()
        assertNotNull(ident)
        assertEquals(expect.id1, ident.id1)
        assertEquals(expect.id2, ident.id2)
        assertEquals(expect.hw, ident.hw)
        assertEquals(expect.sw, ident.sw)
        assertEquals(expect.ci, ident.ci)
        assertEquals(expect.di, ident.di)
        assertEquals(expect.bi, ident.bi)
        assertEquals(expect.producer, ident.producer)
        assertEquals(expect.date, ident.date)
        assertEquals(expect.serial, ident.serial)
    }

    @Test
    fun getVinTest() = runTest {
        val requestSecurityAccess = byteArrayOf(0x00,0x04,0x0E,0x0A)
        val responseSecurityAccess = byteArrayOf(0x00, 0x06, 0xA0.toByte(),0x77, 0x14, 0xC5.toByte())
        val requestKey = byteArrayOf(0x00,0x06,0x64,0x14,0x0A,0x7C.toByte())
        val responseKey = byteArrayOf(0x00,0x04,0xA0.toByte(),0xA4.toByte())
        val fakeSecurityAnswer = OBDMessage().apply {
            this.answerCode = 0xA0.toByte()
            this.wholeMessage = requestSecurityAccess + responseSecurityAccess
            this.answer = responseSecurityAccess
            isValid = true
            length = answer.size
        }
        val fakeKeyAnswer = OBDMessage().apply {
            this.answerCode = 0xA0.toByte()
            this.wholeMessage = requestKey + responseKey
            this.answer = responseKey
            isValid = true
            length = answer.size
        }
        val requestVin = byteArrayOf(0x00,0x04,0x02,0x06)
        val responseVin = byteArrayOf(0x00,0x12,0xA0.toByte(), 0x53, 0x41, 0x52, 0x52 ,0x4A, 0x5A, 0x4C, 0x4C, 0x5A, 0x59,0x4D, 0x13,0x55,0x72,0xCA.toByte())
        val fakeAnswerVin = OBDMessage().apply {
            wholeMessage = requestVin + responseVin
            answer = responseVin
            answerCode = 0xA0.toByte()
            isValid = true
            length = responseVin.size
        }
        coEvery { ds2.write(eq(address), any<ByteArray>(), any()) } returnsMany listOf(fakeSecurityAnswer, fakeKeyAnswer, fakeAnswerVin)
        val vin = ModuleBCU(ds2).getVIN()
        assertNotNull(vin)
        assertEquals("SARRJZLLZYM135572",vin)
    }
}