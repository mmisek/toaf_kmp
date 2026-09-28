package pl.misek.toafmobile.modules.interfaces

import pl.misek.toafmobile.modules.containers.ServotronicMap
import pl.misek.toafmobile.modules.containers.ServotronicPoint

interface IModuleServotronic {
    /** Wszystkie domyslne mapy, dla wszystkich obslugiwanych coding index */
    val defaultMaps: List<ServotronicMap>
    /** Domyslne mapy dla wskazanego coding index (CI) */
    fun getDefaultMaps(codingIndex: String?): List<ServotronicMap> =
        defaultMaps.filter { it.codingIndex.equals(codingIndex, ignoreCase = true) }
    suspend fun readCodingIndex(): String?
    suspend fun readServotronicPoints(): List<ServotronicPoint>?
    suspend fun writeServotronicPoints(points: List<ServotronicPoint>): Boolean
}
