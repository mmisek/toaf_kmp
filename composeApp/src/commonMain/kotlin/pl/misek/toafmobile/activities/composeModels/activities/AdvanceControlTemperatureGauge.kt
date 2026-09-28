package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ActionTypeEnum
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.ToafProgressDialog
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogInput
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.enums.TemperatureGaugeEnum
import pl.misek.toafmobile.modules.interfaces.ITemperatureCoding
import pl.misek.toafmobile.network.UserInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*

const val TEMPERATURE_GAUGE_MIN = 0
const val TEMPERATURE_GAUGE_MAX = 150
const val TEMPERATURE_GAUGE_NO_VALUE = -1

private const val TEMPERATURE_GAUGE_IMAGE_RATIO = 354f / 389f

data class TemperatureGaugeState(
    val temperatureGauge: TemperatureGaugeEnum,
    val temperatureValue: Int,
    val positionXRatio: Float,
    val positionYRatio: Float
)

data class TemperatureGaugeValidationTexts(
    val notNumber : String,
    val outOfRange : String,
    val lessThanPrevious : String,
    val greaterThanNext : String,
    val pointNames : Map<TemperatureGaugeEnum, String>
)

private fun defaultGaugePoints(): List<TemperatureGaugeState> = listOf(
    TemperatureGaugeState(TemperatureGaugeEnum.StartOfBlue,   TEMPERATURE_GAUGE_NO_VALUE, 0.70f, 0.75f),
    TemperatureGaugeState(TemperatureGaugeEnum.EndOfBlue,     TEMPERATURE_GAUGE_NO_VALUE, 0.55f, 0.81f),
    TemperatureGaugeState(TemperatureGaugeEnum.OneQuarter,    TEMPERATURE_GAUGE_NO_VALUE, 0.40f, 0.73f),
    TemperatureGaugeState(TemperatureGaugeEnum.StartOfBuffer, TEMPERATURE_GAUGE_NO_VALUE, 0.10f, 0.50f),
    TemperatureGaugeState(TemperatureGaugeEnum.EndOfBuffer,   TEMPERATURE_GAUGE_NO_VALUE, 0.10f, 0.35f),
    TemperatureGaugeState(TemperatureGaugeEnum.ThreeQuarter,  TEMPERATURE_GAUGE_NO_VALUE, 0.40f, 0.23f),
    TemperatureGaugeState(TemperatureGaugeEnum.Overheat,      TEMPERATURE_GAUGE_NO_VALUE, 0.70f, 0.20f),
)

private fun List<TemperatureGaugeState>.withValues(values : Map<TemperatureGaugeEnum, Int>) =
    map { it.copy(temperatureValue = values[it.temperatureGauge] ?: TEMPERATURE_GAUGE_NO_VALUE) }

private fun List<TemperatureGaugeState>.hasAllValues() = all { it.temperatureValue > TEMPERATURE_GAUGE_NO_VALUE }

private fun TemperatureGaugeEnum.nameResource() : StringResource = when (this) {
    TemperatureGaugeEnum.StartOfBlue -> Res.string.advance_control_temperature_gauge_start_of_blue
    TemperatureGaugeEnum.EndOfBlue -> Res.string.advance_control_temperature_gauge_end_of_blue
    TemperatureGaugeEnum.OneQuarter -> Res.string.advance_control_temperature_gauge_one_quarter
    TemperatureGaugeEnum.StartOfBuffer -> Res.string.advance_control_temperature_gauge_start_of_buffer
    TemperatureGaugeEnum.EndOfBuffer -> Res.string.advance_control_temperature_gauge_end_of_buffer
    TemperatureGaugeEnum.ThreeQuarter -> Res.string.advance_control_temperature_gauge_three_quarter
    TemperatureGaugeEnum.Overheat -> Res.string.advance_control_temperature_gauge_overheat
}

