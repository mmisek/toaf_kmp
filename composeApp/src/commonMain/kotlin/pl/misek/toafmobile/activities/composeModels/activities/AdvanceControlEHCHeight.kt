package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import pl.misek.toafmobile.activities.composeModels.ToafButtons
import pl.misek.toafmobile.activities.composeModels.dialogs.DialogNotFullVersion
import pl.misek.toafmobile.activities.definitions.ToafColors
import pl.misek.toafmobile.activities.definitions.ToafStyles
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.modules.ModulesManager
import pl.misek.toafmobile.modules.containers.EHCHeightLiveData
import pl.misek.toafmobile.modules.enums.EHCControlWheel
import pl.misek.toafmobile.modules.enums.EHCControlWheelDirection
import pl.misek.toafmobile.modules.interfaces.AdvanceControlShowResult
import pl.misek.toafmobile.modules.interfaces.IModuleEHCHeightControl
import pl.misek.toafmobile.network.UserInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

private const val ehcTag = "AdvanceControlEHCHeight"
private const val EHC_HEIGHT_REFRESH_MS = 250
/** Zakres wskaznika wysokosci na rysunku auta (+/- mm) */
private const val EHC_HEIGHT_GAUGE_RANGE_MM = 100f

private val ehcActiveUpColor = Color(0xFF2E9BD6)
private val ehcActiveDownColor = Color(0xFFF4A261)

private sealed interface EhcHeightCommand {
    /** Brak kontroli - sterownik pracuje automatycznie */
    data object Stop : EhcHeightCommand
    /** Tryb reczny, zawory zamkniete */
    data object Hold : EhcHeightCommand
    data class Move(val wheel: EHCControlWheel, val direction: EHCControlWheelDirection) : EhcHeightCommand
}

private suspend fun <T> ehcCall(default: T, call: suspend () -> T): T =
    try {
        withContext(Dispatchers.IO) { call() }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Logger.e { "$ehcTag, $ex" }
        default
    }

@Composable
fun AdvanceControlEHCHeight(userInfo: UserInfo, onShowResult: AdvanceControlShowResult) {
    val modulesManager: ModulesManager = koinInject()
    val moduleEhc = modulesManager.getCurrentModules()
        .firstOrNull { it.getKoinName() == KoinNames.ModuleL322Ehc } as? IModuleEHCHeightControl
    var liveData by remember { mutableStateOf<EHCHeightLiveData?>(null) }
    var command by remember { mutableStateOf<EhcHeightCommand>(EhcHeightCommand.Stop) }
    var manualMode by remember { mutableStateOf(false) }
    // Obejrzenie reklamy odblokowuje sterowanie do wyjscia z ekranu
    var controlUnlocked by remember { mutableStateOf(false) }
    var pendingMove by remember { mutableStateOf<EhcHeightCommand.Move?>(null) }
    val manualModeFailed = stringResource(Res.string.advance_control_ehc_height_manual_mode_failed)
    val controlFailed = stringResource(Res.string.advance_control_ehc_height_control_failed)
    val stopSuccessful = stringResource(Res.string.advance_control_ehc_height_stop_successful)
    val stopFailed = stringResource(Res.string.advance_control_ehc_height_stop_failed)

    // Jedna petla obsluguje odczyt LiveData i komendy sterujace - magistrala DS2 nie moze byc uzywana rownolegle
    LaunchedEffect(moduleEhc) {
        if (moduleEhc == null)
            return@LaunchedEffect
        var sentCommand: EhcHeightCommand = EhcHeightCommand.Stop
        try {
            while (isActive) {
                val currentCommand = command
                when (currentCommand) {
                    is EhcHeightCommand.Move -> {
                        if (!manualMode) {
                            manualMode = ehcCall(false) { moduleEhc.startManualControl() }
                            if (!manualMode) {
                                command = EhcHeightCommand.Stop
                                onShowResult(false, null, manualModeFailed)
                            }
                        }
                        if (manualMode && !ehcCall(false) { moduleEhc.controlHeight(currentCommand.wheel, currentCommand.direction) }) {
                            command = EhcHeightCommand.Hold
                            onShowResult(false, null, controlFailed)
                        }
                    }
                    EhcHeightCommand.Hold -> {
                        if (manualMode && sentCommand is EhcHeightCommand.Move)
                            ehcCall(false) { moduleEhc.closeValves() }
                    }
                    EhcHeightCommand.Stop -> {
                        if (manualMode) {
                            val result = ehcCall(false) { moduleEhc.stopControl() }
                            manualMode = false
                            onShowResult(result, stopSuccessful, stopFailed)
                        }
                    }
                }
                sentCommand = currentCommand
                liveData = ehcCall(null) { moduleEhc.readHeightLiveData() }
                delay(EHC_HEIGHT_REFRESH_MS.milliseconds)
            }
        } finally {
            // Wyjscie z ekranu zawsze oddaje sterownikowi kontrole
            if (manualMode)
                withContext(NonCancellable) { ehcCall(false) { moduleEhc.stopControl() } }
        }
    }

    AdvanceControlEHCHeightContent(
        liveData = liveData,
        activeMove = command as? EhcHeightCommand.Move,
        manualMode = manualMode,
        enabled = moduleEhc != null,
        onWheelClick = { wheel, direction ->
            val move = EhcHeightCommand.Move(wheel, direction)
            when {
                command == move -> command = EhcHeightCommand.Hold
                userInfo.isFullVersion() || controlUnlocked -> command = move
                else -> pendingMove = move
            }
        },
        onStopClick = { command = EhcHeightCommand.Stop }
    )

    pendingMove?.let { move ->
        DialogNotFullVersion(
            onWatchAdResult = { rewarded ->
                if (rewarded) {
                    controlUnlocked = true
                    command = move
                }
                pendingMove = null
            },
            onCloseClick = { pendingMove = null }
        )
    }
}

