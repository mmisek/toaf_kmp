package pl.misek.toafmobile.tools

import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import pl.misek.toafmobile.coding.CodingElement
import pl.misek.toafmobile.coding.CodingElementContainerJson
import pl.misek.toafmobile.coding.CodingElementJson
import pl.misek.toafmobile.coding.ZCSCodingElement
import pl.misek.toafmobile.coding.ZCSCodingElementJson
import pl.misek.toafmobile.coding.ZcsCodingElementContainerJson
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.control.ControlElementContainerJson
import pl.misek.toafmobile.control.ControlElementJson
import pl.misek.toafmobile.control.ControlSetValue
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.livedata.LivaDataElementContainerJson
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.livedata.LiveDataElementJson
import pl.misek.toafmobile.livedata.LiveDataFormat
import pl.misek.toafmobile.modules.DDEFreezeFrame
import pl.misek.toafmobile.modules.DDEFreezeFrameContainerJson
import pl.misek.toafmobile.modules.FreezeFrameElement
import pl.misek.toafmobile.modules.FreezeFrameElementContainerJson
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.ModuleAddress
import toafmobile.composeapp.generated.resources.Res
import java.util.Locale

private var tag = "JSONSupport"

object JsonSupport {
    suspend fun getCodingList(
        moduleAddress: ModuleAddress,
        moduleCodingIndex: Int
    ): List<CodingElement> {
        val path = "files/Coding/${moduleAddress.moduleName.lowercase(Locale.getDefault())}_coding.json"
        val bytes = Res.readBytes(path)
        val jsonString = bytes.decodeToString()
        val container = Json.decodeFromString<CodingElementContainerJson>(jsonString)
        val result = container.elements.map {json ->
            CodingElementJson().apply {
                name = json.name
                block = json.block
                byte = json.byte
                mask = json.mask
                codingIndex = json.codingIndex
                codingValues = json.codingValues
                codingCustomValues = json.codingCustomValues
                visible = json.visible
            }
        }
        val returnResult = result.map { json ->
            CodingElement().apply {
                val resKey = "${moduleAddress.moduleName.lowercase()}_coding_${json.name}".lowercase()
                name = json.name.lowercase()
                description = ResourceMapper.stringResource(resKey)
                block = decodeHexToInt(json.block)
                byteInBlock = decodeHexToInt(json.byte)
                mask = decodeHexToInt(json.mask)
                json.codingIndex?.forEach {
                    x->codingIndex.add(decodeHexToInt(x))
                }
                codingValues = ArrayList()
                json.codingValues?.forEach { v ->
                    codingValues.add(Pair(decodeHexToInt(v.data), ResourceMapper.stringResource("coding_${v.text?.lowercase()}")))
                }
                json.codingCustomValues?.let {
                    codingCustomValues = Pair(it.min?.toInt() ?: 0, it.max?.toInt() ?: 0)
                }
                visible = json.visible.toBooleanStrictOrNull() ?: true
            }
        }
        return returnResult.filter { x->x.visible == true and x.codingIndex.contains(moduleCodingIndex) }
    }

    suspend fun getLiveDataElements(
        moduleAddress: ModuleAddress
    ): List<LiveDataElement>? {
        return getLiveDataElements(
            moduleAddress.showName.lowercase()
        )
    }

