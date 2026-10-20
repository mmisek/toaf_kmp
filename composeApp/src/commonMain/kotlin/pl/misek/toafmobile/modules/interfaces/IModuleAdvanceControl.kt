package pl.misek.toafmobile.modules.interfaces

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.network.UserInfo

typealias AdvanceControlShowResult = (result: Boolean, onSuccess: String?, onFail: String?) -> Unit

data class AdvanceControlElement(
    val name: StringResource,
    /** Nowa funkcja - znacznik NEW na przycisku, przenoszony na przycisk sterowania zaawansowanego i kafelek modulu */
    val isNew: Boolean = false,
    val content: @Composable (userInfo: UserInfo, onShowResult: AdvanceControlShowResult) -> Unit
)

interface IModuleAdvanceControl
{
    fun getAdvanceControlElements(): List<AdvanceControlElement>
}
