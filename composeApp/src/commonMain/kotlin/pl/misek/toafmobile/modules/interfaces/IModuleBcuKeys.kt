package pl.misek.toafmobile.modules.interfaces

import pl.misek.toafmobile.modules.containers.BCUKey

interface IModuleBcuKeys : IModuleAdvanceCoding {

    suspend fun removeKeyAt(position: Int): Boolean
    suspend fun addKeyAt(position: Int, keyCode: String): Boolean
    suspend fun readKeys(): List<BCUKey>?
}
