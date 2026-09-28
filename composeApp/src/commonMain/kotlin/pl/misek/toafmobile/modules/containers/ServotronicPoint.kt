package pl.misek.toafmobile.modules.containers

import kotlin.math.roundToInt

/** Wspolczynnik skalowania mapy pradu Servotronic (05_tune): prad [mA] = rawDec * 3.533 */
const val SERVOTRONIC_CURRENT_SCALING = 3.533

/** Maksymalna wartosc surowa - jeden bajt w EEPROM */
const val SERVOTRONIC_RAW_MAX = 0xFF

/**
 * Punkt mapy Servotronic.
 * @param speed predkosc [km/h]
 * @param rawDec surowa wartosc pradu zapisana w module
 */
data class ServotronicPoint(val speed: Int, val rawDec: Int) {
    val currentMa: Int
        get() = servotronicRawToMa(rawDec)
}

fun servotronicRawToMa(rawDec: Int): Int = (rawDec * SERVOTRONIC_CURRENT_SCALING).roundToInt()

fun servotronicMaToRaw(currentMa: Int): Int = (currentMa / SERVOTRONIC_CURRENT_SCALING).roundToInt()

/**
 * Domyslna mapa Servotronic.
 * @param codingIndex coding index modulu (CI), dla ktorego mapa jest zdefiniowana, np. "04" lub "10"
 * @param name nazwa strojenia, np. 05_tune
 * @param condition warunek zastosowania z pliku definicji kodowania
 */
data class ServotronicMap(val codingIndex: String, val name: String, val condition: String, val points: List<ServotronicPoint>)
