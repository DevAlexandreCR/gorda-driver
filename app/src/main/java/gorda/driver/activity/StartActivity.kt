package gorda.driver.activity

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import gorda.driver.databinding.ActivityStartBinding
import gorda.driver.services.firebase.Auth
import gorda.driver.utils.Constants
import gorda.driver.utils.Utils

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

        if (Utils.isNewerVersion()) {
            val notificationManager: NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val locationChannel = NotificationChannel(
                Constants.LOCATION_NOTIFICATION_CHANNEL_ID,
                Constants.LOCATION_NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH
            )
            val servicesChannel = NotificationChannel(
                Constants.SERVICES_NOTIFICATION_CHANNEL_ID,
                Constants.SERVICES_NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH
            )

            locationChannel.description = Constants.LOCATION_NOTIFICATION_CHANNEL_ID
            servicesChannel.description = Constants.SERVICES_NOTIFICATION_CHANNEL_ID
//            locationChannel.setSound(null, null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                servicesChannel.hasUserSetSound()
            }
            notificationManager.createNotificationChannel(locationChannel)
            notificationManager.createNotificationChannel(servicesChannel)
        }
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
        finish()
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