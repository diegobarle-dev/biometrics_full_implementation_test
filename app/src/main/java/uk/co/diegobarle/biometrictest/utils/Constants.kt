package uk.co.diegobarle.biometrictest.utils

import androidx.biometric.BiometricManager

const val SHARED_PREFS_FILENAME = "biometric_prefs"
const val CIPHERTEXT_WRAPPER = "ciphertext_wrapper"

//Defined here for testing purposes, for easy update
const val ALLOWED_AUTHENTICATOR = BiometricManager.Authenticators.BIOMETRIC_WEAK