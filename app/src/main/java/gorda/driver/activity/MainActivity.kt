package gorda.driver.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.android.material.navigation.NavigationView
import gorda.driver.R
import gorda.driver.activity.background.LocationService
import gorda.driver.activity.ui.MainViewModel
import gorda.driver.activity.ui.service.LocationBroadcastReceiver
import gorda.driver.activity.ui.service.LocationUpdates
import gorda.driver.databinding.ActivityMainBinding
import gorda.driver.interfaces.LocationUpdateInterface
import gorda.driver.location.LocationHandler
import gorda.driver.models.Driver
import gorda.driver.repositories.DriverRepository
import gorda.driver.services.firebase.Auth

@SuppressLint("UseSwitchCompatOrMaterialCode")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private var isConnected = false
    private var msg: Messenger? = null
    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var driver: Driver = Driver()
    private lateinit var switchConnect: Switch
    private lateinit var lastLocation: Location
    private val viewModel: MainViewModel by viewModels()
    private val signInLauncher = registerForActivityResult(FirebaseAuthUIActivityResultContract())
    { res ->
        this.onSignInResult(res)
    }
    private val setDriver: (driver: Driver) -> Unit = {
        this.driver = it
        switchConnect.setOnCheckedChangeListener { buttonView, isChecked ->
            this.setConnected(isChecked, buttonView)
        }
    }
    private var locationService: Messenger? = null
    private var mBound: Boolean = false
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            locationService = Messenger(service)
            mBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            mBound = false
        }
    }
    private val locationBroadcastReceiver =
        LocationBroadcastReceiver(object : LocationUpdateInterface {
            override fun onUpdate(intent: Intent) {
                val extra: Location? = intent.getParcelableExtra("location")
                extra?.let { location ->
                    viewModel.updateLocation(location)
                }
            }
        })

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

        viewModel.lastLocation.observe(this, Observer { locationUpdate ->
            when (locationUpdate) {
                is LocationUpdates.LastLocation -> {
                    lastLocation = locationUpdate.location
                }
            }
        })

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

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                locationBroadcastReceiver,
                IntentFilter(LocationBroadcastReceiver.ACTION_LOCATION_UPDATES)
            )
    }

    override fun onStop() {
        super.onStop()
        if (mBound) {
            this.unbindService(connection)
            mBound = false
            LocalBroadcastManager.getInstance(this).unregisterReceiver(locationBroadcastReceiver)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isLocationEnabled()) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
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
        Intent(this, LocationService::class.java).also { intent ->
            intent.putExtra(Driver.DRIVER_KEY, this.driver.id)
            ContextCompat.startForegroundService(this, intent)
            this.bindService(intent, connection, BIND_AUTO_CREATE)
            mBound = true
        }
    }

    private fun stopLocationService() {
        this.isConnected = false
        if (mBound) {
            this.unbindService(connection)
            val msg: Message = Message.obtain(null, LocationService.STOP_SERVICE_MSG, 0, 0)
            locationService?.send(msg)
            mBound = false
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ), LocationHandler.PERMISSION_REQUEST_ACCESS_LOCATION
        )
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}
