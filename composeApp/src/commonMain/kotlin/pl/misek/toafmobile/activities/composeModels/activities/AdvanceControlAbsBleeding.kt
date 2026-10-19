package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import pl.misek.toafmobile.modules.enums.BrakeBleedingWheel
import pl.misek.toafmobile.modules.interfaces.AdvanceControlShowResult
import pl.misek.toafmobile.modules.interfaces.IModuleBrakeBleeding
import pl.misek.toafmobile.network.UserInfo
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import kotlin.math.min

private const val absBleedingTag = "AdvanceControlAbsBleeding"

private val absBleedingActiveColor = Color(0xFFF4A261)

private sealed interface AbsBleedingTask {
    val wheels: List<BrakeBleedingWheel>
    data class Wheel(val wheel: BrakeBleedingWheel) : AbsBleedingTask {
        override val wheels get() = listOf(wheel)
    }
    data class Modulator(override val wheels: List<BrakeBleedingWheel>) : AbsBleedingTask
}

private suspend fun <T> absBleedingCall(default: T, call: suspend () -> T): T =
    try {
        withContext(Dispatchers.IO) { call() }
    } catch (ex: CancellationException) {
        throw ex
    } catch (ex: Exception) {
        Logger.e { "$absBleedingTag, $ex" }
        default
    }

@Composable
fun AdvanceControlAbsBleeding(userInfo: UserInfo, onShowResult: AdvanceControlShowResult) {
    val modulesManager: ModulesManager = koinInject()
    val moduleAbs = modulesManager.getCurrentModules()
        .firstOrNull { it.getKoinName() == KoinNames.ModuleR40Abs } as? IModuleBrakeBleeding
    var task by remember { mutableStateOf<AbsBleedingTask?>(null) }
    var activeWheel by remember { mutableStateOf<BrakeBleedingWheel?>(null) }
    var cycle by remember { mutableIntStateOf(0) }
    var finishedCycles by remember { mutableIntStateOf(0) }
    // Obejrzenie reklamy odblokowuje odpowietrzanie do wyjscia z ekranu
    var controlUnlocked by remember { mutableStateOf(false) }
    var pendingTask by remember { mutableStateOf<AbsBleedingTask?>(null) }
    val bleedingSuccessful = stringResource(Res.string.advance_control_abs_bleeding_successful)
    val bleedingFailed = stringResource(Res.string.advance_control_abs_bleeding_failed)
    val bleedingStopped = stringResource(Res.string.advance_control_abs_bleeding_stopped)
    val cycles = moduleAbs?.brakeBleedingCycles ?: 0

    // Zmiana zadania (STOP / wyjscie z ekranu) przerywa petle - pompa zawsze wylaczana w finally
    LaunchedEffect(task) {
        val currentTask = task ?: return@LaunchedEffect
        if (moduleAbs == null)
            return@LaunchedEffect
        var pumpRunning = false
        var result = true
        finishedCycles = 0
        try {
            for (wheel in currentTask.wheels) {
                activeWheel = wheel
                cycle = 0
                pumpRunning = absBleedingCall(false) { moduleAbs.startBrakeBleeding() }
                result = pumpRunning
                for (i in 1..cycles) {
                    if (!result)
                        break
                    cycle = i
                    result = absBleedingCall(false) { moduleAbs.bleedWheelCycle(wheel) }
                    finishedCycles++
                }
                val stopped = absBleedingCall(false) { moduleAbs.stopBrakeBleeding() }
                pumpRunning = pumpRunning && !stopped
                result = result && stopped
                if (!result)
                    break
            }
            onShowResult(result, bleedingSuccessful, bleedingFailed)
        } catch (ex: CancellationException) {
            // STOP uzytkownika - przy wyjsciu z ekranu task pozostaje ustawiony
            if (task == null)
                onShowResult(true, bleedingStopped, null)
            throw ex
        } finally {
            if (pumpRunning)
                withContext(NonCancellable) { absBleedingCall(false) { moduleAbs.stopBrakeBleeding() } }
            activeWheel = null
            cycle = 0
            if (task == currentTask)
                task = null
        }
    }

    val startTask: (AbsBleedingTask) -> Unit = { newTask ->
        if (userInfo.isFullVersion() || controlUnlocked)
            task = newTask
        else
            pendingTask = newTask
    }

    AdvanceControlAbsBleedingContent(
        activeWheel = activeWheel,
        cycle = cycle,
        cycles = cycles,
        progress = task?.let { if (cycles > 0) finishedCycles.toFloat() / (cycles * it.wheels.size) else 0f },
        running = task != null,
        enabled = moduleAbs != null,
        onWheelClick = { wheel -> startTask(AbsBleedingTask.Wheel(wheel)) },
        onModulatorClick = { moduleAbs?.let { startTask(AbsBleedingTask.Modulator(it.modulatorBleedingOrder)) } },
        onStopClick = { task = null }
    )

    pendingTask?.let { newTask ->
        DialogNotFullVersion(
            onWatchAdResult = { rewarded ->
                if (rewarded) {
                    controlUnlocked = true
                    task = newTask
                }
                pendingTask = null
            },
            onCloseClick = { pendingTask = null }
        )
    }
}