    suspend fun getLiveDataElements(moduleName: String): List<LiveDataElement>? {
        val path = "files/LiveData/${moduleName.lowercase(Locale.getDefault())}_live_data.json"
        val bytes = Res.readBytes(path)
        val jsonString = bytes.decodeToString()
        val container = Json.decodeFromString<LivaDataElementContainerJson>(jsonString)
        val result = container.livedata.map { json ->
            LiveDataElementJson().apply {
                name = json.name
                block = json.block
                byte = json.byte
                mask = json.mask
                factorA = json.factorA
                factorB = json.factorB
                unit = json.unit
                values = json.values
                visible = json.visible
                u2 = json.u2
                invertA = json.invertA
                carType = json.carType
                format = json.format
            }
        }
        return try {
            result.map { elementJson ->
                LiveDataElement().apply {
                    val resRey = "${moduleName.lowercase()}_live_data_${elementJson.name.lowercase()}"
                    name = elementJson.name
                    descriptionResKey = ResourceMapper.stringResource(resRey)
                    block = decodeHexToInt(elementJson.block)
                    byteInBlock = decodeHexToInt(elementJson.byte)
                    mask = if (elementJson.mask.isNullOrEmpty()) 0L else decodeHexToLong(elementJson.mask)
                    factorA = elementJson.factorA?.toFloat() ?: 1f
                    factorB = elementJson.factorB?.toFloat() ?: 0f
                    unit = elementJson.unit ?: ""
                    elementJson.values?.let { v->
                        values = ArrayList()
                        v.forEach { e ->
                            val vKey = "${moduleName.lowercase()}_live_data_value_${e.text.lowercase()}"
                            values?.add(Pair(decodeHexToInt(e.data), ResourceMapper.stringResource(vKey)))
                        }
                    }
                    visible = elementJson.visible?.toBooleanStrictOrNull() ?: true
                    u2 = elementJson.u2?.toBooleanStrictOrNull() ?: false
                    invertA = elementJson.invertA?.toBooleanStrictOrNull() ?: false
                    elementJson.carType?.let { v ->
                        supportedCarType = ArrayList()
                        v.forEach { e->
                            supportedCarType?.add(CarType.valueOf(e))
                        }
                    }
                    liveDataFormat = if (elementJson.format.isNullOrEmpty())
                        LiveDataFormat.Standard
                    else
                        LiveDataFormat.valueOf(elementJson.format!!)
                }
            }
        } catch (ex : Exception)
        {
            Logger.e{"$tag : getLiveDataElements(), ${ex.toString()}"}
            return null
        }
    }

    suspend fun getControlElements(moduleName: String): List<ControlElement>? {
        val path = "files/Control/${moduleName.lowercase(Locale.getDefault())}_control.json"
        val bytes = Res.readBytes(path)
        val jsonString = bytes.decodeToString()
        val container = Json.decodeFromString<ControlElementContainerJson>(jsonString)
        val result = container.elements.map { json ->
            ControlElementJson().apply {
                name = json.name
                byte = json.byte
                block = json.block
                value = json.value
                visible = json.visible
                controlSetValue = json.controlSetValue
            }
        }
        return try {
            result.map { elementJson ->
                ControlElement().apply {
                    name = elementJson.name.toString()
                    val resKey = "${moduleName.lowercase()}_control_${elementJson.name}"
                    description = ResourceMapper.stringResource(resKey)
                    block = decodeHexToInt(elementJson.block)
                    byteInBlock = decodeHexToInt(elementJson.byte)
                    value = decodeHexToInt(elementJson.value)
                    visibility = elementJson.visible?.toBooleanStrictOrNull() ?: true
                    elementJson.controlSetValue?.let { cv ->
                        controlSetValue = ControlSetValue(
                            min = cv.min?.toInt() ?: 0,
                            max = cv.max?.toInt() ?: 0,
                            unit = cv.unit?: "",
                            value = 0
                        )
                    }
                }
            }.filter { x->x.visibility }
        }
        catch (ex : Exception)
        {
            Logger.e{"$tag : getControlElements, ${ex.toString()}"}
            return null
        }
    }

    suspend fun getFreezeFrameList(): List<DDEFreezeFrame>? {
        var path = "files/dde_errors_ff.json"
        var bytes = Res.readBytes(path)
        var jsonString = bytes.decodeToString()
        val container1 = Json.decodeFromString<FreezeFrameElementContainerJson>(jsonString)
        val resultFreezeFrameElements = container1.list.map { json ->
            FreezeFrameElement().apply {
                val resKey = "dde_error_ff_${json.name.lowercase()}"
                name = json.name
                description = ResourceMapper.stringResource(resKey)
                mask = decodeHexToLong(json.mask)
                byteInBlock = decodeHexToInt(json.byteInBlock)
                factorA = json.factorA.toFloatOrNull() ?: 1f
                factorB = json.factorB.toFloatOrNull() ?: 0f
                u2 = json.u2?.toBooleanStrictOrNull() ?: false
            }
        }
        path = "files/dde_errors_ff_for_error.json"
        bytes = Res.readBytes(path)
        jsonString = bytes.decodeToString()
        val container2 = Json.decodeFromString<DDEFreezeFrameContainerJson>(jsonString)
        return try {
            container2.list.map { json ->
                DDEFreezeFrame().apply {
                    code = json.code
                    ffItems = ArrayList()
                    json.ffItems.forEach { item ->
                        resultFreezeFrameElements
                            .firstOrNull { x -> x.name == item }
                            ?.let { ffItems.add(it) }
                    }
                    ffDetails = ArrayList()
                    json.ffDetails.forEach { item ->
                        val resKey = "dde_error_ff_detail_${item.text}"
                        ffDetails.add(Pair(decodeHexToInt(item.data), ResourceMapper.stringResource(resKey)))
                    }

                }
            }
        }
        catch (ex : Exception)
        {
            Logger.e{"$tag : getFreezeFrameList, ${ex.toString()}"}
            return null
        }
    }

