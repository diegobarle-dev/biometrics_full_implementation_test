package uk.co.diegobarle.biometrictest.ui.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import uk.co.diegobarle.biometrictest.data.*

class LoginViewModel : ViewModel() {

    private val _loginResult = MutableLiveData<LoginResult>()
    val loginResult: LiveData<LoginResult> = _loginResult

    /**
     * Here we would be making a call to the server to get the session token
     */
    fun loginWithPin(pin: String) {
        NetworkRepository.updateToken(java.util.UUID.randomUUID().toString())
        _loginResult.value = LoginResult.SUCCESS
    }

    /**
     * We use expired tokens as an example of server requesting a login using pin
     **/
    fun loginWithToken(token: String) {
        val success = !NetworkRepository.expiredTokens.contains(token)
        if (success) {
            NetworkRepository.updateToken(token)
            _loginResult.value = LoginResult.SUCCESS
        } else {
            _loginResult.value = LoginResult.LOGIN_REQUIRE_PIN
        }

    }
}