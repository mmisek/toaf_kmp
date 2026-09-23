package pl.misek.toafmobile.modules.interfaces

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource

typealias AdvanceControlShowResult = (result: Boolean, onSuccess: String?, onFail: String?) -> Unit

data class AdvanceControlElement(
    val name: StringResource,
    val content: @Composable (onShowResult: AdvanceControlShowResult) -> Unit
)

interface IModuleAdvanceControl
{
    fun getAdvanceControlElements(): List<AdvanceControlElement>
}