@Composable
private fun AdvanceControlAbsBleedingContent(
    activeWheel: BrakeBleedingWheel?,
    cycle: Int,
    cycles: Int,
    progress: Float?,
    running: Boolean,
    enabled: Boolean,
    onWheelClick: (BrakeBleedingWheel) -> Unit,
    onModulatorClick: () -> Unit,
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
            Text(
                text = stringResource(Res.string.advance_control_abs_bleeding_instruction),
                textAlign = TextAlign.Center,
                style = TextStyle(color = ToafColors.toafTextColorGrey, fontSize = 12.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

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
                    AbsBleedingWheelButton(BrakeBleedingWheel.FrontLeft, activeWheel, running, enabled, onWheelClick)
                    AbsBleedingWheelButton(BrakeBleedingWheel.RearLeft, activeWheel, running, enabled, onWheelClick)
                }
                AbsBleedingCarTopView(
                    activeWheel = activeWheel,
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
                    AbsBleedingWheelButton(BrakeBleedingWheel.FrontRight, activeWheel, running, enabled, onWheelClick)
                    AbsBleedingWheelButton(BrakeBleedingWheel.RearRight, activeWheel, running, enabled, onWheelClick)
                }
            }

            ToafButtons.GreenButton(
                buttonText = Res.string.advance_control_abs_bleeding_modulator,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                onClick = { onModulatorClick() },
                enabled = enabled && !running
            )

            Text(
                text = if (activeWheel == null) stringResource(Res.string.advance_control_abs_bleeding_ready)
                else stringResource(Res.string.advance_control_abs_bleeding_progress, stringResource(activeWheel.nameResource()), cycle, cycles),
                textAlign = TextAlign.Center,
                style = TextStyle(
                    color = if (running) absBleedingActiveColor else Color.White,
                    fontSize = 14.sp,
                    fontWeight = if (running) FontWeight.Bold else FontWeight.Normal
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )

            LinearProgressIndicator(
                progress = { progress ?: 0f },
                color = absBleedingActiveColor,
                trackColor = ToafColors.disabledButton,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            ToafButtons.RedButton(
                buttonText = Res.string.advance_control_abs_bleeding_stop,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onStopClick() },
                enabled = running
            )
        }
    }
}

private fun BrakeBleedingWheel.nameResource(): StringResource = when (this) {
    BrakeBleedingWheel.FrontLeft -> Res.string.advance_control_abs_bleeding_front_left
    BrakeBleedingWheel.FrontRight -> Res.string.advance_control_abs_bleeding_front_right
    BrakeBleedingWheel.RearLeft -> Res.string.advance_control_abs_bleeding_rear_left
    BrakeBleedingWheel.RearRight -> Res.string.advance_control_abs_bleeding_rear_right
}

