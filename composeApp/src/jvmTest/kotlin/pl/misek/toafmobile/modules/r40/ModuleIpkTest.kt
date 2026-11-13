package pl.misek.toafmobile.modules.r40

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolDS2
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ModuleIpkTest {
    private val address = ModuleAddress.MODULE_IPK.address
    private val ds2 = mockk<ProtocolDS2>()

    @Test
    fun getIdentificationTest() = runTest{
        val answer = byteArrayOf(0x80.toByte(), 0x12, 0xA0.toByte(), 0xDA.toByte(), 0xA1.toByte(), 0xAF.toByte(),
            0x8D.toByte(), 0x1B, 0x02, 0x02, 0x01, 0x0A, 0x96.toByte(), 0x10, 0x10 ,0x3B, 0x0F, 0xBF.toByte())
        val fakeAnswer = OBDMessage().apply {
            this.answerCode = 0xA0.toByte()
            this.wholeMessage = byteArrayOf(0x80.toByte(), 0x04,0x00,0x84.toByte()) + answer
            this.answer = answer
            isValid = true
            length = answer.size
        }

        coEvery { ds2.write(eq(address), any<ByteArray>(), any()) } returns fakeAnswer
        val expect = ModuleInfo().apply {
            id1 = "YAC110477"
            hw = "21"
            ci = "02"
            di = "02"
            bi = "01"
            producer = "VDO"
            date = "10/96"
            sw = "10"
        }
        val ident = ModuleIPK(ds2).getIdentification()
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
        val request = byteArrayOf(0x80.toByte(),0x02,0x02,0x02,0x85.toByte())
        val response = byteArrayOf(80.toByte(),0x0A,0xA0.toByte(),0x02,0x59,0x4D,0x14,0x25,0x78,0x75)
        val fakeAnswer = OBDMessage().apply {
            wholeMessage = request + response
            answer = response
            answerCode = 0xA0.toByte()
            isValid = true
            length = response.size
        }
        coEvery { ds2.write(eq(address), any<ByteArray>()) } returns fakeAnswer
        val vin = ModuleIPK(ds2).getVIN()
        assertNotNull(vin)
        assertEquals("YM142578",vin)
    }

    @Test
    fun getOdometerTest() = runTest {
        val request = byteArrayOf(0x80.toByte(),0x02,0x02,0x01,0x86.toByte())
        val response = byteArrayOf(80.toByte(),0x08,0xA0.toByte(),0x01,0x14,0x63,0x50,0x0E)
        val fakeAnswer = OBDMessage().apply {
            wholeMessage = request + response
            answer = response
            answerCode = 0xA0.toByte()
            isValid = true
            length = response.size
        }
        coEvery { ds2.write(eq(address), any<ByteArray>()) } returns fakeAnswer
        val odo = ModuleIPK(ds2).getOdometer()
        assertNotNull(odo)
        assertEquals("146350",odo)
    }

    @Test
    fun getAllErrorsTest() = runTest{
        val request = byteArrayOf(0x80.toByte(),0x05,0x04,0x01,0x80.toByte())
        val response = byteArrayOf(80.toByte(),0x14,0xA0.toByte(),0x01,0xBE.toByte(),0x48,0x28,0x00,0x00,0x00,0x00,0x00,
            0x00,0x00,0x00,0x00,0xEB.toByte())
        val fakeAnswer = OBDMessage().apply {
            wholeMessage = request + response
            answer = response
            answerCode = 0xA0.toByte()
            isValid = true
            length = response.size
        }
        // getAllErrors wola interfaceWrite(data, 250), czyli przeciazenie z timeoutem:
        // write(Byte, ByteArray, Long?). Zaslepka bez trzeciego argumentu trafia w inne
        // przeciazenie i MockK zglasza "no answer found".
        coEvery { ds2.write(eq(address), any<ByteArray>(), any<Long>()) } returns fakeAnswer
        val errors = ModuleIPK(ds2).getAllErrors()
        assertNotNull(errors)
        assertEquals(1, errors.size)
        assertEquals("BE", errors[0].code)
        assertEquals(3,errors[0].details.count())
        assertEquals("module_error_count",errors[0].details[0].first.key)
        assertEquals(40,errors[0].details[0].second)
        assertEquals("ipk_error_desc_04",errors[0].details[1].first.key)
        assertNull(errors[0].details[1].second)
        assertEquals("ipk_error_desc_40",errors[0].details[2].first.key)
        assertNull(errors[0].details[2].second)
    }
}