fun validateTemperatureGaugeValue(points : List<TemperatureGaugeState>, index : Int, input : String, texts : TemperatureGaugeValidationTexts) : String?
{
    val value = input.trim().toIntOrNull() ?: return texts.notNumber
    if (value !in TEMPERATURE_GAUGE_MIN..TEMPERATURE_GAUGE_MAX)
        return String.format(texts.outOfRange, TEMPERATURE_GAUGE_MIN, TEMPERATURE_GAUGE_MAX)
    val previous = points.getOrNull(index - 1)
    if (previous != null && previous.temperatureValue > TEMPERATURE_GAUGE_NO_VALUE && value < previous.temperatureValue)
        return String.format(texts.lessThanPrevious, texts.pointNames[previous.temperatureGauge] ?: "", previous.temperatureValue)
    val next = points.getOrNull(index + 1)
    if (next != null && next.temperatureValue > TEMPERATURE_GAUGE_NO_VALUE && value > next.temperatureValue)
        return String.format(texts.greaterThanNext, texts.pointNames[next.temperatureGauge] ?: "", next.temperatureValue)
    return null
}

@Composable
fun AdvanceControlTemperatureGauge(userInfo: UserInfo, onShowResult : (result : Boolean, onSuccess : String?, onFail : String?) -> Unit) {
    val modulesManager : ModulesManager = koinInject()
    val moduleIpk = modulesManager.getCurrentModules().firstOrNull { it.getKoinName() == KoinNames.ModuleR40Ipk } as? ITemperatureCoding
    var gaugePoints by remember { mutableStateOf(defaultGaugePoints()) }
    var action by remember { mutableStateOf<suspend () -> Any?>({ null }) }
    var actionType by remember { mutableStateOf(ActionTypeEnum.None) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var dialogShowNotFullVersion by remember { mutableStateOf(false) }
    val readSuccessful = stringResource(Res.string.advance_control_temperature_gauge_read_successful)
    val readFailed = stringResource(Res.string.advance_control_temperature_gauge_read_failed)
    val writeSuccessful = stringResource(Res.string.advance_control_temperature_gauge_write_successful)
    val writeFailed = stringResource(Res.string.advance_control_temperature_gauge_write_failed)

    AdvanceControlTemperatureGaugeContent(
        gaugePoints = gaugePoints,
        onPointChanged = { index, value ->
            gaugePoints = gaugePoints.toMutableList().also {
                it[index] = it[index].copy(temperatureValue = value)
            }
        },
        onReadClick = {
            actionType = ActionTypeEnum.ReadTemperatureGauge
            action = { moduleIpk?.readTemperatureGauge() }
            showProgressDialog = true
        },
        onWriteClick = {
            actionType = ActionTypeEnum.WriteTemperatureGauge
            val valuesToWrite = gaugePoints.associate { it.temperatureGauge to it.temperatureValue }
            action = { moduleIpk?.writeTemperatureGauge(valuesToWrite) == true }
            if (userInfo.isFullVersion())
                showProgressDialog = true
            else
                dialogShowNotFullVersion = true
        }
    )

    if (showProgressDialog) {
        ToafProgressDialog(
            action = { action() },
            onSuccess = { actionResult ->
                when (actionType) {
                    ActionTypeEnum.ReadTemperatureGauge -> {
                        @Suppress("UNCHECKED_CAST")
                        val values = actionResult as? Map<TemperatureGaugeEnum, Int>
                        if (values != null)
                            gaugePoints = gaugePoints.withValues(values)
                        onShowResult(values != null, readSuccessful, readFailed)
                    }
                    ActionTypeEnum.WriteTemperatureGauge -> {
                        onShowResult(actionResult == true, writeSuccessful, writeFailed)
                    }
                    else -> {}
                }
            },
            onError = {
                onShowResult(
                    false,
                    null,
                    if (actionType == ActionTypeEnum.WriteTemperatureGauge) writeFailed else readFailed
                )
            },
            onDismissRequest = {
                actionType = ActionTypeEnum.None
                showProgressDialog = false })
    }
    if (dialogShowNotFullVersion) {
        DialogNotFullVersion(
            onWatchAdResult = { showProgressDialog = true },
            onCloseClick = { dialogShowNotFullVersion = false }
        )
    }
}

@Composable
private fun AdvanceControlTemperatureGaugeContent(
    gaugePoints : List<TemperatureGaugeState>,
    onPointChanged : (index : Int, value : Int) -> Unit,
    onReadClick : () -> Unit,
    onWriteClick : () -> Unit)
{
    var editedIndex by remember { mutableStateOf<Int?>(null) }
    val validationTexts = TemperatureGaugeValidationTexts(
        notNumber = stringResource(Res.string.advance_control_temperature_gauge_validation_not_number),
        outOfRange = stringResource(Res.string.advance_control_temperature_gauge_validation_out_of_range),
        lessThanPrevious = stringResource(Res.string.advance_control_temperature_gauge_validation_less_than_previous),
        greaterThanNext = stringResource(Res.string.advance_control_temperature_gauge_validation_greater_than_next),
        pointNames = TemperatureGaugeEnum.entries.associateWith { stringResource(it.nameResource()) }
    )
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
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Text(
                text = stringResource(Res.string.advance_control_temperature_gauge_title),
                textAlign = TextAlign.Center,
                style = TextStyle(color = Color.White, fontSize = 16.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(all = 2.dp)
            )
            Text(
                text = stringResource(
                    if (gaugePoints.hasAllValues()) Res.string.advance_control_temperature_gauge_click_to_edit
                    else Res.string.advance_control_temperature_gauge_no_data
                ),
                textAlign = TextAlign.Center,
                style = TextStyle(color = Color.White, fontSize = 14.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )

            Spacer(Modifier.height(8.dp))

            TemperatureGaugeImage(
                gaugePoints = gaugePoints,
                onPointClick = { index -> editedIndex = index },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            )
            {
                ToafButtons.GreenButton(
                    buttonText = Res.string.advance_control_temperature_gauge_read,
                    modifier = Modifier.weight(1f),
                    onClick = { onReadClick() }
                )
                ToafButtons.RedButton(
                    buttonText = Res.string.advance_control_temperature_gauge_write,
                    modifier = Modifier.weight(1f),
                    onClick = { onWriteClick() },
                    enabled = gaugePoints.hasAllValues()
                )
            }
        }
    }
    editedIndex?.let { index ->
        DialogInput(
            title = Res.string.advance_control_temperature_gauge_edit_title,
            name = gaugePoints[index].temperatureGauge.nameResource(),
            value = gaugePoints[index].temperatureValue.toString(),
            okButtonText = Res.string.advance_control_temperature_gauge_edit_save,
            validate = { input -> validateTemperatureGaugeValue(gaugePoints, index, input, validationTexts) },
            onCancelClick = { editedIndex = null },
            onOkClick = { input ->
                onPointChanged(index, input.trim().toInt())
                editedIndex = null
            }
        )
    }
}

@Composable
private fun TemperatureGaugeImage(
    gaugePoints: List<TemperatureGaugeState>,
    onPointClick: (index : Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val gaugeModifier =
            if (maxWidth / maxHeight > TEMPERATURE_GAUGE_IMAGE_RATIO)
                Modifier.fillMaxHeight().aspectRatio(TEMPERATURE_GAUGE_IMAGE_RATIO)
            else
                Modifier.fillMaxWidth().aspectRatio(TEMPERATURE_GAUGE_IMAGE_RATIO)
        BoxWithConstraints(modifier = gaugeModifier) {
            Image(
                painter = painterResource(Res.drawable.temperature_gauge),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
            val fontSize = with(LocalDensity.current) { (maxHeight * 0.06f).toSp() }
            gaugePoints.forEachIndexed { index, point ->
                val hasValue = point.temperatureValue > TEMPERATURE_GAUGE_NO_VALUE
                Text(
                    text = if (hasValue) point.temperatureValue.toString() else "--",
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (hasValue) TextDecoration.Underline else TextDecoration.None,
                    modifier = Modifier
                        .offset(x = maxWidth * point.positionXRatio, y = maxHeight * point.positionYRatio)
                        .background(ToafColors.toafBackground, RoundedCornerShape(4.dp))
                        .then(if (hasValue) Modifier.clickable { onPointClick(index) } else Modifier)
                        .padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xff000000)
@Composable
fun AdvanceControlTemperatureGaugePreview()
{
    val sampleValues = listOf(20, 21, 31, 44, 55, 66, 77)
    var points by remember {
        mutableStateOf(defaultGaugePoints().mapIndexed { index, point -> point.copy(temperatureValue = sampleValues[index]) })
    }
    AdvanceControlTemperatureGaugeContent(
        gaugePoints = points,
        onPointChanged = { index, value ->
            points = points.toMutableList().also { it[index] = it[index].copy(temperatureValue = value) }
        },
        onReadClick = {},
        onWriteClick = {}
    )
}
