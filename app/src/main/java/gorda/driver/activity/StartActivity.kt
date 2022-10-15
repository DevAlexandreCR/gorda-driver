package gorda.driver.activity

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import gorda.driver.databinding.ActivityStartBinding
import gorda.driver.services.firebase.Auth
import gorda.driver.utils.Constants

class StartActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "StartActivity"
    }

    private var loginLaunched = false
    private lateinit var binding: ActivityStartBinding
    private val signInLauncher = registerForActivityResult(FirebaseAuthUIActivityResultContract())
    { res ->
        this.onSignInResult(res)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()
        Auth.onAuthChanges { uuid ->
            if (uuid === null) {
                launchLogin()
            } else {
                launchMain(uuid.toString())
            }
        }
    }

    private fun launchLogin(): Unit {
        if (!loginLaunched) {
            loginLaunched = true
            val intent = Auth.launchLogin()
            this.signInLauncher.launch(intent)
        }
    }

    private fun launchMain(uuid: String): Unit {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(Constants.DRIVER_ID_EXTRA, uuid)
        }
        startActivity(intent)
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        loginLaunched = false
        if (result.resultCode != RESULT_OK) {
            Log.e(TAG, "error ${result.resultCode}")
            launchLogin()
        }
    }
}