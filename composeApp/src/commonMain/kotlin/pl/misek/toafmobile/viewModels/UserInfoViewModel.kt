package pl.misek.toafmobile.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.network.UserInfo

class UserInfoViewModel(private val toafApi : ToafApi, private val database: Database) : ViewModel()
{
    private val tag = "UserInfoViewModel"
    private val _userInfo = MutableStateFlow(UserInfo())
    val userInfo : StateFlow<UserInfo> = _userInfo.asStateFlow()

    init {
        startPeriodicCheckConnection()
    }
    private fun startPeriodicCheckConnection()
    {
        val isActive = true
        viewModelScope.launch {
            while (isActive)
            {
                try {
                    withContext(Dispatchers.IO)
                    {
                        checkServerLogin()
                    }
                }
                catch (ex: Exception)
                {
                    _userInfo.value = UserInfo()
                    println("$tag ${ex.toString()}")
                }
                delay(120 * 1_000L)
            }
        }
    }

    private suspend fun checkServerLogin()
    {
        val userLogin = database.settingState.value?.userLogin ?: ""
        _userInfo.value = toafApi.getUserInfo() ?: UserInfo(username = userLogin)
    }

    suspend fun performLoginUser()
    {
        checkServerLogin()
    }
}