package pl.misek.toafmobile.modules.interfaces

import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.modules.r40.ModuleEWS

/** Pojedynczy krok synchronizacji EWS wraz z jego wynikiem i opcjonalnym detalem (np. ID). */
data class EwsSyncStep(val name: StringResource, val success: Boolean, val detail: String? = null)

/**
 * Synchronizacja immobilizera (EWS) ze sterownikiem silnika (strona silnika).
 * Orkiestracja wykonywana po stronie sterownika silnika; moduł EWS
 * (jesli dostepny) przekazywany jest jako parametr do wykonania handshake.
 * onStep jest wolany po kazdym kroku (raportowanie statusu na biezaco).
 */
interface IModuleEwsSync {

    suspend fun syncEws(ews: ModuleEWS?, onStep: (EwsSyncStep) -> Unit): Boolean
}
