package pl.misek.toafmobile.modules.interfaces

import pl.misek.toafmobile.coding.ZcsCoding

interface IModuleZCS {
    suspend fun readZCS(): ZcsCoding?
    suspend fun writeZCS(data: ZcsCoding): Boolean
}