@Composable
private fun AdvanceControlEHCHeightContent(
    liveData: EHCHeightLiveData?,
    activeMove: EhcHeightCommand.Move?,
    manualMode: Boolean,
    enabled: Boolean,
    onWheelClick: (EHCControlWheel, EHCControlWheelDirection) -> Unit,
    onStopClick: () -> Unit
) {
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
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            {
                Column(
                    modifier = Modifier.weight(0.3f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceAround
                )
                {
                    EhcWheelPanel(EHCControlWheel.FrontLeft, liveData, activeMove, enabled, onWheelClick)
                    EhcWheelPanel(EHCControlWheel.RearLeft, liveData, activeMove, enabled, onWheelClick)
                }
                EhcCarTopView(
                    liveData = liveData,
                    activeMove = activeMove,
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp)
                )
                Column(
                    modifier = Modifier.weight(0.3f).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceAround
                )
                {
                    EhcWheelPanel(EHCControlWheel.FrontRight, liveData, activeMove, enabled, onWheelClick)
                    EhcWheelPanel(EHCControlWheel.RearRight, liveData, activeMove, enabled, onWheelClick)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            )
            {
                EhcInfoTile(
                    name = Res.string.advance_control_ehc_height_pressure,
                    value = formatEhcValue(liveData?.pressure, "%.2f bar"),
                    modifier = Modifier.weight(1f)
                )
                EhcInfoTile(
                    name = Res.string.advance_control_ehc_height_temperature,
                    value = formatEhcValue(liveData?.compressorTemperature, "%.0f °C"),
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = stringResource(
                    if (manualMode) Res.string.advance_control_ehc_height_mode_manual
                    else Res.string.advance_control_ehc_height_mode_auto
                ),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = if (manualMode) ehcActiveDownColor else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (manualMode) FontWeight.Bold else FontWeight.Normal
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            ToafButtons.RedButton(
                buttonText = Res.string.advance_control_ehc_height_stop,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onStopClick() },
                enabled = enabled
            )
        }
    }
}

private fun formatEhcValue(value: Float?, format: String): String =
    if (value == null || value.isNaN()) "--" else String.format(Locale.getDefault(), format, value)

private fun EHCControlWheel.nameResource(): StringResource = when (this) {
    EHCControlWheel.FrontLeft -> Res.string.advance_control_ehc_height_front_left
    EHCControlWheel.FrontRight -> Res.string.advance_control_ehc_height_front_right
    EHCControlWheel.RearLeft -> Res.string.advance_control_ehc_height_rear_left
    EHCControlWheel.RearRight -> Res.string.advance_control_ehc_height_rear_right
}

@Composable
private fun EhcWheelPanel(
    wheel: EHCControlWheel,
    liveData: EHCHeightLiveData?,
    activeMove: EhcHeightCommand.Move?,
    enabled: Boolean,
    onWheelClick: (EHCControlWheel, EHCControlWheelDirection) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    )
    {
        Text(
            text = stringResource(wheel.nameResource()),
            textAlign = TextAlign.Center,
            style = TextStyle(color = ToafColors.toafTextColorGrey, fontSize = 12.sp)
        )
        EhcDirectionButton(wheel, EHCControlWheelDirection.Up, activeMove, enabled, onWheelClick)
        Text(
            text = formatEhcValue(liveData?.height(wheel), "%.1f mm"),
            textAlign = TextAlign.Center,
            style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(vertical = 2.dp)
        )
        EhcDirectionButton(wheel, EHCControlWheelDirection.Down, activeMove, enabled, onWheelClick)
    }
}

