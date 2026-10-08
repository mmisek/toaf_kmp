package pl.misek.toafmobile.modules.interfaces

import pl.misek.toafmobile.modules.containers.EwsKey

interface IModuleEwsKeys {

    suspend fun readKeys(): List<EwsKey>?
    suspend fun readIsn(): String?
    suspend fun readPassword(): String?
}
