package pl.misek.toafmobile.modules.containers

import pl.misek.toafmobile.modules.enums.EHCControlWheel

/** Wartosci NaN oznaczaja brak danych */
data class EHCHeightLiveData(
    val frontLeft: Float = Float.NaN,
    val frontRight: Float = Float.NaN,
    val rearLeft: Float = Float.NaN,
    val rearRight: Float = Float.NaN,
    val pressure: Float = Float.NaN,
    val compressorTemperature: Float = Float.NaN
) {
    fun height(wheel: EHCControlWheel): Float = when (wheel) {
        EHCControlWheel.FrontLeft -> frontLeft
        EHCControlWheel.FrontRight -> frontRight
        EHCControlWheel.RearLeft -> rearLeft
        EHCControlWheel.RearRight -> rearRight
    }
}
