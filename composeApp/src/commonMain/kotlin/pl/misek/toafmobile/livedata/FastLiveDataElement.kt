package pl.misek.toafmobile.livedata

import pl.misek.toafmobile.tools.JsonSupport

class FastLiveDataElement private constructor(
    elementModuleName: String,
    liveDataName: LiveDataElement?,
    customName: String
) {
    companion object {
        suspend fun create(
            moduleName :String,
            liveDataName: String,
            customName: String
        ): FastLiveDataElement {
            val liveDataElements = JsonSupport.getLiveDataElements(moduleName)
            val element = liveDataElements?.first { x -> x.name == liveDataName }
            return FastLiveDataElement(moduleName,element,customName)
        }
    }
}
