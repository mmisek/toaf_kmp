package pl.misek.toafmobile.generated

import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperActivityCodingBcu {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "activity_coding_bcu" -> Res.string.activity_coding_bcu
            "activity_coding_bcu_key_add" -> Res.string.activity_coding_bcu_key_add
            "activity_coding_bcu_key_add_button_add" -> Res.string.activity_coding_bcu_key_add_button_add
            "activity_coding_bcu_key_add_button_cancel" -> Res.string.activity_coding_bcu_key_add_button_cancel
            "activity_coding_bcu_key_add_error" -> Res.string.activity_coding_bcu_key_add_error
            "activity_coding_bcu_key_add_hint" -> Res.string.activity_coding_bcu_key_add_hint
            "activity_coding_bcu_key_add_message" -> Res.string.activity_coding_bcu_key_add_message
            "activity_coding_bcu_key_add_not_supported" -> Res.string.activity_coding_bcu_key_add_not_supported
            "activity_coding_bcu_key_add_sign" -> Res.string.activity_coding_bcu_key_add_sign
            "activity_coding_bcu_key_add_successful" -> Res.string.activity_coding_bcu_key_add_successful
            "activity_coding_bcu_key_add_tittle" -> Res.string.activity_coding_bcu_key_add_tittle
            "activity_coding_bcu_key_code_not_valid" -> Res.string.activity_coding_bcu_key_code_not_valid
            "activity_coding_bcu_key_del_sign" -> Res.string.activity_coding_bcu_key_del_sign
            "activity_coding_bcu_key_delete" -> Res.string.activity_coding_bcu_key_delete
            "activity_coding_bcu_key_empty" -> Res.string.activity_coding_bcu_key_empty
            "activity_coding_bcu_key_number" -> Res.string.activity_coding_bcu_key_number
            "activity_coding_bcu_key_read_error" -> Res.string.activity_coding_bcu_key_read_error
            "activity_coding_bcu_key_remove_error" -> Res.string.activity_coding_bcu_key_remove_error
            "activity_coding_bcu_key_remove_key_successful" -> Res.string.activity_coding_bcu_key_remove_key_successful
            "activity_coding_bcu_key_remove_message" -> Res.string.activity_coding_bcu_key_remove_message
            "activity_coding_bcu_key_remove_no" -> Res.string.activity_coding_bcu_key_remove_no
            "activity_coding_bcu_key_remove_tittle" -> Res.string.activity_coding_bcu_key_remove_tittle
            "activity_coding_bcu_key_remove_yes" -> Res.string.activity_coding_bcu_key_remove_yes
            "activity_coding_bcu_keys" -> Res.string.activity_coding_bcu_keys
            "activity_coding_bcu_tittle" -> Res.string.activity_coding_bcu_tittle
        else -> {
          Logger.i {"NO RESOURCE for : $resourceName" }
            null
        }
     }
   }
}
