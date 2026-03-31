package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperTxu {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
        else -> {
          Logger.e {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
