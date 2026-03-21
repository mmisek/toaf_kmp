package pl.misek.toafmobile.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import pl.misek.toafmobile.database.entities.*

@Dao
interface ToafDao {

    @Query("SELECT * FROM settings LIMIT 1")
    suspend fun getSettings(): SettingsEntity?

    @Query("SELECT * FROM settings LIMIT 1")
    fun observeSettings(): Flow<SettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: SettingsEntity): Long

    @Update
    suspend fun updateSettings(settings: SettingsEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFastControl(item: FastControlEntity): Long

    @Query("DELETE FROM fast_action_control WHERE module_name = :module AND control_name = :control")
    suspend fun deleteFastControl(module: String, control: String): Int

    @Query("SELECT * FROM fast_action_control WHERE module_name = :module AND control_name = :control")
    suspend fun getFastControl(module: String, control: String): FastControlEntity?

    @Query("SELECT * FROM fast_action_control WHERE module_name = :module")
    suspend fun getFastControlsForModule(module: String): List<FastControlEntity>

    @Query("SELECT * FROM fast_action_control")
    suspend fun getAllFastControls(): List<FastControlEntity>

    @Query("SELECT COUNT(*) FROM fast_action_control")
    suspend fun getFastControlCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFastLiveData(item: FastLiveDataEntity): Long

    @Query("DELETE FROM fast_action_live_data WHERE module_name = :module AND live_data_name = :name")
    suspend fun deleteFastLiveData(module: String, name: String): Int

    @Query("SELECT * FROM fast_action_live_data WHERE module_name = :module")
    suspend fun getFastLiveDataForModule(module: String): List<FastLiveDataEntity>

    @Query("SELECT * FROM fast_action_live_data WHERE module_name = :module AND live_data_name = :name")
    suspend fun getFastLiveData(module: String, name: String): FastLiveDataEntity?

    @Query("SELECT COUNT(*) FROM fast_action_live_data")
    suspend fun getFastLiveDataCount(): Int

    @Query("SELECT * FROM fast_action_live_data")
    suspend fun getAllFastLiveData(): List<FastLiveDataEntity>

    @Insert
    suspend fun insertTemplate(template: LiveDataTemplateEntity): Long

    @Query("UPDATE live_data_templates SET name = :name, codes = :codes WHERE id = :id")
    suspend fun updateTemplate(id: Int, name: String, codes: String): Int

    @Query("DELETE FROM live_data_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Int): Int

    @Query("SELECT codes FROM live_data_templates WHERE id = :id")
    suspend fun getTemplateCodes(id: Int): String?

    @Query("SELECT id, name FROM live_data_templates WHERE module = :module")
    suspend fun getTemplatesForModule(module: String): List<TemplateSimple>
}

data class TemplateSimple(val id: Int, val name: String)