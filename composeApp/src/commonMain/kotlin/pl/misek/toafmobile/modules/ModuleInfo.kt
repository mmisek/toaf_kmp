package pl.misek.toafmobile.modules

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

private const val tag = "ModuleInfo"
class ModuleInfo
{
    var id1: String = ""
    var id2: String = ""
    var hw: String = ""
    var ci: String = ""
    var di: String = ""
    var bi: String = ""
    var sw: String = ""
    var date: String = ""
    var serial: String = ""
    var producer: String? = ""
    var additionalInfo: MutableList<Pair<StringResource, Any?>> = ArrayList()

    @Composable
    fun getFormatedInfo(): String {
        val sb = StringBuilder()
        val infoFields = listOfNotNull(
            Pair(id1, Res.string.alert_module_info_id1),
            Pair(id2, Res.string.alert_module_info_id2),
            Pair(hw, Res.string.alert_module_info_hw),
            Pair(sw, Res.string.alert_module_info_sw),
            Pair(ci, Res.string.alert_module_info_ci),
            Pair(bi, Res.string.alert_module_info_bi),
            Pair(di, Res.string.alert_module_info_di),
            Pair(date, Res.string.alert_module_info_date),
            Pair(serial, Res.string.alert_module_info_serial_number),
            Pair(producer, Res.string.module_part_producer)
        ).filter { it.first?.isNotEmpty() ?: false }
        for ((value, resourceId) in infoFields) {
            sb.append(stringResource(resourceId))
                .append(": ")
                .append(value)
                .append("\n")
        }
        for (p in additionalInfo) {
            val param = when (val v = p.second)
            {
                null -> ""
                is String -> v
                is Int -> v.toString()
                is Float -> "%.2f".format(v)
                else -> {
                    println("$tag : no format for data type ${v::class.simpleName}")
                    ""
                }
            }
            sb.append(stringResource(p.first))
                .append(": ")
                .append(param)
                .append("\n")
        }

        return sb.toString()
    }

    fun toJson(moduleName: String?, vin: String?, odometer: String?): JsonObject {
        val jsonObject = buildJsonObject {
            put("name", moduleName)
            put("id1", id1)
            put("id2", id2)
            put("hw", hw)
            put("sw", sw)
            put("ci", ci)
            put("di", di)
            put("bi", bi)
            put("data", date)
            put("serial", serial)
            put("producer", producer)
            put("vin", vin ?: "")
            put("odometer", odometer ?: "")
        }
        return jsonObject
    }
}
