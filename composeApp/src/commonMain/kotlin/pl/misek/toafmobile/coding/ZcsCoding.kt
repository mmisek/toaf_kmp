package pl.misek.toafmobile.coding

class ZcsCoding {
    private var tag = "ZcsCoding"
    var gm: ByteArray = ByteArray(0)
    var gmChecksum: Byte = 0x00
    var sa: ByteArray = ByteArray(0)
    var saChecksum: Byte = 0x00
    var vn: ByteArray = ByteArray(0)
    var vnChecksum: Byte = 0x00

    constructor()

    constructor(data: ByteArray) {
        try {
            gm = data.copyOfRange( 0, 4)
            gmChecksum = data[4]
            sa = data.copyOfRange(5, 13)
            saChecksum = data[13]
            vn = data.copyOfRange(14, 19)
            vnChecksum = data[19]
        } catch (ex: Exception) {
            println("$tag, ${ex.toString()}")
        }
    }

    val bytes: ByteArray
        get() {
            val gmTemp = gm + byteArrayOf(gmChecksum)
            val saTemp = sa + byteArrayOf(saChecksum)
            val vnTemp = vn + byteArrayOf(vnChecksum)
            return gmTemp + saTemp +vnTemp
        }
}
