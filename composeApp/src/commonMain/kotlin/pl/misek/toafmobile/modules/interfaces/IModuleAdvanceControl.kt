package pl.misek.toafmobile.modules.interfaces

import androidx.compose.runtime.Composable
import java.lang.reflect.Modifier

interface IModuleAdvanceControl
{
    fun getAdvanceControlElement(): @Composable (Modifier) -> Unit
}
