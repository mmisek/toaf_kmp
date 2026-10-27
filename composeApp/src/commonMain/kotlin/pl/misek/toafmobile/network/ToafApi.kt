package pl.misek.toafmobile.network

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pl.misek.toafmobile.BuildKonfig
import pl.misek.toafmobile.backup.BackupElement
import pl.misek.toafmobile.backup.BackupElementResponse
import pl.misek.toafmobile.billing.CheckPurchaseResponse
import pl.misek.toafmobile.billing.StripeProductInfo
import pl.misek.toafmobile.billing.StripeProductInfoResponse
import pl.misek.toafmobile.billing.StripeSession
import pl.misek.toafmobile.billing.StripeSessionResponse
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.getPlatformName
import java.io.IOException
import java.net.UnknownHostException
import java.util.Currency
import java.util.Locale

class ToafApi(private val client: HttpClient, private val database: Database) {
    private val tag = "ToafApi"
    private val url = "https://toaf.roverki.eu:5999"
    //private val url = "http://192.168.1.5:5999"

    suspend fun isCredentialValid(): Boolean {
        val credentials = database.getLoginAndPassword()
        return (credentials != null && credentials.second.isNotEmpty() && credentials.first.isNotEmpty())
    }

    suspend fun loginUser(): ServerConnectionState {
        return try {
            val response = client.post("$url/user/login")
            if (response.status == HttpStatusCode.OK)
                ServerConnectionState.CONNECTED
            else
                ServerConnectionState.DISCONNECTED
        }  catch (ex: UnknownHostException) {
            ServerConnectionState.DISCONNECTED
        } catch (ex: HttpRequestTimeoutException) {
            ServerConnectionState.DISCONNECTED
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[loginUser]: ${ex.message}" }
            ServerConnectionState.DISCONNECTED
        }
    }

    suspend fun getCoding(data: JsonObject): Coding? {
        if (!isCredentialValid())
            return null
        return try {
            val result = client.post("$url/coding/get") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (result.status == HttpStatusCode.OK) {
                val body = result.body<CodingResponse>()
                body.message.firstOrNull()
            } else {
                Logger.i { "$tag: ${result.bodyAsText()}" }
                null
            }
        }  catch (ex: UnknownHostException) {
            null
        } catch (ex: HttpRequestTimeoutException) {
            null
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[getCoding]: ${ex.message}" }
            null
        }
    }

