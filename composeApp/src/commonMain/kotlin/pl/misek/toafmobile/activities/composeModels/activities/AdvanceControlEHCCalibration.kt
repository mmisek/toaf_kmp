package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafEditText
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.containers.EHCAdjustmentData
import pl.misek.toafmobile.modules.containers.EHCCalibrationData
import pl.misek.toafmobile.modules.enums.EHCAxle
import pl.misek.toafmobile.modules.interfaces.AdvanceControlShowResult
import pl.misek.toafmobile.modules.interfaces.IModuleEHCCalibration
import pl.misek.toafmobile.network.UserInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import kotlin.time.Duration.Companion.milliseconds

private const val ehcCalibrationTag = "AdvanceControlEHCCalibration"
private const val EHC_CALIBRATION_REFRESH_MS = 500

private val ehcCalibrationWarningColor = Color(0xFFF4A261)

private sealed interface EhcCalibrationAction {
    data class Calculate(val axle: EHCAxle, val measuredLeft: Int, val measuredRight: Int) : EhcCalibrationAction
    data class Write(val calibration: EHCCalibrationData) : EhcCalibrationAction
    data object StoreLeakHeights : EhcCalibrationAction
}

private suspend fun <T> ehcCalibrationCall(default: T, call: suspend () -> T): T =
    try {
        withContext(Dispatchers.IO) { call() }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Logger.e { "$ehcCalibrationTag, $ex" }
        default
    }

@Composable
fun AdvanceControlEHCCalibration(userInfo: UserInfo, onShowResult: AdvanceControlShowResult) {
    val modulesManager: ModulesManager = koinInject()
    val moduleEhc = modulesManager.getCurrentModules()
        .firstOrNull { it.getKoinName() == KoinNames.ModuleL322Ehc } as? IModuleEHCCalibration
    var axle by remember { mutableStateOf(EHCAxle.Front) }
    var measuredLeft by remember { mutableStateOf("0") }
    var measuredRight by remember { mutableStateOf("0") }
    var heights by remember { mutableStateOf<IntArray?>(null) }
    var adjustment by remember { mutableStateOf<EHCAdjustmentData?>(null) }
    var calibration by remember { mutableStateOf<EHCCalibrationData?>(null) }
    var action by remember { mutableStateOf<EhcCalibrationAction?>(null) }
    // Zapis do sterownika wymaga pelnej wersji - bez odblokowania reklama
    var pendingPaidAction by remember { mutableStateOf<EhcCalibrationAction?>(null) }
    val calculateFailed = stringResource(Res.string.advance_control_ehc_calibration_calculate_failed)
    val writeSuccessful = stringResource(Res.string.advance_control_ehc_calibration_write_successful)
    val writeFailed = stringResource(Res.string.advance_control_ehc_calibration_write_failed)
    val storeSuccessful = stringResource(Res.string.advance_control_ehc_calibration_store_successful)
    val storeFailed = stringResource(Res.string.advance_control_ehc_calibration_store_failed)

    // Jedna petla obsluguje odczyt wysokosci i akcje - magistrala DS2 nie moze byc uzywana rownolegle
    LaunchedEffect(moduleEhc) {
        if (moduleEhc == null)
            return@LaunchedEffect
        adjustment = ehcCalibrationCall(null) { moduleEhc.readAdjustmentData() }
        while (isActive) {
            when (val currentAction = action) {
                is EhcCalibrationAction.Calculate -> {
                    calibration = ehcCalibrationCall(null) {
                        moduleEhc.calculateCalibration(currentAction.axle, currentAction.measuredLeft, currentAction.measuredRight)
                    }
                    if (calibration == null)
                        onShowResult(false, null, calculateFailed)
                }
                is EhcCalibrationAction.Write -> {
                    val result = ehcCalibrationCall(false) { moduleEhc.writeCalibration(currentAction.calibration) }
                    onShowResult(result, writeSuccessful, writeFailed)
                    if (result)
                        calibration = null
                    adjustment = ehcCalibrationCall(null) { moduleEhc.readAdjustmentData() }
                }
                EhcCalibrationAction.StoreLeakHeights -> {
                    val result = ehcCalibrationCall(false) { moduleEhc.storeLeakHeights() }
                    onShowResult(result, storeSuccessful, storeFailed)
                    adjustment = ehcCalibrationCall(null) { moduleEhc.readAdjustmentData() }
                }
                null -> {}
            }
            action = null
            heights = ehcCalibrationCall(null) { moduleEhc.readSensorHeights() }
            delay(EHC_CALIBRATION_REFRESH_MS.milliseconds)
        }
    }

    fun requestPaidAction(paidAction: EhcCalibrationAction) {
        if (userInfo.isFullVersion())
            action = paidAction
        else
            pendingPaidAction = paidAction
    }

    AdvanceControlEHCCalibrationContent(
        axle = axle,
        heights = heights,
        adjustment = adjustment,
        measuredLeft = measuredLeft,
        measuredRight = measuredRight,
        calibration = calibration,
        busy = action != null,
        enabled = moduleEhc != null,
        onAxleClick = {
            if (axle != it) {
                axle = it
                calibration = null
            }
        },
        onMeasuredLeftChanged = {
            measuredLeft = it
            calibration = null
        },
        onMeasuredRightChanged = {
            measuredRight = it
            calibration = null
        },
        onCalculateClick = {
            val left = measuredLeft.trim().toIntOrNull()
            val right = measuredRight.trim().toIntOrNull()
            if (left != null && right != null)
                action = EhcCalibrationAction.Calculate(axle, left, right)
        },
        onWriteClick = { calibration?.let { requestPaidAction(EhcCalibrationAction.Write(it)) } },
        onStoreLeakHeightsClick = { requestPaidAction(EhcCalibrationAction.StoreLeakHeights) }
    )

    if (pendingPaidAction != null) {
        DialogNotFullVersion(
            onWatchAdResult = { pendingPaidAction = null },
            onCloseClick = { pendingPaidAction = null },
            allowWatchAd = false
        )
    }
}

