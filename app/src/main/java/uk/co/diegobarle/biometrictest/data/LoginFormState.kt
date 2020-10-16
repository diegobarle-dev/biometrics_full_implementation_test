package uk.co.diegobarle.biometrictest.data

/**
 * Data validation state of the login form.
 */
sealed class LoginFormState

data class FailedLoginFormState(
    val usernameError: Int? = null,
    val passwordError: Int? = null
) : LoginFormState()

data class SuccessfulLoginFormState(
    val isDataValid: Boolean = false
) : LoginFormState()