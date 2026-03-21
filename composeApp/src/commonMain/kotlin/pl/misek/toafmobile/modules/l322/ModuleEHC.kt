package pl.misek.toafmobile.modules.l322

import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.containers.EHCAdjustmentData
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.EHCControlWheel
import pl.misek.toafmobile.modules.enums.EHCControlWheelDirection
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import pl.misek.toafmobile.tools.JsonSupport

import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModuleEHC(protocolDS2: ProtocolDS2?) : ModuleDS2(protocolDS2, ModuleAddress.MODULE_EHC, listOf(CarType.L322)), IModuleLiveData,/* IModuleAdvanceControl,*/ IModuleControl
{
    private var controlFrontLeft: ControlElement? = null
    private var controlFrontRight: ControlElement? = null
    private var controlRearLeft: ControlElement? = null
    private var controlRearRight: ControlElement? = null
    private var controlValveDrain: ControlElement? = null
    private var controlCompressorSwitch: ControlElement? = null

    private suspend fun getControlElements()
    {
        val controlElements = JsonSupport.getControlElements(getModuleName())
        if (controlElements != null) {
            controlFrontLeft = controlElements.stream()
                .filter { x: ControlElement -> x.name == "valve_front_left" }.findFirst().orElse(
                    ControlElement()
                )
            controlFrontRight = controlElements.stream()
                .filter { x: ControlElement -> x.name == "valve_front_right" }.findFirst().orElse(
                    ControlElement()
                )
            controlRearLeft =
                controlElements.stream().filter { x: ControlElement -> x.name == "valve_rear_left" }
                    .findFirst().orElse(
                        ControlElement()
                    )
            controlRearRight = controlElements.stream()
                .filter { x: ControlElement -> x.name == "valve_rear_right" }.findFirst().orElse(
                    ControlElement()
                )
            controlValveDrain =
                controlElements.stream().filter { x: ControlElement -> x.name == "valve_drain" }
                    .findFirst().orElse(
                        ControlElement()
                    )
            controlCompressorSwitch = controlElements.stream()
                .filter { x: ControlElement -> x.name == "compressor_switch" }.findFirst().orElse(
                    ControlElement()
                )
        }
    }
    override fun getKoinName() = KoinNames.ModuleL322Ehc
    override fun getImageResource() =  Res.drawable.module_ehc
    override fun getModuleDescription() = Res.string.module_description_ehc

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val blocks = byteArrayOf(0x00)
        for (block in blocks) {
            val message =  interfaceWrite(byteArrayOf(0x0B, block))
            if (message.isValid) {
                result[block.toInt()] = message.answer.copyOfRange(3, message.answer.size)
            } else return null
        }
        return result
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean = true

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        var message = interfaceWrite(byteArrayOf(0x04, 0x00))
        if (message.isValid) {
            var index = 1
            while (index < (message.answer[3].toInt() and 0xFF) + 1) {
                message = interfaceWrite(byteArrayOf(0x04, index.toByte()))
                if (message.isValid && (message.answer[3].toInt() and 0xFF) > 0) {
                    val errorCode = String.format("%02X", message.answer[4])
                    val errorDescription: MutableList<Pair<StringResource,Any?>> = ArrayList()
                    val detailsList = byteArrayOf(0x01,0x02,0x03,0x04,0x07,0x08,0x0A,0x0B,0x0C,0x0D,0xFE.toByte())
                    for (b in detailsList) {
                        if ((message.answer[6].toInt() and b.toInt()) == b.toInt()) {
                            ResourceMapper
                                .stringResource(String.format("ehc_error_detail_%02X",b))
                                ?.let { errorDescription.add(Pair(it,null)) }
                        }
                    }
                    errorDescription.add(ResourceHelper.getErrorDescriptionCount(message.answer[7].toInt() and 0xFF))
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_mileage,
                            10 * (256 * (message.answer[9].toInt() and 0xFF) + (message.answer[8].toInt() and 0xFF))
                        )
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_voltage,
                            (message.answer[10].toInt() and 0xFF) * 0.125)
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_pressure,
                            (message.answer[11].toInt() and 0xFF) * 0.125
                        )
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_valve_front_right,
                            if ((message.answer[12].toInt() and 0x01) == 0x01) "1" else "0"
                        )
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_valve_front_left,
                            if ((message.answer[12].toInt() and 0x02) == 0x02) "1" else "0"
                        )
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_valve_rear_right,
                            if ((message.answer[12].toInt() and 0x04) == 0x04) "1" else "0"
                        )
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_valve_rear_left,
                            if ((message.answer[12].toInt() and 0x08) == 0x08) "1" else "0"
                        )
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_valve_reservoir,
                            if ((message.answer[12].toInt() and 0x10) == 0x10) "1" else "0"
                        )
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_valve_exhaust,
                            if ((message.answer[12].toInt() and 0x20) == 0x20) "1" else "0"
                        )
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_compressor_relay,
                            if ((message.answer[12].toInt() and 0x40) == 0x40) "1" else "0"
                        )
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_height_pressure_exhaust_valve,
                            if ((message.answer[13].toInt() and 0x01) == 0x01) "1" else "0"
                        )
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_crosslink_valve_rear,
                            if ((message.answer[13].toInt() and 0x04) == 0x04) "1" else "0"
                        )
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_crosslink_valve_left,
                            if ((message.answer[13].toInt() and 0x08) == 0x08) "1" else "0"
                        )
                    )
                    errorDescription.add(
                            Pair(Res.string.ehc_error_detail_compressor_temperature,
                            (message.answer[14].toInt() and 0xFF) - 40
                    ))
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_vehicle_speed,
                        message.answer[15].toInt() and 0xFF)
                    )
                    errorDescription.add(
                        Pair(Res.string.ehc_error_detail_average_height_of_car,
                            (message.answer[16].toInt() and 0xFF))
                    )
                    if (result == null) result = ArrayList()
                    result.add(ModuleError(ModuleAddress.MODULE_EHC, errorCode, errorDescription))
                } else {
                    return result
                }
                index++
            }
        }
        return result
    }

    //override fun getAdvanceControlActivity(context: Context): Intent = Intent(context, ActivityCalibrationEHC::class.java)

    //override fun getAdvanceControlTitle(): String =  resources.getString(R.string.activity_control_ehc_calibration_tittle)

    override suspend fun control(controlElement: ControlElement): Boolean {
        val data: ByteArray
        getControlElements()
        when (controlElement.block) {
            0x00, 0x01 -> data = byteArrayOf(0x1C, 0x00, controlElement.value.toByte(), 0x00)
            0x02 -> {
                data = byteArrayOf(0x0C, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                data[controlElement.byteInBlock + 2] = controlElement.value.toByte()
            }

            else -> return false
        }
        return interfaceWrite(data).isValid
    }

    suspend fun readAdjustmentData(): EHCAdjustmentData {
        val message = interfaceWrite(byteArrayOf(0x40))
        message.answer = byteArrayOf(
            0x0a,
            0x03,
            0xab.toByte(),
            0x02,
            0x49,
            0x01,
            0xda.toByte(),
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00,
            0x00
        )
        return EHCAdjustmentData(message.answer)
    }

    suspend fun writeAdjustmentData(adjustmentData: EHCAdjustmentData): Boolean {
        val data = ByteBuffer
            .allocate(1 + 2 * 8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(0x41.toByte())
            .putShort(adjustmentData.offsetFrontLeft)
            .putShort(adjustmentData.offsetFrontRight)
            .putShort(adjustmentData.offsetRearLeft)
            .putShort(adjustmentData.offsetRearRight)

            .putShort(adjustmentData.offsetFrontLeftBelt)
            .putShort(adjustmentData.offsetFrontRightBelt)
            .putShort(adjustmentData.offsetRearLeftBelt)
            .putShort(adjustmentData.offsetRearRightBelt)
        return interfaceWrite(data.array()).isValid
    }

    suspend fun controlHeight(wheel: EHCControlWheel, direction: EHCControlWheelDirection): Boolean {
        val selectedDirection =
            if (direction == EHCControlWheelDirection.Up) controlCompressorSwitch else controlValveDrain

        val selectedWheel = when (wheel) {
            EHCControlWheel.FrontLeft -> controlFrontLeft
            EHCControlWheel.FrontRight -> controlFrontRight
            EHCControlWheel.RearLeft -> controlRearLeft
            EHCControlWheel.RearRight -> controlRearRight
        }

        val data = byteArrayOf(0x0C, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        data[selectedWheel!!.byteInBlock + 2] = selectedWheel.value.toByte()
        data[selectedDirection!!.byteInBlock + 2] =
            (data[selectedDirection.byteInBlock + 2] + selectedDirection.value.toByte()).toByte()

        //if (Control(controlStartManualControl)) {
        return interfaceWrite(data).isValid
//}

        //return false;
    }
}
