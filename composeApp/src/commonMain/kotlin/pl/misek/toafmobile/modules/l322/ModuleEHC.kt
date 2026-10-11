package pl.misek.toafmobile.modules.l322

import co.touchlab.kermit.Logger
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.activities.composeModels.activities.AdvanceControlEHCCalibration
import pl.misek.toafmobile.activities.composeModels.activities.AdvanceControlEHCHeight
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.ModuleDS2
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.containers.EHCAdjustmentData
import pl.misek.toafmobile.modules.containers.EHCCalibrationData
import pl.misek.toafmobile.modules.containers.EHCHeightLiveData
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.EHCAxle
import pl.misek.toafmobile.modules.enums.EHCControlWheel
import pl.misek.toafmobile.modules.enums.EHCControlWheelDirection
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.modules.interfaces.AdvanceControlElement
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceControl
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEHCCalibration
import pl.misek.toafmobile.modules.interfaces.IModuleEHCHeightControl
import pl.misek.toafmobile.modules.interfaces.IModuleEepromProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.protocols.OBDMessage
import pl.misek.toafmobile.protocols.ProtocolDS2
import pl.misek.toafmobile.tools.JsonSupport

import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.Duration.Companion.milliseconds

class ModuleEHC(protocolDS2: ProtocolDS2?) : ModuleDS2(protocolDS2, ModuleAddress.MODULE_EHC, listOf(CarType.L322)),
    IModuleLiveData, IModuleAdvanceControl, IModuleControl, IModuleEepromProgramming, IModuleEHCHeightControl, IModuleEHCCalibration
{
    private val tag = "ModuleEHC"
    private var controlFrontLeft: ControlElement? = null
    private var controlFrontRight: ControlElement? = null
    private var controlRearLeft: ControlElement? = null
    private var controlRearRight: ControlElement? = null
    private var controlValveDrain: ControlElement? = null
    private var controlCompressorSwitch: ControlElement? = null
    private var heightLiveDataElements: List<LiveDataElement>? = null
    private var savedModes: ByteArray? = null

    private fun errorTypeMatrix(location: Int): IntArray = when (location) {
        in 0x01..0x0A, in 0x14..0x18, 0x23, 0x24 -> intArrayOf(0x01, 0x02, 0x03, 0xFF, 0x07)
        in 0x1E..0x21 -> intArrayOf(0x0A, 0x0B, 0x0C, 0xFF, 0x07)
        0x22 -> intArrayOf(0x0A, 0xFF, 0xFF, 0xFF, 0x07)
        in 0x31..0x37 -> intArrayOf(0xFE, 0xFF, 0xFF, 0xFF, 0x07)
        0x25, in 0x64..0x82 -> intArrayOf(0xFF, 0xFF, 0xFF, 0x04, 0x07)
        else -> intArrayOf(0xFF, 0xFF, 0xFF, 0xFF, 0xFF)
    }

    private suspend fun writeWithBusyRetry(data: ByteArray, timeout: Long = 250): OBDMessage {
        var message = interfaceWrite(data, timeout)
        var retries = 0
        while (message.answerCode == ANSWER_BUSY && retries < MAX_BUSY_RETRIES) {
            delay(BUSY_RETRY_DELAY_MS.milliseconds)
            message = interfaceWrite(data, timeout)
            retries++
        }
        return message
    }

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

    override fun getAdvanceControlElements() = listOf(
        AdvanceControlElement(Res.string.advance_control_ehc_height_title) { userInfo, onShowResult ->
            AdvanceControlEHCHeight(userInfo = userInfo, onShowResult = onShowResult)
        },
        AdvanceControlElement(Res.string.advance_control_ehc_calibration_title) { userInfo, onShowResult ->
            AdvanceControlEHCCalibration(userInfo = userInfo, onShowResult = onShowResult)
        }
    )

    override suspend fun readLiveData(): Map<Int, ByteArray>? = readLiveDataBlocks(LIVE_DATA_BLOCKS)

    private suspend fun readLiveDataBlocks(blocks: ByteArray): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        for (block in blocks) {
            val message =  interfaceWrite(byteArrayOf(0x0B, block))
            try {
                if (message.isValid) {
                    result[block.toInt()] = message.answer.copyOfRange(3, message.answer.size - 1)
                } else return null
            } catch (ex: CancellationException) {
                throw ex
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
        var message = writeWithBusyRetry(byteArrayOf(0x04, 0x00))
        try {
            //message.answer = fakeAnswer
            if (message.isValid)
            {
                var index = 1
                result = ArrayList()
                repeat (message.answer[3].toInt() and 0xFF) {
                    message = writeWithBusyRetry(byteArrayOf(0x04, index.toByte()))
                    index++
                    if (message.isValid) {
                        val location = message.answer[4].toInt() and 0xFF
                        val errorCode = String.format("%02X", location)
                        val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                        val errorTypes = errorTypeMatrix(location)
                        for (i in ERROR_TYPE_BITS.indices) {
                            if ((message.answer[6].toInt() and ERROR_TYPE_BITS[i]) != 0 && errorTypes[i] != 0xFF) {
                                ResourceMapper
                                    .stringResource(String.format("ehc_error_detail_%02X", errorTypes[i]))
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
                                message.answer[16].toInt()
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
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex : Exception)
        {
            Logger.e {"$tag [GetAllErrors], $ex, $message"}
        }
        return result
    }

    override suspend fun control(controlElement: ControlElement): Boolean{
        getControlElements()
        var result = false
        when (controlElement.block) {
            0x02 -> {
                val modes = readModes() ?: return false
                if (!writeModes(withHandControl(modes)))
                    return false
                try {
                    val data = byteArrayOf(0x0C, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                    data[controlElement.byteInBlock + 2] = controlElement.value.toByte()
                    if (controlElement.value != 0) {
                        for (second in 0 until CONTROL_TIME_S) {
                            result = writeWithBusyRetry(data, 500).isValid
                            if (!result)
                                break
                            delay(1000.milliseconds)
                        }
                    }
                } finally {
                    withContext(NonCancellable) {
                        val closed = closeValves()
                        result = writeModes(modes) && closed && (result || controlElement.value == 0)
                    }
                }
            }
            0x1C -> {
                val data = if (controlElement.byteInBlock == 0x00 && controlElement.value != 0) {
                    val modes = readModes() ?: return false
                    byteArrayOf(0x1C, 0x00, ((modes[0].toInt() and 0xFF) or controlElement.value).toByte(), modes[1])
                } else
                    byteArrayOf(0x1C, controlElement.byteInBlock.toByte(), controlElement.value.toByte(), 0x00)
                result = writeWithBusyRetry(data, 500).isValid
            }
        }

        return result
    }

    private suspend fun readModes(): ByteArray? {
        val message = writeWithBusyRetry(byteArrayOf(0x1B, 0x00))
        return if (message.isValid && message.answer.size >= 6) message.answer.copyOfRange(3, 5) else null
    }

    private fun withHandControl(modes: ByteArray) =
        byteArrayOf(((modes[0].toInt() and 0xFF) or MODE_HAND_CONTROL).toByte(), modes[1])

    private suspend fun writeModes(modes: ByteArray): Boolean =
        writeWithBusyRetry(byteArrayOf(0x1C, 0x00, modes[0], modes[1]), 500).isValid

    // ---------------------------------------------------------------------
    // Kalibracja zawieszenia (wg SGBD EHC_2 rev. 1.09)
    //
    // Ramki (payload bez adresu 0xAC, dlugosci i sumy kontrolnej):
    //  0x40                    ABGLEICHWERT_LESEN         - odczyt danych abgleichu (16 B)
    //  0x41 + 16 B             ABGLEICHWERT_PROGRAMMIEREN - zapis danych abgleichu
    //  0x0B 0x00               IO_STATUS_LESEN_SENSOREN   - wysokosci czujnikow [1/8 mm, signed]
    //  0x08 0x00 0x1E 0x0C     CODE_LESEN @0x1E, 12 B     - kierunek i rozdzielczosc czujnikow
    //  0x9E                    DIAGNOSEMODE_ERHALTEN      - podtrzymanie trybu diagnostycznego
    // ---------------------------------------------------------------------

    override suspend fun readAdjustmentData(): EHCAdjustmentData? {
        val message = interfaceWrite(byteArrayOf(CMD_ADJUSTMENT_READ))
        try {
            if (message.isValid && message.answer.size >= 3 + EHCAdjustmentData.DATA_SIZE)
                return EHCAdjustmentData(message.answer.copyOfRange(3, 3 + EHCAdjustmentData.DATA_SIZE))
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [readAdjustmentData], $ex, $message" }
        }
        return null
    }

    suspend fun writeAdjustmentData(adjustmentData: EHCAdjustmentData): Boolean {
        val result = interfaceWrite(byteArrayOf(CMD_ADJUSTMENT_WRITE) + adjustmentData.toByteArray(), 500).isValid
        delay(2000.milliseconds)
        return result
    }

    override suspend fun readSensorHeights(): IntArray? {
        val raw = readSensorHeightsRaw() ?: return null
        val buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(4) { buffer.getShort(it * 2) / 8 }
    }

    private suspend fun readSensorHeightsRaw(): ByteArray? {
        val message = interfaceWrite(byteArrayOf(0x0B, 0x00))
        return if (message.isValid && message.answer.size >= 11) message.answer.copyOfRange(3, 11) else null
    }

    private suspend fun readSensorCoding(axle: EHCAxle): Triple<Boolean, Boolean, Pair<Int, Int>>? {
        val message = interfaceWrite(byteArrayOf(0x08, 0x00, 0x1E, 0x0C))
        if (!message.isValid || message.answer.size < 15)
            return null
        val a = message.answer
        fun u16(index: Int) = (a[index].toInt() and 0xFF) or ((a[index + 1].toInt() and 0xFF) shl 8)
        val flags = a[3].toInt() and 0xFF
        return when (axle) {
            EHCAxle.Front -> Triple(flags and 0x08 != 0, flags and 0x04 != 0, Pair(u16(7), u16(9)))
            EHCAxle.Rear -> Triple(flags and 0x02 != 0, flags and 0x01 != 0, Pair(u16(11), u16(13)))
        }
    }

    override suspend fun calculateCalibration(axle: EHCAxle, measuredLeft: Int, measuredRight: Int): EHCCalibrationData? {
        try {
            val heights = readSensorHeights() ?: return null
            val adjustment = readAdjustmentData() ?: return null
            val (invertLeft, invertRight, resolution) = readSensorCoding(axle) ?: return null

            val ecuLeft = if (axle == EHCAxle.Front) heights[0] else heights[2]
            val ecuRight = if (axle == EHCAxle.Front) heights[1] else heights[3]
            val oldLeft = (if (axle == EHCAxle.Front) adjustment.offsetFrontLeft else adjustment.offsetRearLeft).toInt() and 0xFFFF
            val oldRight = (if (axle == EHCAxle.Front) adjustment.offsetFrontRight else adjustment.offsetRearRight).toInt() and 0xFFFF

            fun correction(deviation: Int, res: Int) = (deviation.toLong() * res * 1024 / 8 / 5000).toInt()
            val correctionLeft = correction(ecuLeft - measuredLeft, resolution.first)
            val correctionRight = correction(ecuRight - measuredRight, resolution.second)
            val newLeft = if (invertLeft) oldLeft - correctionLeft else oldLeft + correctionLeft
            val newRight = if (invertRight) oldRight - correctionRight else oldRight + correctionRight

            val newAdjustment = EHCAdjustmentData(adjustment.toByteArray().copyOfRange(0, 8) + ByteArray(8))
            if (axle == EHCAxle.Front) {
                newAdjustment.offsetFrontLeft = newLeft.toShort()
                newAdjustment.offsetFrontRight = newRight.toShort()
            } else {
                newAdjustment.offsetRearLeft = newLeft.toShort()
                newAdjustment.offsetRearRight = newRight.toShort()
            }

            return EHCCalibrationData(
                axle = axle,
                ecuHeightLeft = ecuLeft,
                ecuHeightRight = ecuRight,
                measuredHeightLeft = measuredLeft,
                measuredHeightRight = measuredRight,
                resolutionLeft = resolution.first,
                resolutionRight = resolution.second,
                offsetLeftOld = oldLeft,
                offsetRightOld = oldRight,
                offsetLeftNew = newLeft,
                offsetRightNew = newRight,
                adjustmentData = newAdjustment
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e { "$tag [calculateCalibration], $ex" }
            return null
        }
    }

    override suspend fun writeCalibration(calibration: EHCCalibrationData): Boolean =
        calibration.isValid && writeAdjustmentData(calibration.adjustmentData)

    override suspend fun storeLeakHeights(): Boolean {
        val adjustment = readAdjustmentData() ?: return false
        val heights = readSensorHeightsRaw() ?: return false
        val data = adjustment.toByteArray().copyOfRange(0, 8) + heights
        return writeAdjustmentData(EHCAdjustmentData(data))
    }

    suspend fun writeOffsetCounts(frontLeft: Int, frontRight: Int, rearLeft: Int, rearRight: Int): Boolean {
        val adjustment = readAdjustmentData() ?: return false
        interfaceWrite(byteArrayOf(0x9E.toByte()))
        adjustment.offsetFrontLeft = frontLeft.toShort()
        adjustment.offsetFrontRight = frontRight.toShort()
        adjustment.offsetRearLeft = rearLeft.toShort()
        adjustment.offsetRearRight = rearRight.toShort()
        return writeAdjustmentData(EHCAdjustmentData(adjustment.toByteArray().copyOfRange(0, 8) + ByteArray(8)))
    }

    override suspend fun startManualControl(): Boolean {
        val modes = readModes() ?: return false
        if (savedModes == null)
            savedModes = modes
        return writeModes(withHandControl(modes))
    }

    override suspend fun controlHeight(wheel: EHCControlWheel, direction: EHCControlWheelDirection): Boolean {
        getControlElements()
        val selectedDirection =
            (if (direction == EHCControlWheelDirection.Up) controlCompressorSwitch else controlValveDrain)
                ?: return false

        val selectedWheel = when (wheel) {
            EHCControlWheel.FrontLeft -> controlFrontLeft
            EHCControlWheel.FrontRight -> controlFrontRight
            EHCControlWheel.RearLeft -> controlRearLeft
            EHCControlWheel.RearRight -> controlRearRight
        } ?: return false

        val data = byteArrayOf(0x0C, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        data[selectedWheel.byteInBlock + 2] = selectedWheel.value.toByte()
        data[selectedDirection.byteInBlock + 2] =
            (data[selectedDirection.byteInBlock + 2] + selectedDirection.value.toByte()).toByte()

        return interfaceWrite(data).isValid
    }

    override suspend fun closeValves(): Boolean =
        interfaceWrite(byteArrayOf(0x0C, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)).isValid

    override suspend fun stopControl(): Boolean {
        val valvesClosed = closeValves()
        val modes = savedModes
            ?: readModes()?.let { byteArrayOf((it[0].toInt() and MODE_HAND_CONTROL.inv()).toByte(), it[1]) }
            ?: return false
        val modesRestored = writeModes(modes)
        if (modesRestored)
            savedModes = null
        return valvesClosed && modesRestored
    }

    override suspend fun readHeightLiveData(): EHCHeightLiveData? {
        val elements = heightLiveDataElements
            ?: JsonSupport.getLiveDataElements(getModuleName())?.also { heightLiveDataElements = it }
            ?: return null
        val liveData = readLiveDataBlocks(byteArrayOf(0x00)) ?: return null
        fun value(name: String): Float =
            elements.firstOrNull { it.name == name }?.let { decodeLiveDataValue(it, liveData) } ?: Float.NaN
        return EHCHeightLiveData(
            frontLeft = value("height_sensor_front_left"),
            frontRight = value("height_sensor_front_right"),
            rearLeft = value("height_sensor_rear_left"),
            rearRight = value("height_sensor_rear_right"),
            pressure = value("pressure"),
            compressorTemperature = value("compressor_temperature")
        )
    }

    private fun decodeLiveDataValue(element: LiveDataElement, liveData: Map<Int, ByteArray>): Float {
        val block = liveData[element.block] ?: return Float.NaN
        val length = if (element.mask > 0xFF) 2 else 1
        if (element.byteInBlock + length > block.size)
            return Float.NaN
        var bytes = block.copyOfRange(element.byteInBlock, element.byteInBlock + length)
        if (element.swap)
            bytes = bytes.reversedArray()
        val buffer = ByteBuffer.wrap(bytes)
        var rawValue = if (length == 2) {
            if (element.u2) buffer.short.toInt() else buffer.short.toInt() and 0xFFFF
        } else {
            if (element.u2) buffer.get().toInt() else buffer.get().toInt() and 0xFF
        }
        if (element.signFrom in 0..rawValue)
            rawValue -= 1 shl (8 * length)
        return rawValue * element.factorA + element.factorB
    }

    override suspend fun readEeprom(address: Int, size: Int): ByteArray? {
        val message = writeWithBusyRetry(
            byteArrayOf(
                0x06,
                0x00,
                ((address and 0x0000FF)).toByte(),
                ((address and 0x00FF00) shr 8).toByte(),
                ((address and 0xFF0000) shr 16).toByte(),
                size.toByte()
            )
        )
        if (!message.isValid)
            return null
        val data = message.answer.copyOfRange(0x03, message.answer.size - 1)
        if (data.size != size) {
            Logger.e { "$tag [readEeprom], size ${data.size} != $size, $message" }
            return null
        }
        return data
    }

    override suspend fun writeEeprom(address: Int, data: ByteArray): Boolean {
        val dataToWrite =
            byteArrayOf(
                0x07,
                0x00,
                ((address and 0x0000FF)).toByte(),
                ((address and 0x00FF00) shr 8).toByte(),
                ((address and 0xFF0000) shr 16).toByte(),
                0x01) + data
        return writeWithBusyRetry(dataToWrite, 500).isValid
    }

    override val eepromWriteAvailable: Boolean = false

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
        if (level != SECURITY_LEVEL_NORMAL && level != SECURITY_LEVEL_EXPERT)
            return false
        val message = interfaceWrite(byteArrayOf(0x93.toByte(), level.toByte()))
        if (!message.isValid || message.answer.size < 5)
            return false
        try {
            val seed = ((message.answer[3].toInt() and 0xFF) shl 8) or (message.answer[4].toInt() and 0xFF)
            val key = if (level == SECURITY_LEVEL_NORMAL) calculateKeyNormal(seed) else calculateKeyExpert(seed)
            return interfaceWrite(byteArrayOf(0x94.toByte(), (key shr 8).toByte(), (key and 0xFF).toByte())).isValid
        } catch (ex: CancellationException) {
            throw ex
        } catch ( ex : Exception)
        {
            Logger.e {"$tag [securityAccess], $ex, $message"}
            return false
        }
    }

    private companion object {
        const val CMD_ADJUSTMENT_READ: Byte = 0x40
        const val CMD_ADJUSTMENT_WRITE: Byte = 0x41
        const val SECURITY_LEVEL_NORMAL = 1
        const val SECURITY_LEVEL_EXPERT = 2
        val LIVE_DATA_BLOCKS = byteArrayOf(0x00, 0x01, 0x02)
        val ERROR_TYPE_BITS = intArrayOf(0x01, 0x02, 0x04, 0x08, 0x40)
        const val ANSWER_BUSY: Byte = 0xA1.toByte()
        const val MAX_BUSY_RETRIES = 50
        const val BUSY_RETRY_DELAY_MS = 100L
        const val MODE_HAND_CONTROL = 0x20
        const val CONTROL_TIME_S = 5
    }

    override  suspend fun  beforeRead(){
        securityAccess(SECURITY_LEVEL_EXPERT)
    }
    override suspend fun beforeWrite() {
        securityAccess(SECURITY_LEVEL_EXPERT)
    }
}
