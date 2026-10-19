package pl.misek.toafmobile.modules.interfaces

import pl.misek.toafmobile.modules.enums.BrakeBleedingWheel

interface IModuleBrakeBleeding {
    /** Liczba cykli otwarcia zaworow na jedno kolo */
    val brakeBleedingCycles: Int
    /** Kolejnosc kol przy odpowietrzaniu modulatora (wszystkie kola po kolei) */
    val modulatorBleedingOrder: List<BrakeBleedingWheel>
    /** Wlacza pompe powrotna modulatora */
    suspend fun startBrakeBleeding(): Boolean
    /** Jeden cykl otwarcia zaworow wlotowego i wylotowego wskazanego kola */
    suspend fun bleedWheelCycle(wheel: BrakeBleedingWheel): Boolean
    /** Wylacza pompe powrotna i zamyka zawory */
    suspend fun stopBrakeBleeding(): Boolean
}
