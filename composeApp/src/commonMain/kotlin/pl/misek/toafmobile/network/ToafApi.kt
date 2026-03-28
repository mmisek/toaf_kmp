package pl.misek.toafmobile.network

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import pl.misek.toafmobile.backup.BackupElement
import pl.misek.toafmobile.backup.BackupElementResponse

class ToafApi(private val client: HttpClient) {
    private val tag = "ToafApi"
    private val url = "https://toaf.roverki.eu:5999"

    suspend fun loginUser(): ServerConnectionState {
        return try {
            val response = client.post("$url/user/login")
            if (response.status == HttpStatusCode.OK)
                ServerConnectionState.CONNECTED
            else
                ServerConnectionState.DISCONNECTED
        } catch (ex: Exception) {
            Logger.e{"$tag: ${ex.toString()}"}
            ServerConnectionState.DISCONNECTED
        }
    }

    suspend fun getCoding(data: JsonObject): Coding? {
        return try {
            val result = client.post("$url/coding/get") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (result.status == HttpStatusCode.OK) {
                val body = result.body<CodingResponse>()
                body.message.firstOrNull()
            } else null
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            null
        }
    }

    suspend fun getCodingList(data: JsonObject, isPublic: Boolean): List<BackupElement>? {
        val urlBackup = if (isPublic) "$url/coding/list/public" else "$url/coding/list"
        return try {
            val response = client.post(urlBackup) {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK) {
                val message = response.body<BackupElementResponse>()
                message.message
            } else null
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            null
        }
    }

    suspend fun addCoding(codingData: JsonObject): Boolean {
        return try {
            val response = client.post("$url/coding/add/") {
                contentType(ContentType.Application.Json)
                setBody(codingData.toString())
            }
            response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            false
        }
    }

    suspend fun deleteUser(): Boolean {
        return try {
            val response = client.post("$url/user/delete/")
            response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            false
        }
    }

    suspend fun addUser(data: JsonObject): Boolean {
        return try {
            val response = client.post("$url/user/add/") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            false
        }
    }

    suspend fun userChangePass(data: JsonObject): Boolean {
        return try {
            val response = client.post("$url/user/pass/") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            false
        }
    }

    suspend fun getUserInfo(): UserInfo? {
        return try {
            val response = client.post("$url/user/info/get")
            if (response.status == HttpStatusCode.OK) {
                val userInfoResponse = response.body<UserInfoResponse>()
                val userInfo = userInfoResponse.message.firstOrNull()
                if (userInfo != null)
                    userInfo.isLogged = ServerConnectionState.CONNECTED
                userInfo
            } else null
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            null
        }
    }

    suspend fun updateUserInfo(userInfo: UserInfo): Boolean {
        return try {
            val response = client.post("$url/user/info/update") {
                contentType(ContentType.Application.Json)
                setBody(userInfo)
            }
            response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            false
        }
    }

    suspend fun userAddPurchases(data: JsonObject): Boolean {
        return try {
            val response = client.post("$url/user/purchases/add") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            false
        }
    }

    suspend fun userConsumeBcuKey(vin: String?, keyCode: String?): Boolean {
        val data = buildJsonObject {
            put("vin", vin ?: "")
            put("key_code", keyCode ?: "")
        }

        return try {
            val response = client.post("$url/user/keys/add") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            false
        }
    }

    suspend fun uploadFlash(data: JsonObject, file: ByteArray): Boolean {
        return try {
            val response = client.submitFormWithBinaryData(
                url = "$url/flash/add/",
                formData = formData {
                    append("data.json", data.toString().toByteArray(), Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"data.json\"; filename=\"data.json\"")
                    })
                    append("flash.bin", file, Headers.build {
                        append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"flash.bin\"; filename=\"flash.bin\"")
                    })
                }
            ) {
                timeout {
                    requestTimeoutMillis = 5000
                }
            }
            response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            false
        }
    }

    suspend fun getToafWifiUpdates(data: JsonObject): ToafWifiUpdates? {
        return try {
            val response = client.post("$url/toaf/wifi/check_update") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK)
                response.body<ToafWifiUpdates>()
            else null
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            null
        }
    }

    suspend fun getToafWifiUpdateFiles(data: JsonObject): List<ToafWifiUpdateFiles?>? {
        return try {
            val response = client.post("$url/toaf/wifi/get_update") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK)
                response.body<List<ToafWifiUpdateFiles?>>()
            else null
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            null
        }
    }

    suspend fun setToafWifiUpdate(data: JsonObject): Boolean {
        return try {
            val response = client.post("$url/toaf/wifi/save_update/") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            false
        }
    }

    suspend fun checkToafVersion(data: JsonObject): Boolean {
        return try {
            val response = client.post("$url/toaf/version") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            response.status == HttpStatusCode.OK
        } catch (ex: Exception) {
            Logger.e{"$tag: $ex"}
            false
        }
    }
}