package pl.misek.toafmobile.modules.interfaces

import pl.misek.toafmobile.livedata.LiveDataElement

interface IModuleLiveData {
    suspend fun readLiveData(): Map<Int, Any>?
    suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean
}