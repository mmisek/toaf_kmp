package pl.misek.toafmobile.protocols

data class OBDMessage (
    var message: ByteArray = ByteArray(0),
    var answer: ByteArray = ByteArray(0),
    var isValid: Boolean = false,
    var answerCode: Byte = 0,
    var length: Int = 0,
    var wholeMessage: ByteArray? = ByteArray(0)
)
{
    init {
        message = ByteArray(0)
        answer = ByteArray(0)
        isValid = false
        answerCode = 0
        length = 0
        wholeMessage = ByteArray(0)
    }
    override fun toString(): String {
        val msgHex = message.joinToString(" ") { "%02X".format(it) }
        val ansHex = answer.joinToString(" ") { "%02X".format(it) }
        val wholeHex = wholeMessage?.joinToString(" ") { "%02X".format(it) } ?: "null"

        return "OBDMessage(" +
                "message=[$msgHex], " +
                "answer=[$ansHex], " +
                "isValid=$isValid, " +
                "answerCode=$answerCode, " +
                "length=$length, " +
                "wholeMessage=[$wholeHex]" +
                ")"
    }
}
