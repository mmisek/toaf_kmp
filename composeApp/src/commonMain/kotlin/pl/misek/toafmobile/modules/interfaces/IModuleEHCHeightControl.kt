package pl.misek.toafmobile.modules.interfaces

import pl.misek.toafmobile.modules.containers.EHCHeightLiveData
import pl.misek.toafmobile.modules.enums.EHCControlWheel
import pl.misek.toafmobile.modules.enums.EHCControlWheelDirection

interface IModuleEHCHeightControl {
    /** Przelacza sterownik w tryb sterowania recznego - wymagane przed controlHeight */
    suspend fun startManualControl(): Boolean
    /** Podnosi/opuszcza wskazane kolo, komende nalezy powtarzac cyklicznie */
    suspend fun controlHeight(wheel: EHCControlWheel, direction: EHCControlWheelDirection): Boolean
    /** Zamyka wszystkie zawory, sterownik pozostaje w trybie recznym */
    suspend fun closeValves(): Boolean
    /** Zamyka zawory i wychodzi z trybu recznego - sterownik wraca do pracy automatycznej */
    suspend fun stopControl(): Boolean
    suspend fun readHeightLiveData(): EHCHeightLiveData?
}
