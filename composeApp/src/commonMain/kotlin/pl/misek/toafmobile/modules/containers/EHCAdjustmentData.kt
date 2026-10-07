package pl.misek.toafmobile.modules.containers

import java.nio.ByteBuffer
import java.nio.ByteOrder

    /**
 * Dane abgleichu (telegram 0x40 / 0x41), 16 bajtow little endian:
 * - offsety zera czujnikow wysokosci [AD counts]: FL, FR, RL, RR
 * - wysokosci referencyjne do kontroli nieszczelnosci [1/8 mm]: FR, FL, RR, RL (kolejnosc wg SGBD EHC_2)
 */
data class EHCAdjustmentData(val data: ByteArray?) {
    var offsetFrontLeft: Short = 0
    var offsetFrontRight: Short = 0
    var offsetRearLeft: Short = 0
    var offsetRearRight: Short = 0

    var offsetFrontRightBelt: Short = 0
    var offsetFrontLeftBelt: Short = 0
    var offsetRearRightBelt: Short = 0
    var offsetRearLeftBelt: Short = 0

    init {
        if (data != null && data.size >= DATA_SIZE) {
            val byteData = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            offsetFrontLeft = byteData.getShort(0)
            offsetFrontRight = byteData.getShort(2)
            offsetRearLeft = byteData.getShort(4)
            offsetRearRight = byteData.getShort(6)

            offsetFrontRightBelt = byteData.getShort(8)
            offsetFrontLeftBelt = byteData.getShort(10)
            offsetRearRightBelt = byteData.getShort(12)
            offsetRearLeftBelt = byteData.getShort(14)
        }
    }

    fun toByteArray(): ByteArray = ByteBuffer
        .allocate(DATA_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putShort(offsetFrontLeft)
        .putShort(offsetFrontRight)
        .putShort(offsetRearLeft)
        .putShort(offsetRearRight)
        .putShort(offsetFrontRightBelt)
        .putShort(offsetFrontLeftBelt)
        .putShort(offsetRearRightBelt)
        .putShort(offsetRearLeftBelt)
        .array()

    companion object {
        const val DATA_SIZE = 16
    }
}
