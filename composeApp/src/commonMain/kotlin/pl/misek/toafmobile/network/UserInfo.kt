package pl.misek.toafmobile.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserInfo(
    @SerialName("full_version") val fullVersion: String = "N",
    @SerialName("bcu_keys") var bcuKeys: Int = 0,
    val email: String = "",
    val username :String = "",
    @SerialName("eka_codes") var ekaKeys : Int = 0
    )
{
    var isLogged : ServerConnectionState = ServerConnectionState.DISCONNECTED

    fun isFullVersion() :Boolean
    {
        return fullVersion == "Y"
    }
}
@Serializable
data class UserInfoResponse(
    val code : Int,
    val message :List<UserInfo>
)
