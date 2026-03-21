package pl.misek.toafmobile.generated

import org.jetbrains.compose.resources.StringResource
import toafmobile.composeapp.generated.resources.*

object ResourceMapperActivityFlashPrograming {
    fun stringResource(resourceName: String): StringResource? {
        return when (resourceName) {
            "activity_flash_programing" -> Res.string.activity_flash_programing
            "activity_flash_programing_warning" -> Res.string.activity_flash_programing_warning
            "activity_flash_programming_backup_flash" -> Res.string.activity_flash_programming_backup_flash
            "activity_flash_programming_close" -> Res.string.activity_flash_programming_close
            "activity_flash_programming_diagnostic_session" -> Res.string.activity_flash_programming_diagnostic_session
            "activity_flash_programming_ecu_init" -> Res.string.activity_flash_programming_ecu_init
            "activity_flash_programming_ecu_reset" -> Res.string.activity_flash_programming_ecu_reset
            "activity_flash_programming_end" -> Res.string.activity_flash_programming_end
            "activity_flash_programming_failed" -> Res.string.activity_flash_programming_failed
            "activity_flash_programming_file_name" -> Res.string.activity_flash_programming_file_name
            "activity_flash_programming_file_size" -> Res.string.activity_flash_programming_file_size
            "activity_flash_programming_flash_erasing" -> Res.string.activity_flash_programming_flash_erasing
            "activity_flash_programming_flash_read" -> Res.string.activity_flash_programming_flash_read
            "activity_flash_programming_flash_reading" -> Res.string.activity_flash_programming_flash_reading
            "activity_flash_programming_flash_write" -> Res.string.activity_flash_programming_flash_write
            "activity_flash_programming_flash_writing" -> Res.string.activity_flash_programming_flash_writing
            "activity_flash_programming_flash_writing_end" -> Res.string.activity_flash_programming_flash_writing_end
            "activity_flash_programming_ok" -> Res.string.activity_flash_programming_ok
            "activity_flash_programming_read_error" -> Res.string.activity_flash_programming_read_error
            "activity_flash_programming_read_flash" -> Res.string.activity_flash_programming_read_flash
            "activity_flash_programming_read_size" -> Res.string.activity_flash_programming_read_size
            "activity_flash_programming_read_time" -> Res.string.activity_flash_programming_read_time
            "activity_flash_programming_security_access" -> Res.string.activity_flash_programming_security_access
            "activity_flash_programming_start" -> Res.string.activity_flash_programming_start
            "activity_flash_programming_tittle" -> Res.string.activity_flash_programming_tittle
            "activity_flash_programming_write_data_to_file_error" -> Res.string.activity_flash_programming_write_data_to_file_error
            "activity_flash_programming_write_error" -> Res.string.activity_flash_programming_write_error
            "activity_flash_programming_write_flash" -> Res.string.activity_flash_programming_write_flash
            "activity_flash_programming_write_time" -> Res.string.activity_flash_programming_write_time
            "activity_flash_programming_written_to" -> Res.string.activity_flash_programming_written_to
            "activity_flash_programming_wrong_file_size" -> Res.string.activity_flash_programming_wrong_file_size
            "activity_flash_read_identification" -> Res.string.activity_flash_read_identification
        else -> {
          println("NO RESOURCE for : $resourceName")
            null
        }
     }
   }
}
