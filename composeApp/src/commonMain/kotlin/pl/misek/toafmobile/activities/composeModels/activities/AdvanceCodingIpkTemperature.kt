package pl.misek.toafmobile.activities.composeModels.activities

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import pl.misek.toafmobile.modules.containers.TemperatureGaugeContainer
import pl.misek.toafmobile.modules.enums.TemperatureGaugeEnum
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.temperature_gauge
import org.jetbrains.compose.resources.painterResource

data class TemperatureGaugeState(
    val temperatureGauge: TemperatureGaugeEnum,
    val temperatureValue: Int,
    val positionXRatio: Float,
    val positionYRatio: Float
)

private fun defaultGaugePoints(): List<TemperatureGaugeState> = listOf(
    TemperatureGaugeState(TemperatureGaugeEnum.StartOfBlue,   20, 0.70f, 0.75f),
    TemperatureGaugeState(TemperatureGaugeEnum.EndOfBlue,     21, 0.55f, 0.82f),
    TemperatureGaugeState(TemperatureGaugeEnum.OneQuarter,    31, 0.40f, 0.75f),
    TemperatureGaugeState(TemperatureGaugeEnum.StartOfBuffer, 44, 0.10f, 0.50f),
    TemperatureGaugeState(TemperatureGaugeEnum.EndOfBuffer,   55, 0.10f, 0.40f),
    TemperatureGaugeState(TemperatureGaugeEnum.ThreeQuarter,  66, 0.40f, 0.26f),
    TemperatureGaugeState(TemperatureGaugeEnum.Overheat,      77, 0.70f, 0.15f),
)

fun List<TemperatureGaugeState>.withUpdatedTemperature(
    gauge: TemperatureGaugeEnum,
    value: Int,
    valid: Boolean
): List<TemperatureGaugeState>? {
    if (valid && (value !in 0..180)) return null

    val index = indexOfFirst { it.temperatureGauge == gauge }
    if (index == -1) return null

    if (valid && index == 0 && this[1].temperatureValue < value) return null

    if (valid && index == size - 1 && value < this[index - 1].temperatureValue) return null

    if (valid && index > 0 && index < size - 1) {
        if (value < this[index - 1].temperatureValue || value > this[index + 1].temperatureValue) {
            return null
        }
    }

    return toMutableList().also { it[index] = it[index].copy(temperatureValue = value) }
}

@Composable
fun TemperatureGaugeImage(
    temperaturesInput: List<TemperatureGaugeState>,
    onTemperatureClick: (TemperatureGaugeState) -> Unit,
    modifier: Modifier = Modifier
) {
    val touchSizeMinPx = 20f
    val touchSizeMaxPx = 90f

    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    val temperatures = temperaturesInput.ifEmpty { defaultGaugePoints() }
    Box(
        modifier = modifier
            .onSizeChanged { imageSize = it }
    ) {
        // Background image
        Image(
            painter = painterResource(Res.drawable.temperature_gauge),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(temperatures) {
                    detectTapGestures { offset ->
                        val x = offset.x
                        val y = offset.y
                        val w = imageSize.width.toFloat()
                        val h = imageSize.height.toFloat()

                        val hit = temperatures.firstOrNull { point ->
                            val px = point.positionXRatio * w
                            val py = point.positionYRatio * h
                            x > px - touchSizeMinPx && x < px + touchSizeMaxPx &&
                                    y > py - touchSizeMaxPx && y < py + touchSizeMinPx
                        }

                        if (hit != null && hit.temperatureValue > -1) {
                            onTemperatureClick(hit)
                        }
                    }
                }
        )

        // Text labels drawn on top of the image
        if (imageSize != IntSize.Zero) {
            val w = imageSize.width.toFloat()
            val h = imageSize.height.toFloat()

            temperatures.forEach { point ->
                val label = if (point.temperatureValue > -1) point.temperatureValue.toString() else "--"
                val xDp = with(density) { (point.positionXRatio * w).toDp() }
                val yDp = with(density) { (point.positionYRatio * h).toDp() }

                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 48.sp,
                    modifier = Modifier.absoluteOffset(x = xDp, y = yDp)  // same anchor as canvas.drawText
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ViewModel helper  (optional — use this in your screen ViewModel)
// ---------------------------------------------------------------------------

/**
 * Example of how to manage temperature state in a ViewModel or composable:
 *
 * ```kotlin
 * var temperatures by remember { mutableStateOf(defaultGaugePoints()) }
 *
 * fun updateTemperature(gauge: TemperatureGaugeEnum, value: Int, valid: Boolean): Boolean {
 *     val updated = temperatures.withUpdatedTemperature(gauge, value, valid) ?: return false
 *     temperatures = updated
 *     return true
 * }
 * ```
 */

// ---------------------------------------------------------------------------
// Usage example (screen-level)
// ---------------------------------------------------------------------------

/**
 * ```kotlin
 * @Composable
 * fun IpkTemperatureScreen(fragmentManager: FragmentManager) {
 *     var temperatures by remember { mutableStateOf(defaultGaugePoints()) }
 *
 *     TemperatureGaugeImage(
 *         temperatures = temperatures,
 *         onTemperatureClick = { point ->
 *             val dialog = TemperatureGaugeInputDialog(
 *                 R.string.ipk_temperature_gauge_enter_temperature_value,
 *                 R.string.ipk_temperature_gauge_new_temperature_value,
 *                 R.string.dialog_information_ok,
 *                 R.string.activity_coding_bcu_key_add_button_cancel,
 *                 point.temperatureValue,
 *                 point.temperatureGauge
 *             )
 *             dialog.show(fragmentManager, "TemperatureGaugeInputDialog")
 *         },
 *         modifier = Modifier.fillMaxSize()
 *     )
 * }
 * ```
 */
@Preview(showBackground = true, backgroundColor = 0x0FF00000)
@Composable
fun TemperatureGaugeImagePreview()
{
    TemperatureGaugeImage(emptyList(), {})
}