@Composable
private fun AdvanceControlEHCCalibrationContent(
    axle: EHCAxle,
    heights: IntArray?,
    adjustment: EHCAdjustmentData?,
    measuredLeft: String,
    measuredRight: String,
    calibration: EHCCalibrationData?,
    busy: Boolean,
    enabled: Boolean,
    onAxleClick: (EHCAxle) -> Unit,
    onMeasuredLeftChanged: (String) -> Unit,
    onMeasuredRightChanged: (String) -> Unit,
    onCalculateClick: () -> Unit,
    onWriteClick: () -> Unit,
    onStoreLeakHeightsClick: () -> Unit
) {
    val canUse = enabled && !busy
    val measuredLeftValid = measuredLeft.trim().toIntOrNull() != null
    val measuredRightValid = measuredRight.trim().toIntOrNull() != null
    val isFront = axle == EHCAxle.Front
    val offsetLeft = adjustment?.let { (if (isFront) it.offsetFrontLeft else it.offsetRearLeft).toInt() and 0xFFFF }
    val offsetRight = adjustment?.let { (if (isFront) it.offsetFrontRight else it.offsetRearRight).toInt() and 0xFFFF }
    val leakReferenceStored = adjustment?.let {
        it.offsetFrontLeftBelt.toInt() != 0 || it.offsetFrontRightBelt.toInt() != 0 ||
                it.offsetRearLeftBelt.toInt() != 0 || it.offsetRearRightBelt.toInt() != 0
    }

    Surface(
        color = ToafColors.toafBackground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color.White),
        modifier = Modifier
            .padding(all = 3.dp)
            .fillMaxSize()
    )
    {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        )
        {
            EhcCalibrationHint(Res.string.advance_control_ehc_calibration_info)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            )
            {
                EHCAxle.entries.forEach { entry ->
                    val text = if (entry == EHCAxle.Front) Res.string.advance_control_ehc_calibration_axle_front
                    else Res.string.advance_control_ehc_calibration_axle_rear
                    // Wybrana os - zielony, pozostala - szary (disabled), ale klikalna
                    ToafButtons.ToafButton(
                        buttonText = text,
                        color = if (entry == axle) ToafColors.greenButton else ToafColors.disabledButton,
                        onClick = { onAxleClick(entry) },
                        modifier = Modifier.weight(1f),
                        enabled = canUse
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                    .padding(4.dp)
            )
            {
                EhcCalibrationRow(null, stringResource(Res.string.advance_control_ehc_calibration_left), stringResource(Res.string.advance_control_ehc_calibration_right), header = true)
                EhcCalibrationRow(
                    Res.string.advance_control_ehc_calibration_ecu_height,
                    formatEhcCalibrationValue(heights?.get(if (isFront) 0 else 2)),
                    formatEhcCalibrationValue(heights?.get(if (isFront) 1 else 3))
                )
                EhcCalibrationRow(
                    Res.string.advance_control_ehc_calibration_offset_old,
                    formatEhcCalibrationValue(offsetLeft),
                    formatEhcCalibrationValue(offsetRight)
                )
            }

            leakReferenceStored?.let { stored ->
                Text(
                    text = stringResource(
                        if (stored) Res.string.advance_control_ehc_calibration_leak_set
                        else Res.string.advance_control_ehc_calibration_leak_not_set
                    ),
                    textAlign = TextAlign.Center,
                    style = TextStyle(color = if (stored) Color.White else ehcCalibrationWarningColor, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            )
            {
                ToafEditText(
                    modifier = Modifier.weight(1f),
                    name = Res.string.advance_control_ehc_calibration_measured_left,
                    value = measuredLeft,
                    readOnly = !canUse,
                    isError = !measuredLeftValid,
                    onValueChanged = onMeasuredLeftChanged
                )
                ToafEditText(
                    modifier = Modifier.weight(1f),
                    name = Res.string.advance_control_ehc_calibration_measured_right,
                    value = measuredRight,
                    readOnly = !canUse,
                    isError = !measuredRightValid,
                    onValueChanged = onMeasuredRightChanged
                )
            }

            ToafButtons.GreenButton(
                buttonText = Res.string.advance_control_ehc_calibration_calculate,
                modifier = Modifier.fillMaxWidth(),
                onClick = onCalculateClick,
                enabled = canUse && measuredLeftValid && measuredRightValid
            )

            calibration?.let { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
                        .padding(4.dp)
                )
                {
                    EhcCalibrationRow(null, stringResource(Res.string.advance_control_ehc_calibration_left), stringResource(Res.string.advance_control_ehc_calibration_right), header = true)
                    EhcCalibrationRow(Res.string.advance_control_ehc_calibration_ecu_height, result.ecuHeightLeft.toString(), result.ecuHeightRight.toString())
                    EhcCalibrationRow(Res.string.advance_control_ehc_calibration_measured, result.measuredHeightLeft.toString(), result.measuredHeightRight.toString())
                    EhcCalibrationRow(Res.string.advance_control_ehc_calibration_deviation, result.deviationLeft.toString(), result.deviationRight.toString())
                    EhcCalibrationRow(Res.string.advance_control_ehc_calibration_offset_old, result.offsetLeftOld.toString(), result.offsetRightOld.toString())
                    EhcCalibrationRow(Res.string.advance_control_ehc_calibration_offset_new, result.offsetLeftNew.toString(), result.offsetRightNew.toString(), highlight = true)
                }
                if (!result.isValid)
                    EhcCalibrationHint(Res.string.advance_control_ehc_calibration_out_of_range, ehcCalibrationWarningColor)

                ToafButtons.RedButton(
                    buttonText = Res.string.advance_control_ehc_calibration_write,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onWriteClick,
                    enabled = canUse && result.isValid
                )
            }

            EhcCalibrationHint(Res.string.advance_control_ehc_calibration_store_leak_info)
            ToafButtons.GreenButton(
                buttonText = Res.string.advance_control_ehc_calibration_store_leak,
                modifier = Modifier.fillMaxWidth(),
                onClick = onStoreLeakHeightsClick,
                enabled = canUse
            )

            if (busy)
                EhcCalibrationHint(Res.string.advance_control_ehc_calibration_busy, ehcCalibrationWarningColor)
        }
    }
}

