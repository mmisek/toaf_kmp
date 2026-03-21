package pl.misek.toafmobile.modules.containers

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class EHCAdjustmentData(val data: ByteArray?) {
    var offsetFrontLeft: Short = 0
    var offsetFrontRight: Short = 0
    var offsetRearLeft: Short = 0
    var offsetRearRight: Short = 0

    var offsetFrontLeftBelt: Short = 0
    var offsetFrontRightBelt: Short = 0
    var offsetRearLeftBelt: Short = 0
    var offsetRearRightBelt: Short = 0

    init {
        if (data != null && data.size >= 16) {
            val byteData = ByteBuffer.allocate(data.size).put(data).order(ByteOrder.LITTLE_ENDIAN)
            offsetFrontLeft = byteData.getShort(0)
            offsetFrontRight = byteData.getShort(2)
            offsetRearLeft = byteData.getShort(4)
            offsetRearRight = byteData.getShort(6)

            offsetFrontLeftBelt = byteData.getShort(8)
            offsetFrontRightBelt = byteData.getShort(10)
            offsetRearLeftBelt = byteData.getShort(12)
            offsetRearRightBelt = byteData.getShort(14)
        }
    }
}