    fun getFreezeFrameForErrorCode(list: List<DDEFreezeFrame>, code: String): DDEFreezeFrame {
        return list.stream().filter { x: DDEFreezeFrame -> x.code == code }.findFirst().get()
    }

    suspend fun getEMSErrorFreezeFrame(): List<FreezeFrameElement>? {
        val path = "files/ems_error_ff.json"
        val bytes = Res.readBytes(path)
        val jsonString = bytes.decodeToString()
        val container = Json.decodeFromString<FreezeFrameElementContainerJson>(jsonString)
        val resultFreezeFrameElements = container.list.map { json ->
            FreezeFrameElement().apply {
                val resKey = "ems_error_ff_${json.name.lowercase()}"
                name = json.name
                description = ResourceMapper.stringResource(resKey)
                mask = decodeHexToLong(json.mask)
                byteInBlock = decodeHexToInt(json.byteInBlock)
                factorA = json.factorA.toFloatOrNull() ?: 1f
                factorB = json.factorB.toFloatOrNull() ?: 0f
                u2 = json.u2?.toBooleanStrictOrNull() ?: false
            }
        }
        return try{
            resultFreezeFrameElements.map { json ->
                FreezeFrameElement().apply {
                    val resKey = "ems_error_ff_${json.name.lowercase()}"
                    name = json.name
                    description = ResourceMapper.stringResource(resKey)
                    byteInBlock = json.byteInBlock
                    mask = json.mask
                    factorA = json.factorA
                    factorB = json.factorB
                    unit = json.unit
                    u2 = json.u2
                }
            }
        }
        catch (ex : Exception)
        {
            Logger.e{"$tag : getFreezeFrameList, ${ex.toString()}"}
            return null
        }
    }

    suspend fun getZCSCodingList(zcsName: String): List<ZCSCodingElement>? {
        val path = "files/zcs_${zcsName.lowercase()}.json"
        val bytes = Res.readBytes(path)
        val jsonString = bytes.decodeToString()
        val container = Json.decodeFromString<ZcsCodingElementContainerJson>(jsonString)
        val result = container.elements.map { json ->
            ZCSCodingElementJson().apply {
                name = json.name
                byteInBlock = json.byteInBlock
                mask = json.mask
                codingValues = json.codingValues
                visible = json.visible
            }
        }
        return try {
            result.map { json ->
                ZCSCodingElement().apply {
                    name = json.name
                    description = ResourceMapper.stringResource(name)
                    byteInBlock = json.byteInBlock
                    mask = json.mask
                    codingValues = ArrayList()
                    json.codingValues.forEach { v ->
                        codingValues.add(Pair(v.first, ResourceMapper.stringResource(v.second)))
                    }
                    visible = json.visible
                }
            }.filter { x -> x.visible }
        } catch (ex: Exception) {
            Logger.e{"$tag : getFreezeFrameList, ${ex.toString()}"}
            return emptyList()
        }
    }

    private fun decodeHexToInt(value: String?): Int {
        if (value.isNullOrEmpty()) return 0
        return try {
            if (value.startsWith("0x")) {
                value.substring(2).toInt(16)
            } else {
                value.toInt()
            }
        } catch (e: Exception) {
            Logger.e{"decodeHexToInt: ${e.toString()}"}
            0
        }
    }
    private fun decodeHexToLong(value: String?): Long {
        if (value.isNullOrEmpty()) return 0
        return try {
            if (value.startsWith("0x")) {
                value.substring(2).toLong(16)
            } else {
                value.toLong()
            }
        } catch (e: Exception) {
            Logger.e{"decodeHexToLong: ${e.toString()}"}
            0
        }
    }
}
