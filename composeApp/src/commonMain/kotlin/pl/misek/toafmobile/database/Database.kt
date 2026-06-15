package pl.misek.toafmobile.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.control.FastControlElement
import pl.misek.toafmobile.database.dao.ToafDao
import pl.misek.toafmobile.database.entities.*
import pl.misek.toafmobile.livedata.FastLiveDataElement

class Database(private val dao : ToafDao) {

    private val tag = "Database"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun getStringSettingValueByName(name: String): String? {
        return withContext(Dispatchers.IO) {
            val settings = dao.getSettings() ?: return@withContext null

            when (name) {
                DatabaseSettingFields.INTERNET_ACCOUNT_NECESSARY -> settings.internetAccountNecessary
                DatabaseSettingFields.ACCESS_TO_PUBLIC_BACKUP -> settings.accessToPublicBackup
                DatabaseSettingFields.LAST_DEVICE_TYPE -> settings.lastDeviceType
                DatabaseSettingFields.WIFI_DEVICE_IP -> settings.wifiDeviceIp
                DatabaseSettingFields.TOAF_FULL_VERSION -> settings.toafFullVersion
                DatabaseSettingFields.SERIAL_PORT_NAME ->settings.serialPortName
                else -> null
            }
        }
    }

    suspend fun getIntSettingValueByName(name: String): Int {
        return withContext(Dispatchers.IO) {
            val settings = dao.getSettings() ?: return@withContext 0

            when (name) {
                DatabaseSettingFields.TOAF_BCU_KEYS -> settings.toafBcuKeys
                DatabaseSettingFields.TOAF_CURRENT_CAR_TYPE -> settings.toafCurrentCarType
                DatabaseSettingFields.TOAF_EKA_CODES -> settings.toafEkaCodes
                DatabaseSettingFields.TOAF_KWP_FAST_INIT_SLEEP -> settings.toafKwpFastInitSleep
                else -> 0
            }
        }
    }

    suspend fun updateStringSettingByName(name: String, value: String?): Boolean {
        return withContext(Dispatchers.IO) {
            val settings = dao.getSettings() ?: run {
                val newSettings = SettingsEntity()
                dao.insertSettings(newSettings)
                dao.getSettings() ?: return@withContext false
            }

            var changed = true
            when (name) {
                DatabaseSettingFields.INTERNET_ACCOUNT_NECESSARY -> settings.internetAccountNecessary = value
                DatabaseSettingFields.ACCESS_TO_PUBLIC_BACKUP -> settings.accessToPublicBackup = value
                DatabaseSettingFields.LAST_DEVICE_TYPE -> settings.lastDeviceType = value
                DatabaseSettingFields.WIFI_DEVICE_IP -> settings.wifiDeviceIp = value
                DatabaseSettingFields.TOAF_FULL_VERSION -> settings.toafFullVersion = value
                DatabaseSettingFields.SERIAL_PORT_NAME -> settings.serialPortName = value
                else -> changed = false
            }

            if (changed) {
                dao.updateSettings(settings) > 0
            } else {
                false
            }
        }
    }

    suspend fun updateIntSettingByName(name: String, value: Int): Boolean {
        return withContext(Dispatchers.IO) {
            val settings = dao.getSettings() ?: return@withContext false

            var changed = true
            when (name) {
                DatabaseSettingFields.TOAF_BCU_KEYS -> settings.toafBcuKeys = value
                DatabaseSettingFields.TOAF_CURRENT_CAR_TYPE -> settings.toafCurrentCarType = value
                DatabaseSettingFields.TOAF_EKA_CODES -> settings.toafEkaCodes = value
                DatabaseSettingFields.TOAF_KWP_FAST_INIT_SLEEP -> settings.toafKwpFastInitSleep = value
                else -> changed = false
            }

            if (changed) {
                dao.updateSettings(settings) > 0
            } else {
                false
            }
        }
    }

    suspend fun insertSettings(login: String?, hash: String?): Long {
        return withContext(Dispatchers.IO) {
            val existing = dao.getSettings()
            if (existing != null) {
                existing.userLogin = login
                existing.userPassword = hash
                dao.updateSettings(existing).toLong()
            } else {
                val newSettings = SettingsEntity(userLogin = login, userPassword = hash)
                dao.insertSettings(newSettings)
            }
        }
    }

    suspend fun updateSettings(login: String?, pass: String?): Boolean {
        return withContext(Dispatchers.IO) {
            val settings = dao.getSettings()
            var result = false
            if (settings == null)
            {
                result = insertSettings(login,pass) > 0
            }
            else {
                settings.userLogin = login
                settings.userPassword = pass
                result = dao.updateSettings(settings) > 0
            }
            result
        }
    }

