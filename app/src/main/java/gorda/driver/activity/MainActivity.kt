package gorda.driver.activity

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.Toast
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import gorda.driver.R
import gorda.driver.activity.background.LocationService
import gorda.driver.databinding.ActivityMainBinding
import gorda.driver.location.LocationHandler
import gorda.driver.models.Driver
import gorda.driver.repositories.DriverRepository
import gorda.driver.services.firebase.Auth
import gorda.driver.services.firebase.FirebaseInitializeApp
import gorda.driver.utils.Utils

@SuppressLint("UseSwitchCompatOrMaterialCode")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var isConnected = false
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var driver: Driver = Driver()
    private lateinit var switchConnect: Switch
    private val signInLauncher = registerForActivityResult(
        FirebaseAuthUIActivityResultContract()
    ) { res ->
        this.onSignInResult(res)
    }
    private val setDriver: (driver: Driver) -> Unit =  {
        this.driver = it
        switchConnect.setOnCheckedChangeListener { buttonView, isChecked ->
            this.setConnected(isChecked, buttonView)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        binding.appBarMain.fab.setOnClickListener {
            Auth.logOut()
            this.onStart()
        }

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        this.switchConnect = binding.appBarMain.toolbar.findViewById(R.id.switchConnect)

        if (!LocationHandler.checkPermissions(this)) {
            requestPermissions()
        }
    }

    override fun onStart() {
        super.onStart()
        if (Auth.getCurrentUser() == null) {
            val intent = Auth.launchLogin()
            this.signInLauncher.launch(intent)
        } else {
            val user = Auth.getCurrentUser()
            user?.uid.let { s ->
                if (s != null) {
                    // TODO: refactor this code within setConnected function
                    DriverRepository.getDriver(s, this.setDriver)
                    DriverRepository.isConnected(s) {
                        this.switchConnect.isChecked = it
                        if (it) {
                            this.switchConnect.setText(R.string.status_connected)
                            startLocationService()
                        } else {
                            this.switchConnect.setText(R.string.status_disconnected)
                            stopLocationService()
                        }
                    }
                }
            }
        }
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            val user = Auth.getCurrentUser()
            user?.uid.let { s ->
                if (s != null) {
                    Log.d(TAG, "setting the driver $s")
                    DriverRepository.getDriver(s, this.setDriver)
                }
            }
        } else {
            Log.e(TAG, "error ${result.resultCode}")
            Toast.makeText(this, R.string.common_error, Toast.LENGTH_LONG).show()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun setConnected(checked: Boolean, switch: CompoundButton): Unit {
        if (checked) {
            this.driver.connect()
                .addOnSuccessListener {
                    switch.setText(R.string.status_connected)
                    startLocationService()
                }
                .addOnCanceledListener {
                    switch.isChecked = false
                }
        } else {
            this.driver.disconnect()
                .addOnSuccessListener {
                    switch.setText(R.string.status_disconnected)
                    stopLocationService()
                }
                .addOnFailureListener {
                    switch.isChecked = true
                }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LocationHandler.PERMISSION_REQUEST_ACCESS_LOCATION) {
            if (grantResults.isEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                finish()
            }
        }
    }

    private fun startLocationService() {
        this.isConnected = true
        val intent = Intent(this, LocationService::class.java)
        intent.action = LocationService.START_SERVICE
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopLocationService() {
        this.isConnected = false
        val intent = Intent(this, LocationService::class.java)
        intent.action = LocationService.STOP_SERVICE
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        ), LocationHandler.PERMISSION_REQUEST_ACCESS_LOCATION
        )
    }
}