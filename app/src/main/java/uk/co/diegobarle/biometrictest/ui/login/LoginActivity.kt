package uk.co.diegobarle.biometrictest.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.activity_login.*
import uk.co.diegobarle.biometrictest.R
import uk.co.diegobarle.biometrictest.data.FailedLoginFormState
import uk.co.diegobarle.biometrictest.data.SampleAppUser
import uk.co.diegobarle.biometrictest.data.SuccessfulLoginFormState
import uk.co.diegobarle.biometrictest.managers.CryptographyManager
import uk.co.diegobarle.biometrictest.ui.main.MainActivity
import uk.co.diegobarle.biometrictest.utils.ALLOWED_AUTHENTICATOR
import uk.co.diegobarle.biometrictest.utils.BiometricPromptUtils
import uk.co.diegobarle.biometrictest.utils.CIPHERTEXT_WRAPPER
import uk.co.diegobarle.biometrictest.utils.SHARED_PREFS_FILENAME

/**
 * After entering "valid" username and password, login button becomes enabled
 */
class LoginActivity : AppCompatActivity() {
    private val TAG = "LoginActivity"
    private val loginWithPasswordViewModel by viewModels<LoginViewModel>()

    private lateinit var biometricPrompt: BiometricPrompt
    private val cryptographyManager = CryptographyManager()
    private val ciphertextWrapper
        get() = cryptographyManager.getCiphertextWrapperFromSharedPrefs(
            applicationContext,
            SHARED_PREFS_FILENAME,
            Context.MODE_PRIVATE,
            CIPHERTEXT_WRAPPER
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val canAuthenticate = BiometricManager.from(applicationContext)
            .canAuthenticate(ALLOWED_AUTHENTICATOR)
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            useBiometrics.visibility = View.VISIBLE
            useBiometrics.setOnClickListener {
                if (ciphertextWrapper != null) {
                    showBiometricPromptForDecryption()
                } else {
                    startActivity(Intent(this, EnableBiometricLoginActivity::class.java))
                }
            }
        } else {
            useBiometrics.visibility = View.INVISIBLE
        }

        if (ciphertextWrapper == null) {
            setupForLoginWithPassword()
        }
    }

    /**
     * The logic is kept inside onResume instead of onCreate so that authorizing biometrics takes
     * immediate effect.
     */
    override fun onResume() {
        super.onResume()
        showBiometricPromptForDecryption()
    }

    // BIOMETRICS SECTION

    private fun showBiometricPromptForDecryption() {
        ciphertextWrapper?.let { textWrapper ->
            val secretKeyName = getString(R.string.secret_key_name)
            val cipher = cryptographyManager.getInitializedCipherForDecryption(
                secretKeyName, textWrapper.initializationVector
            )
            biometricPrompt =
                BiometricPromptUtils.createBiometricPrompt(
                    this,
                    ::decryptServerTokenFromStorage
                )
            val promptInfo = BiometricPromptUtils.createPromptInfo(this)
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }
    }

    private fun decryptServerTokenFromStorage(authResult: BiometricPrompt.AuthenticationResult) {
        ciphertextWrapper?.let { textWrapper ->
            authResult.cryptoObject?.cipher?.let {
                val plaintext =
                    cryptographyManager.decryptData(textWrapper.ciphertext, it)
                SampleAppUser.fakeToken = plaintext
                goToMain()
            }
        }
    }

    // USERNAME + PASSWORD SECTION

    private fun setupForLoginWithPassword() {
        loginWithPasswordViewModel.loginWithPasswordFormState.observe(this, Observer { formState ->
            val loginState = formState ?: return@Observer
            when (loginState) {
                is SuccessfulLoginFormState -> loginButton.isEnabled = loginState.isDataValid
                is FailedLoginFormState -> {
                    loginState.usernameError?.let { username.error = getString(it) }
                    loginState.passwordError?.let { password.error = getString(it) }
                }
            }
        })
        loginWithPasswordViewModel.loginResult.observe(this, Observer {
            val loginResult = it ?: return@Observer
            if (loginResult.success) {
                goToMain()
            }
        })
        username.doAfterTextChanged {
            loginWithPasswordViewModel.onLoginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }
        password.doAfterTextChanged {
            loginWithPasswordViewModel.onLoginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }
        password.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE ->
                    loginWithPasswordViewModel.login(
                        username.text.toString(),
                        password.text.toString()
                    )
            }
            false
        }
        loginButton.setOnClickListener {
            loginWithPasswordViewModel.login(
                username.text.toString(),
                password.text.toString()
            )
        }
        Log.d(TAG, "Username ${SampleAppUser.username}; fake token ${SampleAppUser.fakeToken}")
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
    }
}