package pl.misek.toafmobile.modules.interfaces

import pl.misek.toafmobile.modules.containers.EHCAdjustmentData
import pl.misek.toafmobile.modules.containers.EHCCalibrationData
import pl.misek.toafmobile.modules.enums.EHCAxle

interface IModuleEHCCalibration {
    /** Wysokosci wg sterownika [mm] w kolejnosci FL, FR, RL, RR */
    suspend fun readSensorHeights(): IntArray?
    suspend fun readAdjustmentData(): EHCAdjustmentData?
    /** Oblicza nowe offsety osi bez zapisu - wysokosci zmierzone [mm] jako odchylka od nominalnej */
    suspend fun calculateCalibration(axle: EHCAxle, measuredLeft: Int, measuredRight: Int): EHCCalibrationData?
    /** Zapisuje kalibracje - zeruje wysokosci referencyjne kontroli nieszczelnosci */
    suspend fun writeCalibration(calibration: EHCCalibrationData): Boolean
    /** Zapisuje aktualne wysokosci jako referencje kontroli nieszczelnosci */
    suspend fun storeLeakHeights(): Boolean
}
