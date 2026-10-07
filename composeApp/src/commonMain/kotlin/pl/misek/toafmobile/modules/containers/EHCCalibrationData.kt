package pl.misek.toafmobile.modules.containers

import pl.misek.toafmobile.modules.enums.EHCAxle

/** Wynik obliczenia kalibracji osi (odpowiednik FAHRZEUG_HOEHE_ABGLEICHEN / STATUS_ABGLEICH z SGBD EHC_2) */
data class EHCCalibrationData(
    val axle: EHCAxle,
    /** Wysokosc wg sterownika [mm] */
    val ecuHeightLeft: Int,
    val ecuHeightRight: Int,
    /** Wysokosc zmierzona [mm] */
    val measuredHeightLeft: Int,
    val measuredHeightRight: Int,
    /** Rozdzielczosc czujnikow z kodowania */
    val resolutionLeft: Int,
    val resolutionRight: Int,
    /** Offsety zera [AD counts] */
    val offsetLeftOld: Int,
    val offsetRightOld: Int,
    val offsetLeftNew: Int,
    val offsetRightNew: Int,
    /** Dane do zapisu telegramem 0x41 */
    val adjustmentData: EHCAdjustmentData
) {
    val deviationLeft: Int get() = ecuHeightLeft - measuredHeightLeft
    val deviationRight: Int get() = ecuHeightRight - measuredHeightRight

    /** ABGLEICHSTATUS - nowe offsety w dopuszczalnym zakresie */
    val isValid: Boolean
        get() = offsetLeftNew in (OFFSET_MIN + 1) until OFFSET_MAX &&
                offsetRightNew in (OFFSET_MIN + 1) until OFFSET_MAX

    companion object {
        const val OFFSET_MIN = 100
        const val OFFSET_MAX = 900
    }
}
