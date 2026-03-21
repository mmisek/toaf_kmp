package pl.misek.toafmobile.generated

import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperNottranslatable {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
        else -> {
          println("NO RESOURCE for : $resourceName")
            null
        }
     }
   }
}
