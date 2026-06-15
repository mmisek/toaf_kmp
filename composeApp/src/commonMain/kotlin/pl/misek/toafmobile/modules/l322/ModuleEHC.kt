package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.delay
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
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.ProtocolDS2
import pl.misek.toafmobile.tools.JsonSupport

import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class ModuleEHC(protocolDS2: ProtocolDS2?) : ModuleDS2(protocolDS2, ModuleAddress.MODULE_EHC, listOf(CarType.L322)),
    IModuleLiveData,/* IModuleAdvanceControl,*/ IModuleControl, IModuleEepromProgramming
{
    private val tag = "ModuleEHC"
    private var controlFrontLeft: ControlElement? = null
    private var controlFrontRight: ControlElement? = null
    private var controlRearLeft: ControlElement? = null
    private var controlRearRight: ControlElement? = null
    private var controlValveDrain: ControlElement? = null
    private var controlCompressorSwitch: ControlElement? = null
    private val errorDetailsList = byteArrayOf(0x01, 0x02,0x03,0x04,0x07,0x08,0x0A, 0x0B,0x0C, 0x0D,0xFE.toByte())

    private suspend fun getControlElements()
    {
        if (controlFrontLeft != null
            || controlFrontRight != null
            || controlRearLeft != null
            || controlRearRight != null
            || controlValveDrain != null
            || controlCompressorSwitch != null)
        {
            return
        }

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
            try {
                if (message.isValid) {
                    result[block.toInt()] = message.answer.copyOfRange(3, message.answer.size)
                } else return null
            } catch (ex : Exception)
            {
                Logger.e {"$tag [readLiveData], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean = true

    override suspend fun getAllErrors(): List<ModuleError>? {
        val fakeAnswer = byteArrayOf(0xAC.toByte(), 0x13.toByte(), 0xA0.toByte(), 0x02.toByte(), 0x36.toByte(), 0x00.toByte(), 0x61.toByte(), 0x01.toByte(), 0x86.toByte(), 0x90.toByte(),
            0x70.toByte(), 0x59.toByte(), 0x00.toByte(), 0x00.toByte(), 0x58.toByte(), 0x00.toByte(), 0xFE.toByte(), 0x00.toByte(), 0xD2.toByte(),
            0xAC.toByte(), 0x05.toByte(), 0x04.toByte(), 0x00.toByte(), 0xAD.toByte(), 0xAC.toByte(), 0x07.toByte(), 0xA0.toByte(),
            0x02.toByte(), 0x24.toByte(), 0x26.toByte(), 0x0B.toByte())
        var result: MutableList<ModuleError>? = null
        var message = interfaceWrite(byteArrayOf(0x04, 0x00),250)
        try {
            //message.answer = fakeAnswer
            if (message.isValid)
            {
                var index = 1
                result = ArrayList()
                repeat (message.answer[3].toInt() and 0xFF) {
                    message = interfaceWrite(byteArrayOf(0x04, index.toByte()),250)
                    index++
                    if (message.isValid) {
                        val errorCode = String.format("%02X", message.answer[4])
                        val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                        for (b in errorDetailsList) {
                            if ((message.answer[6].toInt() and b.toInt()) == b.toInt()) {
                                ResourceMapper
                                    .stringResource(String.format("ehc_error_detail_%02X", b))
                                    ?.let { errorDescription.add(Pair(it, null)) }
                            }
                        }
                        errorDescription.add(ResourceHelper.getErrorDescriptionCount(message.answer[7].toInt() and 0xFF))
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_mileage,
                                10 * (256 * (message.answer[9].toInt() and 0xFF) + (message.answer[8].toInt() and 0xFF))
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_voltage,
                                (message.answer[10].toInt() and 0xFF) * 0.125
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_pressure,
                                (message.answer[11].toInt() and 0xFF) * 0.125
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_valve_front_right,
                                if ((message.answer[12].toInt() and 0x01) == 0x01) "1" else "0"
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_valve_front_left,
                                if ((message.answer[12].toInt() and 0x02) == 0x02) "1" else "0"
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_valve_rear_right,
                                if ((message.answer[12].toInt() and 0x04) == 0x04) "1" else "0"
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_valve_rear_left,
                                if ((message.answer[12].toInt() and 0x08) == 0x08) "1" else "0"
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_valve_reservoir,
                                if ((message.answer[12].toInt() and 0x10) == 0x10) "1" else "0"
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_valve_exhaust,
                                if ((message.answer[12].toInt() and 0x20) == 0x20) "1" else "0"
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_compressor_relay,
                                if ((message.answer[12].toInt() and 0x40) == 0x40) "1" else "0"
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_height_pressure_exhaust_valve,
                                if ((message.answer[13].toInt() and 0x01) == 0x01) "1" else "0"
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_crosslink_valve_rear,
                                if ((message.answer[13].toInt() and 0x04) == 0x04) "1" else "0"
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_crosslink_valve_left,
                                if ((message.answer[13].toInt() and 0x08) == 0x08) "1" else "0"
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_compressor_temperature,
                                (message.answer[14].toInt() and 0xFF) - 40
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_vehicle_speed,
                                message.answer[15].toInt() and 0xFF
                            )
                        )
                        errorDescription.add(
                            Pair(
                                Res.string.ehc_error_detail_average_height_of_car,
                                (message.answer[16].toInt() and 0xFF)
                            )
                        )
                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_EHC,
                                errorCode,
                                errorDescription
                            )
                        )
                    }
                }

            }
        } catch (ex : Exception)
        {
            Logger.e {"$tag [GetAllErrors], $ex, $message"}
        }
        return result
    }

    //override fun getAdvanceControlActivity(context: Context): Intent = Intent(context, ActivityCalibrationEHC::class.java)

    //override fun getAdvanceControlTitle(): String =  resources.getString(R.string.activity_control_ehc_calibration_tittle)

    override suspend fun control(controlElement: ControlElement): Boolean{
        val data: ByteArray
        getControlElements()
        var result = false
        when (controlElement.block) {
            0x02 -> {
                data = byteArrayOf(0x0C, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                data[controlElement.byteInBlock + 2] = controlElement.value.toByte()
                interfaceWrite(byteArrayOf(0x1C, 0x00, 0x20, 0x00),500)
                result = interfaceWrite(data,500).isValid;
                if (result)
                {
                    repeat(5)
                    {
                        interfaceWrite(byteArrayOf(0x9E.toByte()))
                        delay(500.milliseconds)
                    }
                }
            }
            0x1C -> {
                data = byteArrayOf(0x1C, controlElement.byteInBlock.toByte(), controlElement.value.toByte(), 0x00)
                result =  interfaceWrite(data,500).isValid
            }
        }

        return result
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

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        val message = interfaceWrite(
            byteArrayOf(
                0x06,
                0x00,
                ((address and 0xFF0000) shr 16).toByte(),
                ((address and 0x00FF00) shr 8).toByte(),
                ((address and 0x0000FF)).toByte(),
                size.toByte()
            ),
            250
        )
        return if (message.isValid) message.answer.copyOfRange(0x03, message.answer.size - 1) else null
    }

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val dataToWrite =
            byteArrayOf(
                0x07,
                0x00,
                ((address and 0xFF0000) shr 16).toByte(),
                ((address and 0x00FF00) shr 8).toByte(),
                ((address and 0x0000FF)).toByte(),
                0x01) + data
        return interfaceWrite(dataToWrite).isValid
    }

    override fun getEepromSize(): Int = 512

    override fun getEepromReadSize() : Int = 16

    override fun getEepromWriteSize(): Int = 1

    private fun calculateKeyNormal(seed : Int) : Int
    {
        val hi =  (((seed and 0xFF00) shr 8) xor 0xFA) or 0x80
        val low = (((seed and 0x00FF)) xor 0x23 ) or 0x80
        return (hi shl 8) + low
    }

    private fun calculateKeyExpert(seed : Int) : Int
    {
        val hi =  (((seed and 0xFF00) shr 8) xor 0xAD) or 0x08
        val low = (((seed and 0x00FF)) xor 0xDE ) or 0x08
        return (hi shl 8) + low
    }

    suspend fun securityAccess(level : Int) : Boolean
    {
        val message = interfaceWrite(byteArrayOf(0x93.toByte(),level.toByte()))
        if (!message.isValid)
            return false
        try {
            val seed = ((message.answer[3].toInt() and 0xFF) shl 8) or (message.answer[4].toInt() and 0xFF)
            val key = when (level) {
                0 -> calculateKeyNormal(seed)
                1 -> calculateKeyExpert(seed)
                else -> return false
            }
            return interfaceWrite(byteArrayOf(0x94.toByte(), 0x00, (key shr 8).toByte(), (key and 0xFF).toByte())).isValid

        } catch ( ex : Exception)
        {
            Logger.e {"$tag [securityAccess], $ex, $message"}
            return false
        }
    }

    override  suspend fun  beforeRead(){
        securityAccess(1)
    }
    override suspend fun beforeWrite() {
        securityAccess(1)
    }
}
