package uk.co.diegobarle.biometrictest.ui.login

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.activity_enable_biometric_login.*
import uk.co.diegobarle.biometrictest.R
import uk.co.diegobarle.biometrictest.data.FailedLoginFormState
import uk.co.diegobarle.biometrictest.data.SampleAppUser
import uk.co.diegobarle.biometrictest.data.SuccessfulLoginFormState
import uk.co.diegobarle.biometrictest.managers.CryptographyManager
import uk.co.diegobarle.biometrictest.utils.ALLOWED_AUTHENTICATOR
import uk.co.diegobarle.biometrictest.utils.BiometricPromptUtils
import uk.co.diegobarle.biometrictest.utils.CIPHERTEXT_WRAPPER
import uk.co.diegobarle.biometrictest.utils.SHARED_PREFS_FILENAME

class EnableBiometricLoginActivity : AppCompatActivity() {

    private lateinit var cryptographyManager: CryptographyManager
    private val loginViewModel by viewModels<LoginViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enable_biometric_login)
        cancelButton.setOnClickListener { finish() }

        loginViewModel.loginWithPasswordFormState.observe(this, Observer { formState ->
            val loginState = formState ?: return@Observer
            when (loginState) {
                is SuccessfulLoginFormState -> authorizeButton.isEnabled = loginState.isDataValid
                is FailedLoginFormState -> {
                    loginState.usernameError?.let { username.error = getString(it) }
                    loginState.passwordError?.let { password.error = getString(it) }
                }
            }
        })
        loginViewModel.loginResult.observe(this, Observer {
            val loginResult = it ?: return@Observer
            if (loginResult.success) {
                showBiometricPromptForEncryption()
            }
        })
        username.doAfterTextChanged {
            loginViewModel.onLoginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }
        password.doAfterTextChanged {
            loginViewModel.onLoginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }
        password.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE ->
                    loginViewModel.login(
                        username.text.toString(),
                        password.text.toString()
                    )
            }
            false
        }
        authorizeButton.setOnClickListener {
            loginViewModel.login(username.text.toString(), password.text.toString())
        }
    }

    private fun showBiometricPromptForEncryption() {
        val canAuthenticate = BiometricManager.from(applicationContext)
            .canAuthenticate(ALLOWED_AUTHENTICATOR)
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val secretKeyName = getString(R.string.secret_key_name)
            cryptographyManager = CryptographyManager()
            val cipher = cryptographyManager.getInitializedCipherForEncryption(secretKeyName)
            val biometricPrompt =
                BiometricPromptUtils.createBiometricPrompt(this, ::encryptAndStoreServerToken)
            val promptInfo = BiometricPromptUtils.createPromptInfo(this)
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }
    }

    private fun encryptAndStoreServerToken(authResult: BiometricPrompt.AuthenticationResult) {
        authResult.cryptoObject?.cipher?.apply {
            SampleAppUser.fakeToken?.let { token ->
                Log.d(TAG, "The token from server is $token")
                val encryptedServerTokenWrapper = cryptographyManager.encryptData(token, this)
                cryptographyManager.persistCiphertextWrapperToSharedPrefs(
                    encryptedServerTokenWrapper,
                    applicationContext,
                    SHARED_PREFS_FILENAME,
                    Context.MODE_PRIVATE,
                    CIPHERTEXT_WRAPPER
                )
            }
        }
        finish()
    }

    companion object {
        private const val TAG = "EnableBiometricLogin"
    }
}