@Composable
private fun EhcDirectionButton(
    wheel: EHCControlWheel,
    direction: EHCControlWheelDirection,
    activeMove: EhcHeightCommand.Move?,
    enabled: Boolean,
    onWheelClick: (EHCControlWheel, EHCControlWheelDirection) -> Unit
) {
    val isActive = activeMove?.wheel == wheel && activeMove.direction == direction
    val activeColor = if (direction == EHCControlWheelDirection.Up) ehcActiveUpColor else ehcActiveDownColor
    FilledTonalButton(
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp),
        contentPadding = PaddingValues(0.dp),
        border = BorderStroke(ToafStyles.borderSize, if (isActive) Color.White else ToafColors.borderColor),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isActive) activeColor else ToafColors.greenButton,
            disabledContainerColor = ToafColors.disabledButton
        ),
        onClick = { onWheelClick(wheel, direction) },
        content = {
            Text(
                text = if (direction == EHCControlWheelDirection.Up) "▲" else "▼",
                fontSize = 18.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    )
}

@Composable
private fun EhcInfoTile(name: StringResource, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .border(1.dp, Color.LightGray, RoundedCornerShape(6.dp))
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    )
    {
        Text(
            text = stringResource(name),
            textAlign = TextAlign.Center,
            style = TextStyle(color = ToafColors.toafTextColorGrey, fontSize = 12.sp)
        )
        Text(
            text = value,
            textAlign = TextAlign.Center,
            style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        )
    }
}

/** Rzut z gory Range Rover L322 (przod u gory) ze wskaznikami wysokosci przy kazdym kole */
@Composable
private fun EhcCarTopView(liveData: EHCHeightLiveData?, activeMove: EhcHeightCommand.Move?, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val bodyHeight = min(size.height * 0.96f, size.width / 0.52f)
        val bodyWidth = bodyHeight * 0.4f
        val left = (size.width - bodyWidth) / 2f
        val top = (size.height - bodyHeight) / 2f
        fun x(ratio: Float) = left + bodyWidth * ratio
        fun y(ratio: Float) = top + bodyHeight * ratio

        val wheelWidth = bodyWidth * 0.17f
        val wheelHeight = bodyHeight * 0.15f
        val outline = Stroke(width = 1.5.dp.toPx())

        // Kola - pod nadwoziem, wystaja na boki
        EHCControlWheel.entries.forEach { wheel ->
            val isLeft = wheel == EHCControlWheel.FrontLeft || wheel == EHCControlWheel.RearLeft
            val isFront = wheel == EHCControlWheel.FrontLeft || wheel == EHCControlWheel.FrontRight
            val wheelLeft = if (isLeft) left - wheelWidth * 0.45f else left + bodyWidth - wheelWidth * 0.55f
            val wheelTop = y(if (isFront) 0.2f else 0.79f) - wheelHeight / 2f
            val color = when {
                activeMove?.wheel != wheel -> Color(0xFF202020)
                activeMove.direction == EHCControlWheelDirection.Up -> ehcActiveUpColor
                else -> ehcActiveDownColor
            }
            drawRoundRect(color, Offset(wheelLeft, wheelTop), Size(wheelWidth, wheelHeight), CornerRadius(wheelWidth * 0.3f))
            drawRoundRect(Color.LightGray, Offset(wheelLeft, wheelTop), Size(wheelWidth, wheelHeight), CornerRadius(wheelWidth * 0.3f), style = outline)
        }

        // Lusterka
        val mirrorSize = Size(bodyWidth * 0.1f, bodyHeight * 0.035f)
        drawRoundRect(Color(0xFF5B6770), Offset(left - mirrorSize.width * 0.8f, y(0.3f)), mirrorSize, CornerRadius(4f))
        drawRoundRect(Color(0xFF5B6770), Offset(left + bodyWidth - mirrorSize.width * 0.2f, y(0.3f)), mirrorSize, CornerRadius(4f))

        // Nadwozie
        drawRoundRect(Color(0xFF5B6770), Offset(left, top), Size(bodyWidth, bodyHeight), CornerRadius(bodyWidth * 0.16f))
        drawRoundRect(Color.White, Offset(left, top), Size(bodyWidth, bodyHeight), CornerRadius(bodyWidth * 0.16f), style = outline)

        // Grill i swiatla przednie
        drawRect(Color(0xFF2A2F33), Offset(x(0.28f), y(0.008f)), Size(bodyWidth * 0.44f, bodyHeight * 0.025f))
        drawRoundRect(Color(0xFFFFF3C4), Offset(x(0.07f), y(0.015f)), Size(bodyWidth * 0.18f, bodyHeight * 0.03f), CornerRadius(3f))
        drawRoundRect(Color(0xFFFFF3C4), Offset(x(0.75f), y(0.015f)), Size(bodyWidth * 0.18f, bodyHeight * 0.03f), CornerRadius(3f))
        // Linie maski
        drawLine(Color(0xFF8A969E), Offset(x(0.2f), y(0.06f)), Offset(x(0.2f), y(0.27f)), 1.dp.toPx())
        drawLine(Color(0xFF8A969E), Offset(x(0.8f), y(0.06f)), Offset(x(0.8f), y(0.27f)), 1.dp.toPx())

        val glass = Color(0xFF1E2A33)
        // Szyba przednia
        drawPath(Path().apply {
            moveTo(x(0.15f), y(0.28f))
            lineTo(x(0.85f), y(0.28f))
            lineTo(x(0.92f), y(0.37f))
            lineTo(x(0.08f), y(0.37f))
            close()
        }, glass)
        // Dach i szyberdach
        drawRect(Color(0xFF6E7B85), Offset(x(0.08f), y(0.37f)), Size(bodyWidth * 0.84f, bodyHeight * 0.43f))
        drawRoundRect(glass, Offset(x(0.27f), y(0.41f)), Size(bodyWidth * 0.46f, bodyHeight * 0.12f), CornerRadius(4f))
        // Szyba tylna
        drawPath(Path().apply {
            moveTo(x(0.08f), y(0.8f))
            lineTo(x(0.92f), y(0.8f))
            lineTo(x(0.87f), y(0.86f))
            lineTo(x(0.13f), y(0.86f))
            close()
        }, glass)
        // Swiatla tylne
        drawRoundRect(Color(0xFFD32F2F), Offset(x(0.04f), y(0.94f)), Size(bodyWidth * 0.17f, bodyHeight * 0.035f), CornerRadius(3f))
        drawRoundRect(Color(0xFFD32F2F), Offset(x(0.79f), y(0.94f)), Size(bodyWidth * 0.17f, bodyHeight * 0.035f), CornerRadius(3f))

        // Wskazniki wysokosci przy kolach
        EHCControlWheel.entries.forEach { wheel ->
            val isLeft = wheel == EHCControlWheel.FrontLeft || wheel == EHCControlWheel.RearLeft
            val isFront = wheel == EHCControlWheel.FrontLeft || wheel == EHCControlWheel.FrontRight
            drawHeightGauge(
                height = liveData?.height(wheel) ?: Float.NaN,
                centerX = x(if (isLeft) 0.24f else 0.76f),
                centerY = y(if (isFront) 0.2f else 0.79f),
                gaugeWidth = bodyWidth * 0.1f,
                gaugeHeight = bodyHeight * 0.14f
            )
        }
    }
}

