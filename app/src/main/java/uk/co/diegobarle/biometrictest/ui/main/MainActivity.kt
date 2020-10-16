package uk.co.diegobarle.biometrictest.ui.main

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import uk.co.diegobarle.biometrictest.R
import uk.co.diegobarle.biometrictest.data.SampleAppUser
import java.lang.StringBuilder

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        displayMessage()
    }

    private fun displayMessage() {
        val message = StringBuilder()
            .append("You successfully signed up as:")
            .append("\n\n")
        if (SampleAppUser.username != null) {
            message.append("user:")
                .append("\n")
                .append(SampleAppUser.username)
                .append("\n\n")
        }
        message.append("fake token:")
            .append("\n")
            .append(SampleAppUser.fakeToken)

        updateApp(message.toString())
    }

    private fun updateApp(successMsg: String) {
        success.text = successMsg
    }
}