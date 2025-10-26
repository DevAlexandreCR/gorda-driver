package gorda.driver.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import gorda.driver.R
import gorda.driver.background.LocationService
import gorda.driver.databinding.ActivityMainBinding
import gorda.driver.helpers.withTimeout
import gorda.driver.interfaces.DeviceInterface
import gorda.driver.interfaces.LocInterface
import gorda.driver.interfaces.LocationUpdateInterface
import gorda.driver.location.LocationHandler
import gorda.driver.models.Driver
import gorda.driver.models.Service
import gorda.driver.services.firebase.Auth
import gorda.driver.services.firebase.Messaging
import gorda.driver.services.network.NetworkMonitor
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.ConnectionBroadcastReceiver
import gorda.driver.ui.service.LocationBroadcastReceiver
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.utils.Constants
import gorda.driver.utils.Constants.Companion.LOCATION_EXTRA
import gorda.driver.utils.Utils
import kotlinx.coroutines.launch


@SuppressLint("UseSwitchCompatOrMaterialCode")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var notificationButton: FloatingActionButton
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var preferences: SharedPreferences
    private lateinit var connectionBar: ProgressBar
    private lateinit var deviceID: String
    private lateinit var deviceName: String
    private var driver: Driver? = null
    private lateinit var switchConnect: Switch
    private lateinit var lastLocation: Location
    private val viewModel: MainViewModel by viewModels()
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
                val extra: Location? = intent.getParcelableExtra(LOCATION_EXTRA)
                extra?.let { location ->
                    viewModel.updateLocation(location)
                }
            }
        })

    private val connectionBroadcastReceiver =
        ConnectionBroadcastReceiver(object : LocationUpdateInterface {
            override fun onUpdate(intent: Intent) {
                val extra: Location? = intent.getParcelableExtra(LOCATION_EXTRA)
                extra?.let { location ->
                    viewModel.driver.value?.let { driver ->
                        driver.connect(object: LocInterface {
                            override var lat = location.latitude
                            override var lng = location.longitude
                        }).addOnSuccessListener {
                            viewModel.setLoading(false)
                            viewModel.setConnectedLocal(true)
                            viewModel.setConnecting(false)
                            viewModel.connected()
                        }.addOnFailureListener { e ->
                            stopLocationService()
                            viewModel.setLoading(false)
                            viewModel.setConnectedLocal(false)
                            viewModel.setConnecting(false)
                            switchConnect.setText(R.string.status_disconnected)
                            e.message?.let { message -> Log.e(TAG, message) }
                        }.withTimeout {
                            stopLocationService()
                            viewModel.setConnecting(false)
                            viewModel.setLoading(false)
                            viewModel.setConnectedLocal(false)
                            viewModel.setErrorTimeout(true)
                            switchConnect.setText(R.string.status_disconnected)
                        }
                    }
                }
            }
        })

    private lateinit var snackBar: Snackbar

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        this.deviceID = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        this.deviceName = Build.MANUFACTURER + " " + Build.BRAND

        intent.getStringExtra(Constants.DRIVER_ID_EXTRA)?.let {
            viewModel.getDriver(it)
            Messaging.init(it)
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        connectionBar = binding.root.findViewById(R.id.connectionBar)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_current_service, R.id.nav_home),
            drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        networkMonitor = NetworkMonitor(this) { isConnected ->
            onNetWorkChange(isConnected)
        }

        snackBar = Snackbar.make(
            binding.root,
            resources.getString(R.string.connection_lost),
            Snackbar.LENGTH_INDEFINITE
        )

        snackBar.setTextColor(getColor(R.color.white))

        navView.setNavigationItemSelectedListener { item ->
            NavigationUI.onNavDestinationSelected(item, navController)
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        navController.addOnDestinationChangedListener { controller, destination, _ ->
            if (destination.id == R.id.nav_home) {
                if (viewModel.currentService.value != null) {
                    controller.navigate(R.id.nav_current_service)
                }
            }
        }

        this.switchConnect = binding.appBarMain.toolbar.findViewById(R.id.switchConnect)

        switchConnect.setOnClickListener {
            driver?.let { driver ->
                if (switchConnect.isChecked) {
                    LocalBroadcastManager.getInstance(this)
                        .registerReceiver(
                            connectionBroadcastReceiver,
                            IntentFilter(ConnectionBroadcastReceiver.ACTION_CONNECTION)
                        )
                    viewModel.connecting()
                    this.startLocationService()
                } else {
                    LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionBroadcastReceiver)
                    this.stopLocationService()
                    viewModel.disconnect(driver)
                }
            }
        }

        viewModel.lastLocation.observe(this) { locationUpdate ->
            when (locationUpdate) {
                is LocationUpdates.LastLocation -> {
                    lastLocation = locationUpdate.location
                }
                else -> {}
            }
        }

        notificationButton = binding.root.findViewById(R.id.notification_button)
        val isNotificationMute: Boolean = preferences.getBoolean(Constants.NOTIFICATION_MUTE, false)
        setIconNotificationButton(isNotificationMute)

        notificationButton.setOnClickListener {
            val isNotifyMute: Boolean = !preferences.getBoolean(Constants.NOTIFICATION_MUTE, false)
            preferences.edit() {
                putBoolean(Constants.NOTIFICATION_MUTE, isNotifyMute)
            }
            setIconNotificationButton(isNotifyMute)
        }

        this.observeDriver(navView)

        // Observe StateFlow for network connectivity
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isNetWorkConnected.collect { isConnected ->
                    if (!isConnected) {
                        connectionBar.visibility = View.VISIBLE
                        viewModel.setLoading(true)
                        viewModel.setConnecting(true)
                    } else {
                        driver?.let { d ->
                            Auth.reloadUser().addOnSuccessListener {
                                viewModel.isConnected(d.id)
                            }.withTimeout {
                                viewModel.setErrorTimeout(true)
                            }
                        }
                        connectionBar.visibility = View.GONE
                        viewModel.setLoading(false)
                    }
                }
            }
        }

        // Observe StateFlow for automatic reconnection
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.shouldAttemptReconnect.collect { shouldReconnect ->
                    Log.d(TAG, "shouldAttemptReconnect changed to: $shouldReconnect, networkConnected: ${viewModel.isNetWorkConnected.value}, switchChecked: ${switchConnect.isChecked}")

                    if (shouldReconnect && viewModel.isNetWorkConnected.value) {
                        Log.d(TAG, "Attempting automatic reconnection...")
                        // Only reconnect if switch was checked (user intended to be connected)
                        if (switchConnect.isChecked) {
                            driver?.let { d ->
                                // Small delay to ensure network is fully stabilized
                                kotlinx.coroutines.delay(500)
                                attemptReconnection(d)
                            }
                        } else {
                            // User manually disconnected, clear the flag
                            Log.d(TAG, "Switch is OFF, clearing reconnection flag")
                            viewModel.clearReconnectionFlag()
                        }
                    }
                }
            }
        }

        // Observe StateFlow for driver status
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.driverStatus.collect { driverUpdates ->
                    when (driverUpdates) {
                        is DriverUpdates.IsConnected -> {
                            switchConnect.isChecked = driverUpdates.connected
                            switchConnect.isEnabled = true
                            if (driverUpdates.connected) {
                                switchConnect.setText(R.string.status_connected)
                                if (!LocationHandler.checkPermissions(this@MainActivity)) {
                                    viewModel.setConnectedLocal(false)
                                    this@MainActivity.stopLocationService()
                                } else {
                                    this@MainActivity.startLocationService()
                                }
                            } else {
                                switchConnect.setText(R.string.status_disconnected)
                                stopLocationService()
                            }
                        }

                        is DriverUpdates.Connecting -> {
                            if (driverUpdates.connecting) {
                                switchConnect.setText(R.string.status_connecting)
                                switchConnect.isEnabled = false
                            } else {
                                switchConnect.isEnabled = true
                            }
                        }

                        else -> {
                            switchConnect.isEnabled = true
                        }
                    }
                }
            }
        }

        viewModel.errorTimeout.observe(this) { error ->
            if (error) {
                Toast.makeText(this, R.string.error_timeout, Toast.LENGTH_SHORT).show()
                viewModel.setErrorTimeout(false)
            }
        }

        viewModel.isLoading.observe(this) {
            if (it) {
                connectionBar.visibility = View.VISIBLE
            } else {
                connectionBar.visibility = View.GONE
            }
        }

        viewModel.getRideFees()

        viewModel.currentService.observe(this) { currentService ->
            if (currentService == null && viewModel.isLoading.value == false) {
                if (navController.currentDestination?.id == R.id.nav_current_service)
                    navController.navigate(R.id.nav_home)
                removeFeeServiceData()
                this.switchConnect.visibility = View.VISIBLE
            } else if (currentService != null) {
                this.switchConnect.visibility = View.GONE
                when (currentService.status) {
                    Service.STATUS_IN_PROGRESS -> {
                        if (navController.currentDestination?.id != R.id.nav_current_service) {
                            navController.navigate(R.id.nav_current_service)
                        }
                    }
                    else -> {
                        viewModel.completeCurrentService()
                        if (navController.currentDestination?.id == R.id.nav_current_service)
                            navController.navigate(R.id.nav_home)
                        removeFeeServiceData()
                    }
                }
            }
        }
    }

    private fun attemptReconnection(driver: Driver) {
        Log.d(TAG, "Starting reconnection process...")

        // Check if we have a valid location
        if (!::lastLocation.isInitialized) {
            Log.w(TAG, "No location available for reconnection, waiting...")
            // Register for connection broadcast to get location
            LocalBroadcastManager.getInstance(this)
                .registerReceiver(
                    connectionBroadcastReceiver,
                    IntentFilter(ConnectionBroadcastReceiver.ACTION_CONNECTION)
                )
            viewModel.connecting()
            startLocationService()
            return
        }

        // Attempt to reconnect with last known location
        viewModel.connecting()
        driver.connect(object: LocInterface {
            override var lat = lastLocation.latitude
            override var lng = lastLocation.longitude
        }).addOnSuccessListener {
            Log.d(TAG, "Automatic reconnection successful")
            viewModel.setLoading(false)
            viewModel.setConnectedLocal(true)
            viewModel.setConnecting(false)
            viewModel.connected()
            startLocationService()
        }.addOnFailureListener { e ->
            Log.e(TAG, "Automatic reconnection failed: ${e.message}")
            stopLocationService()
            viewModel.setLoading(false)
            viewModel.setConnectedLocal(false)
            viewModel.setConnecting(false)
            viewModel.clearReconnectionFlag()
            switchConnect.setText(R.string.status_disconnected)
            switchConnect.isChecked = false
            Toast.makeText(this, R.string.error_reconnection_failed, Toast.LENGTH_SHORT).show()
        }.withTimeout {
            Log.e(TAG, "Automatic reconnection timeout")
            stopLocationService()
            viewModel.setConnecting(false)
            viewModel.setLoading(false)
            viewModel.setConnectedLocal(false)
            viewModel.setErrorTimeout(true)
            viewModel.clearReconnectionFlag()
            switchConnect.setText(R.string.status_disconnected)
            switchConnect.isChecked = false
        }
    }

    private fun removeFeeServiceData() {
        preferences.edit(true) { putString(Constants.CURRENT_SERVICE_ID, null) }
        preferences.edit(true) { remove(Constants.START_TIME) }
        preferences.edit(true) { remove(Constants.MULTIPLIER) }
        preferences.edit(true) { remove(Constants.POINTS) }
    }

    private fun setIconNotificationButton(isNotificationMute: Boolean) {
        if (isNotificationMute) {
            notificationButton.backgroundTintList =
                ColorStateList.valueOf(getColor(R.color.red))
            notificationButton.setImageResource(R.drawable.notifications_off)
        } else {
            notificationButton.backgroundTintList =
                ColorStateList.valueOf(getColor(R.color.secondary_dark))
            notificationButton.setImageResource(R.drawable.notifications_active)
        }
    }

    private fun onNetWorkChange(isConnected: Boolean) {
        viewModel.changeNetWorkStatus(isConnected)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                locationBroadcastReceiver,
                IntentFilter(LocationBroadcastReceiver.ACTION_LOCATION_UPDATES)
            )
        Intent(this, LocationService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_NOT_FOREGROUND)
        }

        Auth.onAuthChanges { uuid ->
            if (uuid === null) {
                val intent = Intent(this, StartActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
        viewModel.isThereCurrentService()
        viewModel.isThereConnectionService()
        networkMonitor.startMonitoring()
    }

    override fun onStop() {
        super.onStop()
        preferences.edit(true) {
            putString(Constants.CURRENT_SERVICE_ID, viewModel.currentService.value?.id)
        }
        if (mBound) {
            this.unbindService(connection)
            mBound = false
            LocalBroadcastManager.getInstance(this).unregisterReceiver(locationBroadcastReceiver)
        }
        networkMonitor.stopMonitoring()
        viewModel.stopNextServiceListener()
    }

    override fun onResume() {
        super.onResume()
        if (!LocationHandler.checkPermissions(this)) {
            showDisClosure()
        }
        if (!isLocationEnabled()) {
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
        requestNotificationPermissionIfNeeded()
    }

    private fun showDisClosure() {
        val builder = AlertDialog.Builder(this)
        builder.setIcon(R.drawable.ic_location_24)
        val layout: View = layoutInflater.inflate(R.layout.disclosure_layout, null)
        builder.setView(layout)
        builder.setPositiveButton(R.string.allow) { _, _ ->
            requestPermissions()
        }
        builder.setNegativeButton(R.string.disallow) { _, _ ->
            finish()
        }
        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(false)
        alertDialog.setOnShowListener {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(resources.getColor(R.color.secondary, null))
            alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(resources.getColor(R.color.red, null))
        }
        alertDialog.show()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    private fun setDrawerHeader(navView: NavigationView) {
        val header = navView.getHeaderView(0)
        val imageDrawer = header.findViewById<ImageView>(R.id.drawer_image)
        val nameDrawer = header.findViewById<TextView>(R.id.drawer_name)
        val emailDrawer = header.findViewById<TextView>(R.id.drawer_email)

        driver?.let { driver ->
            nameDrawer.text = driver.name
            emailDrawer.text = driver.email
            Glide
                .with(this)
                .load(driver.photoUrl)
                .placeholder(R.mipmap.ic_profile)
                .into(imageDrawer)
        }
    }

    private fun observeDriver(navView: NavigationView) {
        viewModel.driver.observe(this) {
            when (it) {
                is Driver -> {
                    this.driver = it
                    if (it.device != null) {
                        if (this.deviceID != it.device!!.id) {
                            Auth.logOut(this).addOnCompleteListener { completed ->
                                if (completed.isSuccessful) {
                                    Toast.makeText(
                                        this,
                                        R.string.logout_first,
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    } else {
                        this.driver!!.id.let { it1 ->
                            viewModel.updateDriverDevice(it1, object : DeviceInterface {
                                override var id = deviceID
                                override var name = deviceName
                            }).addOnFailureListener { exception ->
                                Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    switchConnect.isEnabled = true
                    setDrawerHeader(navView)
                    viewModel.isConnected(it.id)
                }
                else -> {
                    Auth.logOut(this)
                }
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
            var denied = false
            for (i in permissions.indices) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    denied = true
                    if (permissions[i] == Manifest.permission.POST_NOTIFICATIONS) {
                        Toast.makeText(this, R.string.notification_permission_required, Toast.LENGTH_LONG).show()
                    }
                }
            }
            if (denied) {
                finish()
            }
        }
    }

    private fun startLocationService() {
        if (Utils.isNewerVersion(Build.VERSION_CODES.S) && !Utils.isAppInForeground(this)) {
            return
        }
        Intent(this, LocationService::class.java).also { intent ->
            intent.putExtra(Driver.DRIVER_KEY, this.driver?.id)
            if (Utils.isNewerVersion(Build.VERSION_CODES.O)) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            this.bindService(intent, connection, BIND_NOT_FOREGROUND)
            mBound = true
        }
    }

    private fun stopLocationService() {
        if (mBound) {
            this.unbindService(connection)
            val msg: Message = Message.obtain(null, LocationService.STOP_SERVICE_MSG, 0, 0)
            locationService?.send(msg)
            mBound = false
        }
    }

    private fun requestPermissions() {
        var permissions: Array<String> = arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        if (Utils.isNewerVersion(Build.VERSION_CODES.TIRAMISU)) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (Utils.isNewerVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            permissions += Manifest.permission.FOREGROUND_SERVICE_LOCATION
        }

        ActivityCompat.requestPermissions(
            this, permissions, LocationHandler.PERMISSION_REQUEST_ACCESS_LOCATION
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Utils.isNewerVersion(Build.VERSION_CODES.TIRAMISU)) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    LocationHandler.PERMISSION_REQUEST_ACCESS_LOCATION
                )
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (navController.currentDestination != null && navController.currentDestination?.id != R.id.nav_home) {
            super.onBackPressed()
        }
    }
}
