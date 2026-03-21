package pl.misek.toafmobile.modules.interfaces

import pl.misek.toafmobile.control.ControlElement

interface IModuleControl {
    suspend fun control(controlElement: ControlElement): Boolean
}