@Composable
private fun AbsBleedingWheelButton(
    wheel: BrakeBleedingWheel,
    activeWheel: BrakeBleedingWheel?,
    running: Boolean,
    enabled: Boolean,
    onWheelClick: (BrakeBleedingWheel) -> Unit
) {
    val isActive = activeWheel == wheel
    FilledTonalButton(
        enabled = enabled && !running,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        contentPadding = PaddingValues(2.dp),
        border = BorderStroke(ToafStyles.borderSize, if (isActive) Color.White else ToafColors.borderColor),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = ToafColors.greenButton,
            disabledContainerColor = if (isActive) absBleedingActiveColor else ToafColors.disabledButton
        ),
        onClick = { onWheelClick(wheel) },
        content = {
            Text(
                text = stringResource(wheel.nameResource()),
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    )
}

/** Rzut z gory Rover 75 (przod u gory) z zaznaczonym odpowietrzanym kolem */
@Composable
private fun AbsBleedingCarTopView(activeWheel: BrakeBleedingWheel?, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val bodyHeight = min(size.height * 0.96f, size.width / 0.5f)
        val bodyWidth = bodyHeight * 0.38f
        val left = (size.width - bodyWidth) / 2f
        val top = (size.height - bodyHeight) / 2f
        fun x(ratio: Float) = left + bodyWidth * ratio
        fun y(ratio: Float) = top + bodyHeight * ratio

        val wheelWidth = bodyWidth * 0.16f
        val wheelHeight = bodyHeight * 0.14f
        val outline = Stroke(width = 1.5.dp.toPx())

        // Kola - pod nadwoziem, wystaja na boki
        BrakeBleedingWheel.entries.forEach { wheel ->
            val isLeft = wheel == BrakeBleedingWheel.FrontLeft || wheel == BrakeBleedingWheel.RearLeft
            val isFront = wheel == BrakeBleedingWheel.FrontLeft || wheel == BrakeBleedingWheel.FrontRight
            val wheelLeft = if (isLeft) left - wheelWidth * 0.45f else left + bodyWidth - wheelWidth * 0.55f
            val wheelTop = y(if (isFront) 0.2f else 0.78f) - wheelHeight / 2f
            val color = if (activeWheel == wheel) absBleedingActiveColor else Color(0xFF202020)
            drawRoundRect(color, Offset(wheelLeft, wheelTop), Size(wheelWidth, wheelHeight), CornerRadius(wheelWidth * 0.3f))
            drawRoundRect(Color.LightGray, Offset(wheelLeft, wheelTop), Size(wheelWidth, wheelHeight), CornerRadius(wheelWidth * 0.3f), style = outline)
        }

        // Nadwozie
        drawRoundRect(Color(0xFF5B6770), Offset(left, top), Size(bodyWidth, bodyHeight), CornerRadius(bodyWidth * 0.3f))
        drawRoundRect(Color.White, Offset(left, top), Size(bodyWidth, bodyHeight), CornerRadius(bodyWidth * 0.3f), style = outline)

        // Swiatla
        drawRoundRect(Color(0xFFFFF3C4), Offset(x(0.1f), y(0.02f)), Size(bodyWidth * 0.2f, bodyHeight * 0.03f), CornerRadius(3f))
        drawRoundRect(Color(0xFFFFF3C4), Offset(x(0.7f), y(0.02f)), Size(bodyWidth * 0.2f, bodyHeight * 0.03f), CornerRadius(3f))
        drawRoundRect(Color(0xFFD32F2F), Offset(x(0.08f), y(0.95f)), Size(bodyWidth * 0.2f, bodyHeight * 0.03f), CornerRadius(3f))
        drawRoundRect(Color(0xFFD32F2F), Offset(x(0.72f), y(0.95f)), Size(bodyWidth * 0.2f, bodyHeight * 0.03f), CornerRadius(3f))

        val glass = Color(0xFF1E2A33)
        // Szyba przednia
        drawPath(Path().apply {
            moveTo(x(0.16f), y(0.3f))
            lineTo(x(0.84f), y(0.3f))
            lineTo(x(0.92f), y(0.4f))
            lineTo(x(0.08f), y(0.4f))
            close()
        }, glass)
        // Dach
        drawRect(Color(0xFF6E7B85), Offset(x(0.08f), y(0.4f)), Size(bodyWidth * 0.84f, bodyHeight * 0.3f))
        // Szyba tylna
        drawPath(Path().apply {
            moveTo(x(0.08f), y(0.7f))
            lineTo(x(0.92f), y(0.7f))
            lineTo(x(0.84f), y(0.78f))
            lineTo(x(0.16f), y(0.78f))
            close()
        }, glass)
    }
}

@Preview(showBackground = true, backgroundColor = 0xff000000)
@Composable
fun AdvanceControlAbsBleedingPreview()
{
    AdvanceControlAbsBleedingContent(
        activeWheel = BrakeBleedingWheel.FrontLeft,
        cycle = 5,
        cycles = 16,
        progress = 5f / 16f,
        running = true,
        enabled = true,
        onWheelClick = {},
        onModulatorClick = {},
        onStopClick = {}
    )
}
