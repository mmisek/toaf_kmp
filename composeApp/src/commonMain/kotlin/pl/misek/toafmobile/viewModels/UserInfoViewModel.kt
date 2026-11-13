package pl.misek.toafmobile.viewModels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.database.Database
import pl.misek.toafmobile.logger.SentryUser
import pl.misek.toafmobile.network.ToafApi
import pl.misek.toafmobile.network.UserInfo
import java.net.SocketTimeoutException

class UserInfoViewModel(private val toafApi : ToafApi, private val database: Database) : ViewModel()
{
    private val tag = "UserInfoViewModel"
    private val _userInfo = MutableStateFlow(UserInfo())
    val userInfo : StateFlow<UserInfo> = _userInfo.asStateFlow()
    private val _loginRequestChannel = Channel<Boolean>()
    val loginResultFlow = _loginRequestChannel.receiveAsFlow()

    init {
        startPeriodicCheckConnection()
    }
    private fun startPeriodicCheckConnection()
    {
        viewModelScope.launch {
            while (true)
            {
                withContext(Dispatchers.IO){
                    checkServerLogin(true)
                }
                delay(120 * 1_000L)
            }
        }
    }

    private suspend fun checkServerLogin(isPeriodic : Boolean) : Boolean
    {
        return try {
            val settings = database.getLoginAndPassword()
            val userLogin = settings?.first ?: ""
            // login saved in the app counts as logged in, regardless of server response
            SentryUser.login = userLogin.ifEmpty { SentryUser.ANONYMOUS }
            val databaseUserLogin = toafApi.getUserInfo()
            if (databaseUserLogin != null)
            {
                _userInfo.value = databaseUserLogin
                true
            } else
            {
                _userInfo.value = UserInfo(username = userLogin)
                false
            }
        }
        catch (ex : Exception)
        {
            Logger.e {"$tag [checkServerLogin], ${ex.toString()}"}
            if (!isPeriodic)
            {
                _userInfo.value = UserInfo()
            }
            false
        }
    }

    suspend fun performLoginUser()
    {
        withContext(Dispatchers.IO)
        {
            val result = checkServerLogin(false)
            _loginRequestChannel.send(result)
        }
    }
}