private fun formatEhcCalibrationValue(value: Int?): String = value?.toString() ?: "--"

@Composable
private fun EhcCalibrationHint(text: StringResource, color: Color = ToafColors.toafTextColorGrey) {
    Text(
        text = stringResource(text),
        textAlign = TextAlign.Center,
        style = TextStyle(color = color, fontSize = 13.sp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EhcCalibrationRow(
    name: StringResource?,
    left: String,
    right: String,
    header: Boolean = false,
    highlight: Boolean = false
) {
    val valueStyle = TextStyle(
        color = if (header) ToafColors.toafTextColorGrey else Color.White,
        fontSize = if (header) 12.sp else 15.sp,
        fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    )
    {
        Text(
            text = name?.let { stringResource(it) } ?: "",
            style = TextStyle(color = ToafColors.toafTextColorGrey, fontSize = 12.sp),
            modifier = Modifier.weight(0.5f)
        )
        Text(text = left, textAlign = TextAlign.Center, style = valueStyle, modifier = Modifier.weight(0.25f))
        Text(text = right, textAlign = TextAlign.Center, style = valueStyle, modifier = Modifier.weight(0.25f))
    }
}

@Preview(showBackground = true, backgroundColor = 0xff000000)
@Composable
fun AdvanceControlEHCCalibrationPreview()
{
    // Wartosci z logu INPA (kalibracja osi przedniej, zmierzone 2 mm)
    val adjustment = EHCAdjustmentData(
        byteArrayOf(0x0F, 0x03, 0xA1.toByte(), 0x02, 0x49, 0x01, 0xDA.toByte(), 0x00, 0, 0, 0, 0, 0, 0, 0, 0)
    )
    AdvanceControlEHCCalibrationContent(
        axle = EHCAxle.Front,
        heights = intArrayOf(4, -2, 0, 0),
        adjustment = adjustment,
        measuredLeft = "2",
        measuredRight = "2",
        calibration = EHCCalibrationData(
            axle = EHCAxle.Front,
            ecuHeightLeft = 4,
            ecuHeightRight = -2,
            measuredHeightLeft = 2,
            measuredHeightRight = 2,
            resolutionLeft = 106,
            resolutionRight = 106,
            offsetLeftOld = 783,
            offsetRightOld = 673,
            offsetLeftNew = 778,
            offsetRightNew = 683,
            adjustmentData = adjustment
        ),
        busy = false,
        enabled = true,
        onAxleClick = {},
        onMeasuredLeftChanged = {},
        onMeasuredRightChanged = {},
        onCalculateClick = {},
        onWriteClick = {},
        onStoreLeakHeightsClick = {}
    )
}
