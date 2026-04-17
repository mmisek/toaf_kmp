package pl.misek.toafmobile.modules.interfaces

interface IModuleScuKeys : IModuleAdvanceCoding {

    suspend fun addKey(barCode: String): Boolean
}
