package pl.misek.toafmobile.modules

import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.modules.enums.ModuleAddress

class ModuleError(moduleCode: ModuleAddress, errorCode: String, errorDetails: List<Pair<StringResource,Any?>>) {

    var code: String = errorCode
    var description: StringResource? = null
    var details: List<Pair<StringResource, Any?>>

    init {
        description = ResourceMapper.stringResource("${moduleCode.moduleName}_error_$errorCode".lowercase())
        details = errorDetails
    }
}
