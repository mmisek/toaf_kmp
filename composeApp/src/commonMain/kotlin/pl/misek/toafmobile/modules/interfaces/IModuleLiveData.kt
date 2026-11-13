package pl.misek.toafmobile.modules.interfaces

import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.livedata.LiveDataFormat
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.tools.JsonSupport
import java.nio.ByteBuffer

interface IModuleLiveData {
    suspend fun readLiveData(): Map<Int, Any>?
    suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean

    fun getLiveDataRawValue(data : Map<Int, Any>?, element : LiveDataElement) : Long? {
        if (data == null || element.liveDataFormat != LiveDataFormat.Standard) return null
        val bytes = data[element.block] as? ByteArray ?: return null
        var mask = element.mask
        val length = calculateLengthFromMask(mask)
        if (bytes.size < element.byteInBlock + length) return null

        var valueBytes = bytes.copyOfRange(element.byteInBlock, element.byteInBlock + length)
        if (element.swap)
            valueBytes = valueBytes.reversed().toByteArray()

        var value = if (element.u2) {
            val paddledBytes = ByteArray(8)
            valueBytes.copyInto(paddledBytes, destinationOffset = 8 - valueBytes.size)
            ByteBuffer.wrap(paddledBytes).getLong()
        } else
            convertToLong(valueBytes)
        value = value and mask

        while ((mask and 0x01L) != 0x01L) {
            mask = mask shr 0x01
            value = value shr 0x01
        }
        // Wartosci ze znakiem - szerokosc pola wg maski
        if (element.signFrom >= 0) {
            val bits = 64 - mask.countLeadingZeroBits()
            if (bits in 1..63 && value >= element.signFrom)
                value -= (1L shl bits)
        } else if (element.u2 || element.signOffset) {
            val bits = 64 - mask.countLeadingZeroBits()
            if (bits in 1..63 && ((value shr (bits - 1)) and 0x01L) == 0x01L) {
                value = if (element.signOffset)
                    ((1L shl (bits - 1)) - 1) - value
                else
                    value - (1L shl bits)
            }
        }
        return value
    }

    fun getLiveDataValue(data : Map<Int, Any>?, element : LiveDataElement) : Float {
        val rawValue = getLiveDataRawValue(data, element) ?: return Float.NaN
        if (element.invertA && rawValue == 0L) return 0.0f
        val scaled = if (element.invertA) 1 / rawValue.toFloat() else rawValue.toFloat()
        return element.factorA * scaled + element.factorB
    }

    suspend fun getModuleVoltage(moduleAddress: ModuleAddress, count : Int) : Float
    {
        val list = JsonSupport.getLiveDataElements(moduleAddress = moduleAddress)
        val elementName = getBatteryLiveDataName(moduleAddress = moduleAddress)
        if (elementName != null) {
            val liveDataElement = list?.firstOrNull { x -> x.name == elementName }
            if (liveDataElement != null) {
                if (startLiveDataRead(listOf(liveDataElement))) {
                    val values = mutableListOf<Float>()
                    repeat(count)
                    {
                        val data = readLiveData()
                        val value = getLiveDataValue(data, liveDataElement)
                        if (!value.isNaN()) values.add(value)
                    }
                    return values.average().toFloat()
                }
            }
        }
        return Float.NaN
    }
}

internal fun calculateLengthFromMask(value : Long) : Int {
    var count = 0
    var temp = value

    while (temp > 0) {
        count++
        temp /= 0x100
    }
    return count
}

internal fun convertToLong(byteArray : ByteArray) : Long {
    var value = 0L
    val size = byteArray.size
    for ((i, byte) in byteArray.withIndex()) {
        val shift = 8 * (size - 1 - i)
        value += (byte.toLong() and 0xFF) shl shift
    }
    return value
}

internal  fun getBatteryLiveDataName(moduleAddress: ModuleAddress) : String?
{
    return when (moduleAddress)
    {
        ModuleAddress.MODULE_BMBT -> "battery_voltage"
        ModuleAddress.MODULE_DDE -> "anmUBT"
        ModuleAddress.MODULE_DSC -> "ignition_voltage"
        ModuleAddress.MODULE_EHC -> "supply_voltage"
        ModuleAddress.MODULE_ELV -> "ALOG_U_KL30"
        ModuleAddress.MODULE_EMS -> "battery_voltage"
        ModuleAddress.MODULE_GS20 -> "battery_voltage"
        ModuleAddress.MODULE_GS8601 -> "battery_voltage"
        ModuleAddress.MODULE_HEVAC -> "power_supply"
        ModuleAddress.MODULE_IPK -> "battery_voltage"
        ModuleAddress.MODULE_LCM -> "battery_voltage"
        ModuleAddress.MODULE_LCM_L322 -> "battery_voltage"
        ModuleAddress.MODULE_LWS -> "terminal_87_voltage"
        ModuleAddress.MODULE_ME72 -> "UB"
        ModuleAddress.MODULE_MEMS -> "input_battery_voltage"
        ModuleAddress.MODULE_MFL -> "operating_voltage"
        ModuleAddress.MODULE_NAVMK3 -> "12v_value"
        ModuleAddress.MODULE_WEBASTO -> "battery_voltage"
        else -> null
    }
}
