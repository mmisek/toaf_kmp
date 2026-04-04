package pl.misek.toafmobile.network

import androidx.compose.ui.text.toLowerCase
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.sentry.kotlin.multiplatform.Sentry

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

import pl.misek.toafmobile.backup.BackupElement
import pl.misek.toafmobile.backup.BackupElementResponse
import pl.misek.toafmobile.billing.CheckPurchaseResponse
import pl.misek.toafmobile.billing.StripeProductInfo
import pl.misek.toafmobile.billing.StripeProductInfoResponse
import pl.misek.toafmobile.billing.StripeSession
import pl.misek.toafmobile.billing.StripeSessionResponse
import java.util.Currency
import java.util.Locale

class ToafApi(private val client: HttpClient) {
    private val tag = "ToafApi"
    //private val url = "https://toaf.roverki.eu:5999"
    private val url = "http://192.168.1.3:5999"

    suspend fun loginUser(): ServerConnectionState {
        return try {
            val response = client.post("$url/user/login")
            if (response.status == HttpStatusCode.OK)
                ServerConnectionState.CONNECTED
            else
                ServerConnectionState.DISCONNECTED
        } catch (ex: Exception) {
            Logger.i {"$tag: ${ex.toString()}"}
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
            } else {
                Logger.e {"$tag: ${result.bodyAsText()}"}
                null
            }
        } catch (ex: Exception) {
            Logger.i {"$tag: $ex"}
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
            } else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                null
            }
        } catch (ex: Exception) {
            Logger.i{"$tag: $ex"}
            null
        }
    }

    suspend fun addCoding(codingData: JsonObject): Boolean {
        return try {
            val response = client.post("$url/coding/add/") {
                contentType(ContentType.Application.Json)
                setBody(codingData.toString())
            }
            return if (response.status == HttpStatusCode.OK)
                true
            else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                false
            }

        } catch (ex: Exception) {
            Logger.i {"$tag: $ex"}
            false
        }
    }

    suspend fun deleteUser(): Boolean {
        return try {
            val response = client.post("$url/user/delete/")
            return if (response.status == HttpStatusCode.OK)
                true
            else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                false
            }
        } catch (ex: Exception) {
            Logger.i {"$tag: $ex"}
            false
        }
    }

    suspend fun addUser(data: JsonObject): Boolean {
        return try {
            val response = client.post("$url/user/add/") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            return if (response.status == HttpStatusCode.OK)
                true
            else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                false
            }
        } catch (ex: Exception) {
            Logger.i {"$tag: $ex"}
            false
        }
    }

    suspend fun userChangePass(data: JsonObject): Boolean {
        return try {
            val response = client.post("$url/user/pass/") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            return if (response.status == HttpStatusCode.OK)
                true
            else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                false
            }
        } catch (ex: Exception) {
            Logger.i {"$tag: $ex"}
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
            }
            else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                null
            }

        } catch (ex: Exception) {
            Logger.i {"$tag: $ex"}
            null
        }
    }

    suspend fun updateUserInfo(userInfo: UserInfo): Boolean {
        return try {
            val response = client.post("$url/user/info/update") {
                contentType(ContentType.Application.Json)
                setBody(userInfo)
            }
            return if (response.status == HttpStatusCode.OK)
                true
            else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                false
            }
        } catch (ex: Exception) {
            Logger.i {"$tag: $ex"}
            false
        }
    }

    suspend fun userAddPurchases(data: JsonObject): Boolean {
        return try {
            val response = client.post("$url/user/purchases/add") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            return if (response.status == HttpStatusCode.OK)
                true
            else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                false
            }
        } catch (ex: Exception) {
            Logger.i {"$tag: $ex"}
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
            return if (response.status == HttpStatusCode.OK)
                true
            else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                false
            }
        } catch (ex: Exception) {
            Logger.i {"$tag: $ex"}
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
            return if (response.status == HttpStatusCode.OK)
                true
            else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                false
            }
        } catch (ex: Exception) {
            Logger.i {"$tag: $ex"}
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

    suspend fun getStripeProductInfo(productName : String): StripeProductInfo? {
        return try {
            val supportedCurrency = listOf("PLN","EUR","USD")
            var currencyCode = try {
                Currency.getInstance(Locale.getDefault()).currencyCode
            } catch ( _: Exception)
            {
                "EUR"
            }
            if (!supportedCurrency.contains(currencyCode))
                currencyCode = "EUR"
            val data = buildJsonObject {
                put("productName", productName.lowercase())
                put("currency", currencyCode)
            }
            val response = client.post("$url/payments/products") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK) {
                val productResponse = response.body<StripeProductInfoResponse>()
                return productResponse.data
            }
            else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                null
            }
        } catch (ex: Exception) {
            Logger.i { "$tag: $ex" }
            null
        }
    }

    suspend fun createStripeCheckout(priceId :String, productName: String) : StripeSession?
    {
        return try {
            val data = buildJsonObject {
                put("priceId", priceId)
                put("productName", productName.lowercase())
            }
            val response = client.post("$url/payments/create-checkout"){
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK) {
                val session = response.body<StripeSessionResponse>()
                session.data
            }
            else {
                Logger.e {"$tag: ${response.bodyAsText()}"}
                null
            }
        } catch (ex: Exception) {
            Logger.i { "$tag: $ex" }
            null
        }
    }
    suspend fun checkPurchase(purchaseId : String) : Boolean{
        return try {
            val data = buildJsonObject {
                put("purchaseId", purchaseId)
            }
            val response = client.post("$url/user/purchases/check"){
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK)
            {
                val body = response.body<CheckPurchaseResponse>()
                return (body.data != null && body.data == purchaseId)
            }
            else
            {
                Logger.e {"$tag : ${response.bodyAsText()}"}
                false
            }
        } catch (ex: Exception)
        {
            Logger.i { "$tag: $ex" }
            false
        }
    }
}