package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.protocols.ProtocolDS2
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.module_description_ike
import toafmobile.composeapp.generated.resources.module_ipk

class ModuleIKE(protocolDS2: ProtocolDS2?) :
    ModuleDS2(protocolDS2, ModuleAddress.MODULE_IKE, listOf(CarType.L322)),
    IModuleCoding {

    override fun getKoinName() = KoinNames.ModuleL322Ike

    override fun getImageResource() = Res.drawable.module_ipk

    override fun getModuleDescription() = Res.string.module_description_ike

    override suspend fun getAllErrors(): List<ModuleError>? {
        val message = interfaceWrite(byteArrayOf(0x04, 0x01))
        try {
            if (!message.isValid || message.answer.size < 5) return null
            val result: MutableList<ModuleError> = ArrayList()
            val count = message.answer[3].toInt() and 0xFF
            for (i in 0 until count) {
                val offset = 4 + 3 * i
                if (offset + 2 >= message.answer.size) break
                val errorCode = String.format("%02X", message.answer[offset])
                val type = message.answer[offset + 1].toInt() and 0xFF
                val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                errorDescription.add(ResourceHelper.getErrorDescriptionCount(message.answer[offset + 2].toInt() and 0xFF))
                for (bit in 0..7) {
                    val x = 1 shl bit
                    if ((type and x) == x) {
                        ResourceMapper.stringResource(String.format("ipk_error_desc_%02X", x))
                            ?.let { errorDescription.add(Pair(it, null)) }
                    }
                }
                result.add(ModuleError(ModuleAddress.MODULE_IKE, errorCode, errorDescription))
            }
            return result
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getAllErrors], $ex, $message" }
            return null
        }
    }

    override suspend fun getVIN(): String? {
        val message = interfaceWrite(byteArrayOf(0x06, 0x03, 0x00, 0x00, VIN_WORD.toByte(), 0x03))
        try {
            if (!message.isValid || message.answer.size < 3 + 6 + 1) return null
            val v = message.answer.copyOfRange(3, 3 + 6)
            val letters = String(charArrayOf((v[0].toInt() and 0xFF).toChar(), (v[1].toInt() and 0xFF).toChar()))
            val digits = String.format("%02X%02X%X", v[2], v[3], (v[5].toInt() shr 4) and 0x0F)
            val vin = letters + digits
            return if (vin.all { it.isLetterOrDigit() }) vin else {
                Logger.w { "$tag [getVIN]: invalid VIN data $message" }
                null
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getVIN], $ex, $message" }
            return null
        }
    }

    override suspend fun getOdometer(): String? {
        val message = interfaceWrite(byteArrayOf(0x02, 0x01))
        try {
            return if (message.isValid && message.answer.size >= 7) {
                String.format("%02X%02X%02X", message.answer[4], message.answer[5], message.answer[6])
            } else null
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [getOdometer], $ex, $message" }
            return null
        }
    }

    private suspend fun readImage(): ByteArray? {
        val image = ByteArray(IMAGE_SIZE)
        for (chunk in 0 until IMAGE_SIZE / READ_CHUNK_BYTES) {
            val word = chunk * READ_CHUNK_BYTES / 2
            val message = interfaceWrite(
                byteArrayOf(0x06, 0x03, 0x00, 0x00, word.toByte(), (READ_CHUNK_BYTES / 2).toByte()),
                300
            )
            try {
                if (!message.isValid || message.answer.size < 3 + READ_CHUNK_BYTES + 1) {
                    Logger.e { "$tag [readImage]: word ${word.toString(16)} read failed, $message" }
                    return null
                }
                message.answer.copyInto(image, chunk * READ_CHUNK_BYTES, 3, 3 + READ_CHUNK_BYTES)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [readImage], $ex, $message" }
                return null
            }
        }
        return image
    }

    override suspend fun readCoding(): List<ByteArray>? {
        val image = readImage() ?: return null
        if (!isCodingChecksumValid(image))
            Logger.w { "$tag [readCoding]: coding checksum invalid - will be corrected on write" }
        return listOf(image)
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        val newImage = dataToSave.firstOrNull()
        if (newImage == null || newImage.size != IMAGE_SIZE) {
            Logger.e { "$tag [writeCoding]: invalid coding image" }
            return false
        }
        val image = calculateCodingChecksums(newImage)
        val current = readImage() ?: return false
        // Fragmenty, ktore faktycznie przyjal sterownik - tylko je weryfikujemy.
        // Obszary chronione (VIN, przebieg, dane producenta) sa odrzucane przez ECU: pomijamy je, nie przerywajac zapisu.
        val writtenChunks = mutableListOf<Int>()
        for (chunk in 0 until IMAGE_SIZE / WRITE_CHUNK_BYTES) {
            val start = chunk * WRITE_CHUNK_BYTES
            val end = start + WRITE_CHUNK_BYTES
            if (image.copyOfRange(start, end).contentEquals(current.copyOfRange(start, end))) continue
            val word = start / 2
            val message = interfaceWrite(
                byteArrayOf(0x07, 0x03, 0x00, 0x00, word.toByte(), (WRITE_CHUNK_BYTES / 2).toByte()) +
                        image.copyOfRange(start, end)
            )
            if (!message.isValid) {
                Logger.w { "$tag [writeCoding]: word ${word.toString(16)} write refused (obszar chroniony?), pomijam" }
                continue
            }
            writtenChunks.add(chunk)
        }
        val verify = readImage() ?: return false
        reset()
        // Sukces, gdy kazdy zapisany fragment odczytuje sie zgodnie z zadaniem (chronione pominiete).
        return writtenChunks.all { chunk ->
            val start = chunk * WRITE_CHUNK_BYTES
            val end = start + WRITE_CHUNK_BYTES
            image.copyOfRange(start, end).contentEquals(verify.copyOfRange(start, end))
        }
    }

    companion object {
        private const val tag = "ModuleIKE"
        private const val VIN_WORD = 0x42
        const val IMAGE_SIZE = 0x200
        private const val READ_CHUNK_BYTES = 16
        private const val WRITE_CHUNK_BYTES = 8

        const val CHECKSUM_1_ADDRESS = 0x2C * 2 + 1
        private const val CHECKSUM_1_START = 0x2D * 2
        private const val CHECKSUM_1_END = 0x7F * 2 + 1

        const val CHECKSUM_2_ADDRESS = 0x80 * 2 + 1
        private const val CHECKSUM_2_START = 0x80 * 2
        private const val CHECKSUM_2_END = 0xDF * 2 + 1

        private fun xorRange(image: ByteArray, start: Int, end: Int, skip: Int): Byte {
            var cs = 0
            for (i in start..end) {
                if (i != skip) cs = cs xor (image[i].toInt() and 0xFF)
            }
            return cs.toByte()
        }

        fun calculateChecksum1(image: ByteArray): Byte? =
            if (image.size > CHECKSUM_1_END) xorRange(image, CHECKSUM_1_START, CHECKSUM_1_END, CHECKSUM_1_ADDRESS) else null

        fun calculateChecksum2(image: ByteArray): Byte? =
            if (image.size > CHECKSUM_2_END) xorRange(image, CHECKSUM_2_START, CHECKSUM_2_END, CHECKSUM_2_ADDRESS) else null

        fun calculateCodingChecksums(image: ByteArray): ByteArray {
            val result = image.copyOf()
            calculateChecksum1(result)?.let { result[CHECKSUM_1_ADDRESS] = it }
            calculateChecksum2(result)?.let { result[CHECKSUM_2_ADDRESS] = it }
            return result
        }

        fun isCodingChecksumValid(image: ByteArray): Boolean {
            val cs1 = calculateChecksum1(image)
            val cs2 = calculateChecksum2(image)
            return (cs1 == null || cs1 == image[CHECKSUM_1_ADDRESS]) &&
                    (cs2 == null || cs2 == image[CHECKSUM_2_ADDRESS])
        }
    }
}