private fun DrawScope.drawHeightGauge(height: Float, centerX: Float, centerY: Float, gaugeWidth: Float, gaugeHeight: Float) {
    val gaugeLeft = centerX - gaugeWidth / 2f
    val gaugeTop = centerY - gaugeHeight / 2f
    drawRect(Color(0xCC101418), Offset(gaugeLeft, gaugeTop), Size(gaugeWidth, gaugeHeight))
    if (!height.isNaN()) {
        val ratio = (height / EHC_HEIGHT_GAUGE_RANGE_MM).coerceIn(-1f, 1f)
        val barHeight = gaugeHeight / 2f * ratio
        val color = if (ratio >= 0f) ehcActiveUpColor else ehcActiveDownColor
        // Wartosc dodatnia rosnie w gore od linii zera
        drawRect(
            color,
            Offset(gaugeLeft, if (ratio >= 0f) centerY - barHeight else centerY),
            Size(gaugeWidth, abs(barHeight))
        )
    }
    drawLine(Color.White, Offset(gaugeLeft - 2f, centerY), Offset(gaugeLeft + gaugeWidth + 2f, centerY), 1.dp.toPx())
    drawRect(Color.LightGray, Offset(gaugeLeft, gaugeTop), Size(gaugeWidth, gaugeHeight), style = Stroke(1f))
}

@Preview(showBackground = true, backgroundColor = 0xff000000)
@Composable
fun AdvanceControlEHCHeightPreview()
{
    AdvanceControlEHCHeightContent(
        liveData = EHCHeightLiveData(
            frontLeft = 12.5f,
            frontRight = -8.0f,
            rearLeft = 45.25f,
            rearRight = -60f,
            pressure = 14.2f,
            compressorTemperature = 38f
        ),
        activeMove = EhcHeightCommand.Move(EHCControlWheel.FrontLeft, EHCControlWheelDirection.Up),
        manualMode = true,
        enabled = true,
        onWheelClick = { _, _ -> },
        onStopClick = {}
    )
}