    suspend fun getLoginAndPassword(): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            val settings = dao.getSettings() ?: return@withContext null
            Pair(first = settings.userLogin ?: "", second = settings.userPassword ?: "")
        }
    }

    suspend fun insertFastActionControl(
        module: String?,
        controlName: String?,
        controlValue: Float,
        customName: String?
    ): Long {
        if (module == null || controlName == null) return -1
        return withContext(Dispatchers.IO) {
            dao.insertFastControl(
                FastControlEntity(module, controlName, controlValue, customName)
            )
        }
    }

    suspend fun deleteFastActionControl(moduleName: String, controlName: String): Int {
        return withContext(Dispatchers.IO) {
            dao.deleteFastControl(moduleName, controlName)
        }
    }

    suspend fun selectFastControlElement(moduleName: String, controlName: String): FastControlElement? {
        return withContext(Dispatchers.IO) {
            val entity = dao.getFastControl(moduleName, controlName)
            entity?.let {
                FastControlElement.create( it.moduleName, it.controlName, it.controlValue, it.customName ?: "")
            }
        }
    }

    suspend fun selectFastControlElementsForModule(moduleName: String): List<FastControlElement> {
        return withContext(Dispatchers.IO) {
            dao.getFastControlsForModule(moduleName).map {
                FastControlElement.create( it.moduleName, it.controlName, it.controlValue, it.customName ?: "")
            }
        }
    }

    suspend fun selectAllFastControlElements(): Collection<FastControlElement> {

        return withContext(Dispatchers.IO) {
            dao.getAllFastControls().map {
                FastControlElement.create( it.moduleName, it.controlName, it.controlValue, it.customName ?:"")
            }
        }
    }

    suspend fun selectFastActionElementCount(): Int {
        return withContext(Dispatchers.IO) {
            dao.getFastControlCount()
        }
    }

    suspend fun insertFastLiveDataElement(
        module: String?,
        liveDataName: String?,
        customName: String?
    ): Long {
        if (module == null || liveDataName == null) return -1
        return withContext(Dispatchers.IO) {
            dao.insertFastLiveData(
                FastLiveDataEntity(module, liveDataName, customName)
            )
        }
    }

    suspend fun deleteFastLiveDataElement(moduleName: String, liveDataName: String): Int {
        return withContext(Dispatchers.IO) {
            dao.deleteFastLiveData(moduleName, liveDataName)
        }
    }

    suspend fun selectFastLiveDataElementsForModule(moduleName: String): List<FastLiveDataElement> {
        return withContext(Dispatchers.IO) {
            dao.getFastLiveDataForModule(moduleName).map {
                FastLiveDataElement.create( it.moduleName, it.liveDataName, it.customName ?: "")
            }
        }
    }

    suspend fun selectFastLiveDataElement(moduleName: String, liveDataName: String): FastLiveDataElement? {
        return withContext(Dispatchers.IO) {
            val entity = dao.getFastLiveData(moduleName, liveDataName)
            entity?.let {
                FastLiveDataElement.create( it.moduleName, it.liveDataName, it.customName ?: "")
            }
        }
    }

    suspend fun selectFastLiveDataElementCount(): Int {
        return withContext(Dispatchers.IO) {
            dao.getFastLiveDataCount()
        }
    }

    suspend fun selectAllFastLiveDataElements(): List<FastLiveDataElement> {
        return withContext(Dispatchers.IO) {
            dao.getAllFastLiveData().map {
                FastLiveDataElement.create( it.moduleName, it.liveDataName, it.customName ?: "")
            }
        }
    }

    suspend fun insertLiveDataTemplate(module: String, name: String, data: String): Long {
        return withContext(Dispatchers.IO) {
            dao.insertTemplate(
                LiveDataTemplateEntity(module = module, name = name, codes = data)
            )
        }
    }

    suspend fun updateLiveDateTemplate(id: Int, name: String, data: String): Boolean {
        return withContext(Dispatchers.IO) {
            dao.updateTemplate(id, name, data) > 0
        }
    }

    suspend fun deleteLiveDataTemplate(id: Int): Boolean {
        return withContext(Dispatchers.IO) {
            dao.deleteTemplate(id) > 0
        }
    }

    suspend fun selectLiveDateTemplateValues(id: Int): List<String> {
        return withContext(Dispatchers.IO) {
            val codes = dao.getTemplateCodes(id) ?: ""
            if (codes.isNotEmpty()) {
                codes.split(";".toRegex())
                    .filter { it.isNotEmpty() }
            } else {
                emptyList()
            }
        }
    }

    suspend fun selectLiveDateTemplatesForModule(module: String): ArrayList<Pair<Int, String>> {
        return withContext(Dispatchers.IO) {
            val list = dao.getTemplatesForModule(module)
            val result = ArrayList<Pair<Int, String>>()
            list.forEach { item ->
                result.add(Pair(item.id, item.name))
            }
            result
        }
    }

    fun observeSetting() : Flow<SettingsEntity?>
    {
        return dao.observeSettings().distinctUntilChanged()
    }

    val settingState : StateFlow<SettingsEntity?> = dao.observeSettings()
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

}