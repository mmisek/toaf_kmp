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
}
