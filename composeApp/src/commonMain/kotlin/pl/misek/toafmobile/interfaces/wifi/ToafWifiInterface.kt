package pl.misek.toafmobile.interfaces.wifi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pl.misek.toafmobile.interfaces.IInterfaceDevice
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class ToafWifiInterface(ipAddress: String) : IInterfaceDevice {
    private var tag = "ToafWifiIInterface"
    val wifiAddress: String = ipAddress
    private val wifiPort = 65432
    private val bufferSize = 1024

    private var wifiTimeout = 2000
    private var outputStream: OutputStream? = null
    private var inputStream : InputStream? = null
    private var socket: Socket? = null

    override suspend fun write(data: ByteArray?, forceAnswer: Boolean, useDelay: Boolean): ByteArray? {
        return write(data, 300, true, useDelay)
    }

    override suspend fun write(
        data: ByteArray?,
        timeout: Int,
        forceAnswer: Boolean,
        useDelay: Boolean
    ): ByteArray? = withContext(Dispatchers.IO) {
            val jsonObject = buildJsonObject {
                data?.let {
                    val hexString = it.map { b ->
                        "0x${b.toUByte().toString(16).uppercase().padStart(2, '0')}"
                    }
                    put("write_data", JsonArray(hexString.map { h-> JsonPrimitive(h) }))
                }
            }
            println("$tag : $jsonObject")
            val jsonObjectOutput = write("WRITE", jsonObject, timeout)
            try {
                if (isValid(jsonObjectOutput))
                {
                    val array = jsonObjectOutput?.get("data")?.jsonArray
                    array?.let {
                        val sendResult = ByteArray(it.size)
                        for (i in 0 until  it.size)
                            sendResult[i] = it[i].jsonPrimitive.content.substring(2).toInt(16).toByte()
                        return@withContext sendResult
                    }
                }
            } catch (ex: Exception) {
                println("$tag: ${ex.toString()}")
            }
            return@withContext null
        }


    suspend fun write(message: String, data: JsonObject? = null, timeout: Int = 500): JsonObject? {
        return sendMessage(message, data, timeout)
    }

    override val isConnected: Boolean
        get() = true

    suspend fun getDeviceName(): JsonObject? {
        val result = this.write("INFO")
        if (result != null) {
            try {
                return result["data"]?.jsonObject
            } catch (ex: Exception) {
                println("$tag: ${ex.toString()}")
            }
        }
        return null
    }

    private fun getDeviceInfo(jsonObject: JsonObject?): ToafWifiDeviceInfo? {
        try {
            jsonObject?.let {
                if (it["device_name"]?.jsonPrimitive?.contentOrNull == "TOAF-WIFI")
                {
                    return ToafWifiDeviceInfo().apply {
                        deviceName = "TOAF-WIFI"
                        serialNumber = it["serial_number"]?.jsonPrimitive?.contentOrNull
                        hardwareVersion = it["hw"]?.jsonPrimitive?.contentOrNull
                        softwareVersion = it["sw"]?.jsonPrimitive?.contentOrNull
                        ipAddress = wifiAddress
                    }
                }
            }
        } catch (ex: Exception) {
            println("$tag: ${ex.toString()}")
        }

        return null
    }

    suspend fun getDeviceInfo(): ToafWifiDeviceInfo? {
        return getDeviceInfo(getDeviceName())
    }

    suspend fun getSsidAndPassword(): Pair<String, String>? {
        var result: Pair<String, String>? = null
        try {
            val jsonObject = write("CONFIG_READ")
            if (isValid(jsonObject)) {
                val data = jsonObject?.get("data")?.jsonObject
                if (data != null) {
                    val ssid = data["ssid"]?.jsonPrimitive?.content ?: ""
                    val password = data["password"]?.jsonPrimitive?.content ?: ""
                    result = Pair(ssid, password)
                }
            }
        } catch (ex: Exception) {
            println("$tag: ${ex.toString()}")
        }
        return result
    }

    suspend fun setSsidAndPassword(ssid: String, password: String): Boolean {
        return try {
            val jsonObject = buildJsonObject {
                put("ssid", ssid)
                put("password", password)
            }
            isValid(write("CONFIG_SAVE", jsonObject))
        } catch (ex: Exception) {
            println("$tag : ${ex.toString()}")
            false
        }
    }

    suspend fun updateFile(fileName: String, data: List<String>): Boolean {
        wifiTimeout = 10000
        var dataLength = 0
        var sentBytes = 0
        val maxTry = 3
        for (line in data) {
            try {
                for (i in 0..maxTry) {
                    dataLength += line.length
                    val jsonObject = buildJsonObject {
                        put("line",line)
                    }
                    val result = write("UPDATE_GET", jsonObject)
                    if (isValid(result)) {
                        sentBytes += line.length
                        break
                    } else if (i == maxTry) {
                        return false
                    }
                }
            } catch (ex: Exception) {
                println("$tag: ${ex.toString()}")
                return false
            }
        }

        wifiTimeout = 2000

        if (sentBytes != dataLength) {
            return false
        }
        try {
            val jsonObject = buildJsonObject {
                put("file", fileName)
            }
            val result = write("UPDATE_SAVE", jsonObject)
            val resultData = result?.get("data")?.jsonObject
            val size = resultData?.get("fileSize")?.jsonPrimitive?.content?.toInt()
            return isValid(resultData)  && size == sentBytes
        } catch (ex: Exception) {
            println("$tag: ${ex.toString()}")
            return false
        }
    }

    suspend fun resetDevice() {
        write("RESET")
    }

    suspend fun stopDevice(): Boolean {
        return (write("STOP") != null)
    }

    suspend fun enterDebugMode(): Boolean {
        return isValid(write("DEBUG"))
    }


    private fun isKwpStarFrame(jsonData: JsonObject?): Boolean {
        if (jsonData == null) return false
    val data = jsonData["write_data"]?.jsonArray ?: return false
        if (data.isEmpty()) return false

        try {
            fun getHex(index: Int): String = data[index].jsonPrimitive.content
            fun getIntFromHex(index: Int): Int = getHex(index).removePrefix("0x").toInt(16)

            val firstVal = getIntFromHex(0)
            val len = firstVal - 0x80

            if (len == (data.size - 4) && getHex(2) == "0xF7") {
                return getHex(2) != "0xC5"
            }

            if (data.size > 2) {
                if (getHex(0) == "0x80" && getHex(1) == "0x13" && getHex(2) == "0xF7") {
                    return true
                }
            }
        } catch (e: Exception) {
            println("Error parsing JSON: ${e.message}")
        }

        return false
    }

    private suspend fun sendMessage(message: String, data: JsonObject?, timeout: Int) : JsonObject? = withContext(Dispatchers.IO){

        try {
            val jsonData = buildJsonObject {
                put("command", message)
                put("data", data?: JsonNull)
                put("fast_init", isKwpStarFrame(data))
                put("timeout", timeout)
            }
            println("$tag : $jsonData")
            connect("")
            if (socket == null || !socket!!.isConnected) {
                return@withContext null
            }
            try {
                outputStream?.write(jsonData.toString().toByteArray())
                outputStream?.flush()
            } catch (ex : Exception) {
                println("$tag : ${ex.toString()}")
                disconnect()
                connect("")
                outputStream?.write(jsonData.toString().toByteArray())
                outputStream?.flush()
            }
            val jsonObject = listener() ?: return@withContext null
            val msg = jsonObject["message"]?.jsonPrimitive?.content
            if (msg == message)
                return@withContext jsonObject
            else
                return@withContext null
        } catch (ex: Exception) {
            println("$tag: ${ex.toString()}")
            return@withContext null
        }
        finally {
            disconnect()
        }
    }

    @Synchronized
    override fun connect(deviceName : String) {
        try {
            if (socket != null && socket!!.isConnected && !socket!!.isClosed)
                return
            disconnect()
            val newSocket = Socket()
            newSocket.tcpNoDelay = true
            newSocket.keepAlive = false
            newSocket.soTimeout = wifiTimeout
            newSocket.connect(InetSocketAddress(wifiAddress, wifiPort), 1000)
            socket = newSocket
            outputStream = newSocket.getOutputStream()
            inputStream = newSocket.getInputStream()

        } catch (ex: Exception) {
            println("$tag: ${ex.toString()}")
            disconnect()
            throw ex
        }
    }
    override fun disconnect() {
        try {
            outputStream?.close()
            inputStream?.close()
            socket?.close()
        } catch (_: Exception) {
        } finally {
            outputStream = null
            inputStream = null
            socket = null
        }
    }
    private suspend fun listener() : JsonObject? = withContext(Dispatchers.IO) {
        val collector = ByteArrayOutputStream()
        try {
            val buffer = ByteArray(bufferSize)
            val bytesRead = inputStream?.read(buffer) ?: -1
            if (bytesRead <=0) return@withContext null
            collector.write(buffer,0,bytesRead)
            delay(20)
            while ((inputStream?.available() ?: 0) > 0) {
                val additionalBytes = inputStream?.read(buffer) ?: -1
                if (additionalBytes > 0) {
                    collector.write(buffer, 0, additionalBytes)
                }
                delay(20)
            }
            val rawResponse = collector.toString("UTF-8").trim()
            println("$tag : $rawResponse")
            if (rawResponse.isEmpty()) return@withContext null
            return@withContext try {
                Json.parseToJsonElement(rawResponse).jsonObject
            }catch (ex: Exception) {
                println("$tag: ${ex.toString()}")
                null
            }
        } catch (ex: Exception) {
            println("$tag: ${ex.toString()}")
            return@withContext null
        }
    }

    private fun isValid(jsonObject : JsonObject?) : Boolean
    {
        return jsonObject?.get("return_code")?.jsonPrimitive?.content?.toInt() == 200
    }
}
