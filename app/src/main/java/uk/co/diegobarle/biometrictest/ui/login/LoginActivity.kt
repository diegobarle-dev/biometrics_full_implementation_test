package uk.co.diegobarle.biometrictest.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Observer
import kotlinx.android.synthetic.main.activity_login.*
import uk.co.diegobarle.biometrictest.R
import uk.co.diegobarle.biometrictest.data.LoginResult
import uk.co.diegobarle.biometrictest.data.NetworkRepository
import uk.co.diegobarle.biometrictest.managers.CryptographyManager
import uk.co.diegobarle.biometrictest.ui.main.MainActivity
import uk.co.diegobarle.biometrictest.utils.ALLOWED_AUTHENTICATOR
import uk.co.diegobarle.biometrictest.utils.BiometricPromptUtils
import uk.co.diegobarle.biometrictest.utils.CIPHERTEXT_WRAPPER
import uk.co.diegobarle.biometrictest.utils.SHARED_PREFS_FILENAME
import javax.crypto.Cipher

/**
 * After entering "valid" username and password, login button becomes enabled
 */
class LoginActivity : AppCompatActivity() {
    private val viewModel by viewModels<LoginViewModel>()

    private val cryptographyManager: CryptographyManager = CryptographyManager()

    private val cipherTextWrapper
        get() = cryptographyManager.getCiphertextWrapperFromSharedPrefs(
            applicationContext,
            SHARED_PREFS_FILENAME,
            Context.MODE_PRIVATE,
            CIPHERTEXT_WRAPPER
        )

    private var cipherToEncryptOnLogin: Cipher? = null

    private lateinit var biometricPrompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        setupObservers()
        setupViews()

        //Try logging in using Biometrics when user opens app
        showBiometricPromptForDecryption()
    }

    private fun setupObservers() {
        viewModel.loginResult.observe(this, Observer { loginResult ->
            when (loginResult) {
                LoginResult.SUCCESS -> {
                    val cipher = cipherToEncryptOnLogin
                    cipherToEncryptOnLogin = null
                    cipher?.apply {
                        NetworkRepository.fakeToken?.let { token ->
                            encryptAndStoreServerToken(cipher, token)
                        }
                    }
                    goToMain()
                }
                LoginResult.LOGIN_REQUIRE_PIN -> {
                    BiometricPromptUtils.createPromptPinRequired(
                        this,
                        {
                            showBiometricPromptForEncryption()
                        },
                        {
                            updateMessage("")
                        }
                    ).show()
                }
            }
            pin.text?.clear()
        })
    }

    private fun setupViews() {
        if (canAuthenticateWithBiometrics()) {
            useBiometrics.visibility = View.VISIBLE
            useBiometrics.setOnClickListener {
                if (isBiometricsSetup()) {
                    showBiometricPromptForDecryption()
                } else {
                    showBiometricPromptForEncryption()
                }
            }
        } else {
            useBiometrics.visibility = View.INVISIBLE
        }
        setupForLoginWithPassword()
    }

    // BIOMETRICS SECTION

    /**
     * Checks if the phone has biometrics and they are ready to use in the app
     */
    private fun canAuthenticateWithBiometrics(): Boolean =
        BiometricManager.from(applicationContext)
            .canAuthenticate(ALLOWED_AUTHENTICATOR) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Checks if the user has linked the app with the biometric (if the user has already stored
     * an encrypted token within the app).
     */
    private fun isBiometricsSetup(): Boolean = cipherTextWrapper != null

    /**
     * Displays the Biometric dialog to allow the decryption of the token stored within the app.
     */
    private fun showBiometricPromptForDecryption() {
        cipherTextWrapper?.let { textWrapper ->
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

    /**
     * Decrypts the token stored within the app after validating user using Biometrics.
     */
    private fun decryptServerTokenFromStorage(authResult: BiometricPrompt.AuthenticationResult) {
        cipherTextWrapper?.let { textWrapper ->
            authResult.cryptoObject?.cipher?.let {
                val plaintext =
                    cryptographyManager.decryptData(textWrapper.ciphertext, it)
                viewModel.loginWithToken(plaintext)
            }
        }
    }

    /**
     * Displays the Biometric dialog to allow the encryption of the token in order to store it
     * within the app.
     */
    private fun showBiometricPromptForEncryption() {
        val canAuthenticate = BiometricManager.from(applicationContext)
            .canAuthenticate(ALLOWED_AUTHENTICATOR)
        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val secretKeyName = getString(R.string.secret_key_name)
            val cipher = cryptographyManager.getInitializedCipherForEncryption(secretKeyName)
            val biometricPrompt =
                BiometricPromptUtils.createBiometricPrompt(this, ::onReadyToLogin)
            val promptInfo = BiometricPromptUtils.createPromptInfo(this)
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        }
    }

    /**
     * After validating the user using Biometrics, we momentarily store the cipher until the user
     * logs in using pin.
     */
    private fun onReadyToLogin(authResult: BiometricPrompt.AuthenticationResult) {
        cipherToEncryptOnLogin = authResult.cryptoObject?.cipher
        updateMessage(getString(R.string.prompt_pin_required_message))
    }

    /**
     * After user was validated using Biometrics and logged in using pin, use the cipher returned
     * by the Biometrics prompt to encrypt and store the token.
     */
    private fun encryptAndStoreServerToken(cipher: Cipher, token: String) {
        val encryptedServerTokenWrapper = cryptographyManager.encryptData(token, cipher)
        cryptographyManager.persistCiphertextWrapperToSharedPrefs(
            encryptedServerTokenWrapper,
            applicationContext,
            SHARED_PREFS_FILENAME,
            Context.MODE_PRIVATE,
            CIPHERTEXT_WRAPPER
        )
    }

    // USERNAME + PASSWORD SECTION

    private fun setupForLoginWithPassword() {
        pin.doAfterTextChanged {
            if (pin.text?.length == 5) {
                updateMessage("")
                viewModel.loginWithPin(pin.text.toString())
            }
        }
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun updateMessage(text: String) {
        message.text = text
    }
}