    suspend fun getCodingList(data: JsonObject, isPublic: Boolean): List<BackupElement>? {
        if (!isCredentialValid())
            return null
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
                Logger.i { "$tag: ${response.bodyAsText()}" }
                null
            }
        }  catch (ex: UnknownHostException) {
            null
        } catch (ex: HttpRequestTimeoutException) {
            null
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[getCodingList]: ${ex.message}" }
            null
        }
    }

    suspend fun addCoding(codingData: JsonObject): Boolean {
        if (!isCredentialValid())
            return false

        return try {
            val response = client.post("$url/coding/add/") {
                contentType(ContentType.Application.Json)
                setBody(codingData.toString())
            }
            if (response.status == HttpStatusCode.OK) {
                true
            } else {
                Logger.i { "$tag: ${response.bodyAsText()}" }
                false
            }
        }  catch (ex: UnknownHostException) {
            false
        } catch (ex: HttpRequestTimeoutException) {
            false
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[addCoding]: ${ex.message}" }
            false
        }
    }

    suspend fun deleteUser(): Boolean {
        if (!isCredentialValid())
            return false

        return try {
            val response = client.post("$url/user/delete/")
            if (response.status == HttpStatusCode.OK) {
                true
            } else {
                Logger.i { "$tag: ${response.bodyAsText()}" }
                false
            }
        }  catch (ex: UnknownHostException) {
            false
        } catch (ex: HttpRequestTimeoutException) {
            false
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[deleteUser]: ${ex.message}" }
            false
        }
    }

    suspend fun addUser(data: JsonObject): Boolean {
        if (!isCredentialValid())
            return false

        return try {
            val response = client.post("$url/user/add/") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK) {
                true
            } else {
                Logger.i { "$tag: ${response.bodyAsText()}" }
                false
            }
        }  catch (ex: UnknownHostException) {
            false
        } catch (ex: HttpRequestTimeoutException) {
            false
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[addUser]: ${ex.message}" }
            false
        }
    }

    suspend fun userChangePass(data: JsonObject): Boolean {
        if (!isCredentialValid())
            return false
        return try {
            val response = client.post("$url/user/pass/") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK) {
                true
            } else {
                Logger.i { "$tag: ${response.bodyAsText()}" }
                false
            }
        }  catch (ex: UnknownHostException) {
            false
        } catch (ex: HttpRequestTimeoutException) {
            false
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[userChangePass]: ${ex.message}" }
            false
        }
    }

    suspend fun getUserInfo(): UserInfo? {
        if (!isCredentialValid()) {
            Logger.i { "getUserInfo - Credential NOT VALID" }
            return null
        }
        return try {
            val response = client.post("$url/user/info/get")
            if (response.status == HttpStatusCode.OK) {
                val userInfoResponse = response.body<UserInfoResponse>()
                val userInfo = userInfoResponse.message.firstOrNull()
                if (userInfo != null)
                    userInfo.isLogged = ServerConnectionState.CONNECTED
                userInfo
            } else {
                Logger.i { "$tag: ${response.bodyAsText()}" }
                null
            }
        }  catch (ex: UnknownHostException) {
            null
        } catch (ex: HttpRequestTimeoutException) {
            null
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[getUserInfo]: ${ex.message}" }
            null
        }
    }

    suspend fun updateUserInfo(userInfo: UserInfo): Boolean {
        if (!isCredentialValid())
            return false
        return try {
            val response = client.post("$url/user/info/update") {
                contentType(ContentType.Application.Json)
                setBody(userInfo)
            }
            if (response.status == HttpStatusCode.OK) {
                true
            } else {
                Logger.i { "$tag: ${response.bodyAsText()}" }
                false
            }
        }  catch (ex: UnknownHostException) {
            false
        } catch (ex: HttpRequestTimeoutException) {
            false
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[updateUserInfo]: ${ex.message}" }
            false
        }
    }

    suspend fun userAddPurchases(data: JsonObject): Boolean {
        if (!isCredentialValid())
            return false
        return try {
            val response = client.post("$url/user/purchases/add") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK) {
                true
            } else {
                Logger.i { "$tag: ${response.bodyAsText()}" }
                false
            }
        }  catch (ex: UnknownHostException) {
            false
        } catch (ex: HttpRequestTimeoutException) {
            false
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[userAddPurchases]: ${ex.message}" }
            false
        }
    }

    suspend fun userConsumeBcuKey(vin: String?, keyCode: String?): Boolean {
        if (!isCredentialValid())
            return false
        val data = buildJsonObject {
            put("vin", vin ?: "")
            put("key_code", keyCode ?: "")
        }

        return try {
            val response = client.post("$url/user/keys/add") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK) {
                true
            } else {
                Logger.i { "$tag: ${response.bodyAsText()}" }
                false
            }
        }  catch (ex: UnknownHostException) {
            false
        } catch (ex: HttpRequestTimeoutException) {
            false
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[userConsumeBcuKey]: ${ex.message}" }
            false
        }
    }

    suspend fun uploadFlash(data: JsonObject, file: ByteArray): Boolean {
        if (!isCredentialValid())
            return false
        // Krotki upload (np. EEPROM 512 B zaraz po szybkim odczycie) trafia na przeterminowane polaczenie
        // keep-alive z puli - serwer zamyka socket ("unexpected end of stream"), a OkHttp nie ponawia
        // jednorazowego body multipart POST. Ponawiamy sami - druga proba idzie swiezym polaczeniem.
        val maxAttempts = 2
        for (attempt in 1..maxAttempts) {
            try {
                val response = client.submitFormWithBinaryData(
                    url = "$url/flash/add/",
                    formData = formData {
                        append("data.json", data.toString().toByteArray(), Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                            // Samo filename - nazwe czesci (name=...) Ktor dodaje z klucza; podanie pelnego
                            // "form-data; name=...; filename=..." dublowalo naglowek Content-Disposition.
                            append(HttpHeaders.ContentDisposition, "filename=\"data.json\"")
                        })
                        append("flash.bin", file, Headers.build {
                            append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                            append(HttpHeaders.ContentDisposition, "filename=\"flash.bin\"")
                        })
                    }
                ) {
                    timeout {
                        requestTimeoutMillis = 5000
                    }
                }
                return if (response.status == HttpStatusCode.OK) {
                    true
                } else {
                    Logger.i { "$tag: ${response.bodyAsText()}" }
                    false
                }
            } catch (ex: UnknownHostException) {
                return false
            } catch (ex: HttpRequestTimeoutException) {
                return false
            } catch (ex: IOException) {
                // Zerwane/przeterminowane polaczenie - ponow na kolejnej iteracji (swieze polaczenie).
                Logger.withTag(tag).w(ex) { "[uploadFlash]: proba $attempt/$maxAttempts, ${ex.message}" }
                if (attempt == maxAttempts)
                    return false
            } catch (ex: Exception) {
                Logger.withTag(tag).e(ex) { "[uploadFlash]: ${ex.message}" }
                return false
            }
        }
        return false
    }

    suspend fun getToafWifiUpdates(data: JsonObject): ToafWifiUpdates? {
        if (!isCredentialValid())
            return null
        return try {
            val response = client.post("$url/toaf/wifi/check_update") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK)
                response.body<ToafWifiUpdates>()
            else null
        }  catch (ex: UnknownHostException) {
            null
        } catch (ex: HttpRequestTimeoutException) {
            null
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[getToafWifiUpdates]: ${ex.message}" }
            null
        }
    }

    suspend fun getToafWifiUpdateFiles(data: JsonObject): List<ToafWifiUpdateFiles?>? {
        if (!isCredentialValid())
            return null
        return try {
            val response = client.post("$url/toaf/wifi/get_update") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK)
                response.body<List<ToafWifiUpdateFiles?>>()
            else null
        }  catch (ex: UnknownHostException) {
            null
        } catch (ex: HttpRequestTimeoutException) {
            null
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[getToafWifiUpdateFiles]: ${ex.message}" }
            null
        }
    }

    suspend fun setToafWifiUpdate(data: JsonObject): Boolean {
        if (!isCredentialValid())
            return false
        return try {
            val response = client.post("$url/toaf/wifi/save_update/") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            response.status == HttpStatusCode.OK
        }  catch (ex: UnknownHostException) {
            false
        } catch (ex: HttpRequestTimeoutException) {
            false
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[setToafWifiUpdate]: ${ex.message}" }
            false
        }
    }

    suspend fun checkToafVersion(): ToafVersionInfo? {
        return try {
            val body = ToafVersionInfo(
                versionName = BuildKonfig.VERSION_NAME,
                versionCode = BuildKonfig.VERSION_CODE,
                platform = getPlatformName()
            )
            val response = client.post("$url/toaf/version/") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status == HttpStatusCode.OK) {
                val info = response.body<ToafVersionInfoResponse>()
                return info.message
            }
            return null
        }  catch (ex: UnknownHostException) {
            null
        } catch (ex: HttpRequestTimeoutException) {
            null
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[checkToafVersion]: ${ex.message}" }
            null
        }
    }

    suspend fun getStripeProductInfo(productName: String): StripeProductInfo? {
        if (!isCredentialValid())
            return null
        return try {
            val supportedCurrency = listOf("PLN", "EUR", "USD")
            var currencyCode = try {
                Currency.getInstance(Locale.getDefault()).currencyCode
            } catch (_: Exception) {
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
            } else {
                Logger.i { "$tag: ${response.bodyAsText()}" }
                null
            }
        }  catch (ex: UnknownHostException) {
            null
        } catch (ex: HttpRequestTimeoutException) {
            null
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[getStripeProductInfo]: ${ex.message}" }
            null
        }
    }

    suspend fun createStripeCheckout(priceId: String, productName: String): StripeSession? {
        if (!isCredentialValid())
            return null
        return try {
            val data = buildJsonObject {
                put("priceId", priceId)
                put("productName", productName.lowercase())
            }
            val response = client.post("$url/payments/create-checkout") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK) {
                val session = response.body<StripeSessionResponse>()
                session.data
            } else {
                Logger.i { "$tag: ${response.bodyAsText()}" }
                null
            }
        }  catch (ex: UnknownHostException) {
            null
        } catch (ex: HttpRequestTimeoutException) {
            null
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[createStripeCheckout]: ${ex.message}" }
            null
        }
    }

    suspend fun checkPurchase(purchaseId: String): Boolean {
        if (!isCredentialValid())
            return false
        return try {
            val data = buildJsonObject {
                put("purchaseId", purchaseId)
            }
            val response = client.post("$url/user/purchases/check") {
                contentType(ContentType.Application.Json)
                setBody(data.toString())
            }
            if (response.status == HttpStatusCode.OK) {
                val body = response.body<CheckPurchaseResponse>()
                return (body.data != null && body.data == purchaseId)
            } else {
                Logger.i { "$tag : ${response.bodyAsText()}" }
                false
            }
        }  catch (ex: UnknownHostException) {
            false
        } catch (ex: HttpRequestTimeoutException) {
            false
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[checkPurchase]: ${ex.message}" }
            false
        }
    }

    suspend fun consumeEkaCode(): Boolean {
        if (!isCredentialValid())
            return false
        return try {
            val response = client.post("$url/user/eka_codes/consume")
            response.status == HttpStatusCode.OK
        }  catch (ex: UnknownHostException) {
            false
        } catch (ex: HttpRequestTimeoutException) {
            false
        } catch (ex: Exception) {
            Logger.withTag(tag).e(ex) { "[consumeEkaCode]: ${ex.message}" }
            false
        }
    }
}