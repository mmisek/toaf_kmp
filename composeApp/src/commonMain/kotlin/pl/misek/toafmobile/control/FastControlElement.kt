package pl.misek.toafmobile.control

import pl.misek.toafmobile.tools.JsonSupport

class FastControlElement private constructor(
    elementModuleName: String,
    elementControlName: String,
    elementControlValue: Float,
    elementCustomName: String
) {
    companion object {
        suspend fun create(
            elementModuleName: String,
            elementControlName: String,
            elementControlValue: Float,
            elementCustomName: String
        ): FastControlElement {
            val liveDataElements = JsonSupport.getControlElements(elementModuleName)
            val element = liveDataElements?.first { x -> x.name == elementControlName }
            element?.controlSetValue?.value = elementControlValue.toInt()
            return FastControlElement(elementModuleName,elementControlName,elementControlValue,elementCustomName)
        }
    }
}
