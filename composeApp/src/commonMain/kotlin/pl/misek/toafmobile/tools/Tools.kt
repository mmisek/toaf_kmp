package pl.misek.toafmobile.tools

object Tools {

    fun byteArrayToHexString(data: ByteArray): String {
        val sb = StringBuilder()
        for (b in data) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    fun byteArrayToHexStringJSON(data: ByteArray): String {
        val sb = StringBuilder()
        sb.append("[")
        for (b in data) sb.append(String.format("0x%02X, ", b))
        sb.append("]")
        return sb.toString()
    }

    fun stringToByteArray(data: String): ByteArray {
        val result = ByteArray(data.length / 2)
        var pos = 0
        var i = 0
        while (i < data.length) {
            val s = data.substring(i, i + 2)
            val x = s.toInt(16)
            result[pos++] = x.toByte()
            i += 2
        }
        return result
    }
}

