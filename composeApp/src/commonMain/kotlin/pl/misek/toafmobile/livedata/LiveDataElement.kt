package pl.misek.toafmobile.livedata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.modules.enums.CarType

@Serializable
data class LivaDataElementContainerJson(
    val livedata : List<LiveDataElementJson>
)
@Serializable
data class LiveDataElementValuesJson(
    var data : String,
    var text : String
)

@Serializable
data class LiveDataElementJson (
    var name: String = "",
    var block: String? = "",
    var byte: String? = "",
    var mask: String? = "",
    @SerialName("factor_a") var factorA: String? = null,
    @SerialName("factor_b") var factorB: String? = null,
    var unit: String? = "",
    var values: MutableList<LiveDataElementValuesJson>? = null,
    var visible: String? = "",
    var u2: String? = "",
    @SerialName("invert_a") var invertA: String? = "",
    var carType: MutableList<String>? = null,
    var format: String? = "",
    var service : String? = "",
    var swap : String? = ""
)

data class LiveDataElement(
    var name: String = "",
    var descriptionResKey: StringResource? = null,
    var description: String? = null,
    var block: Int = 0,
    var byteInBlock: Int = 0,
    var mask: Long = 0,
    var factorA: Float = 1f,
    var factorB: Float = 0f,
    var unit: String = "",
    var values: MutableList<Pair<Int, StringResource?>>? = null,
    var selected: Boolean = false,
    var visible: Boolean = true,
    var u2: Boolean = false,
    var invertA: Boolean = false,
    var supportedCarType: MutableList<CarType>? = null,
    var formatedValue : String = "",
    var liveDataFormat: LiveDataFormat = LiveDataFormat.Standard,
    var service: Int = 0,
    var swap : Boolean = false
)
enum class LiveDataFormat {
    Standard,
    String
}