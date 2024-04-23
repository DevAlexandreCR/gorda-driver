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
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
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
import gorda.driver.interfaces.DeviceInterface
import gorda.driver.interfaces.LocationUpdateInterface
import gorda.driver.location.LocationHandler
import gorda.driver.models.Driver
import gorda.driver.models.Service
import gorda.driver.services.firebase.Auth
import gorda.driver.services.network.NetworkMonitor
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.LocationBroadcastReceiver
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.utils.Constants
import gorda.driver.utils.Constants.Companion.LOCATION_EXTRA
import gorda.driver.utils.Utils


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
    private var driver: Driver = Driver()
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

    private lateinit var snackBar: Snackbar

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)

        this.deviceID = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        this.deviceName = Build.MANUFACTURER + " " + Build.BRAND

        intent.getStringExtra(Constants.DRIVER_ID_EXTRA)?.let {
            viewModel.getDriver(it)
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
        if (Utils.isNewerVersion(Build.VERSION_CODES.M)) {
            snackBar.setTextColor(getColor(R.color.white))
        }

        navView.setNavigationItemSelectedListener { item ->
            if (item.itemId == R.id.logout) {
                driver.id?.let { viewModel.disconnect(driver) }
                Auth.logOut(this)
            }
            NavigationUI.onNavDestinationSelected(item, navController)
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        navController.addOnDestinationChangedListener { controller, destination, _ ->
            if (destination.id == R.id.nav_home) {
                if (viewModel.currentService.value != null) {
                    controller.navigate(R.id.nav_current_service)
                }
            } else if (destination.id == R.id.nav_apply) {
                if (viewModel.isNetWorkConnected.value == false) {
                    controller.navigate(R.id.nav_home)
                }
            }
        }

        this.switchConnect = binding.appBarMain.toolbar.findViewById(R.id.switchConnect)

        switchConnect.setOnClickListener {
            if (switchConnect.isChecked) {
                viewModel.connect(driver)
            } else {
                viewModel.disconnect(driver)
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
            val editor: SharedPreferences.Editor = preferences.edit()
            editor.putBoolean(Constants.NOTIFICATION_MUTE, isNotifyMute)
            editor.apply()
            setIconNotificationButton(isNotifyMute)
        }

        observeDriver(navView)

        networkMonitor.startMonitoring()
        viewModel.isNetWorkConnected.observe(this) {
            if (!it) {
                connectionBar.visibility = View.VISIBLE
                snackBar.show()
                viewModel.setConnectedLocal(false)
            } else {
                driver.id?.let { id -> viewModel.isConnected(id) }
                connectionBar.visibility = View.GONE
                snackBar.dismiss()
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
            currentService?.status?.let { status ->
                when (status) {
                    Service.STATUS_IN_PROGRESS -> {
                        if (navController.currentDestination?.id != R.id.nav_current_service) {
                            navController.navigate(R.id.nav_current_service)
                        }
                    }

                    Service.STATUS_CANCELED -> {
                        viewModel.completeCurrentService()
                        if (navController.currentDestination?.id == R.id.nav_current_service)
                            navController.navigate(R.id.nav_home)
                    }

                    else -> {
                        if (navController.currentDestination?.id == R.id.nav_current_service)
                            navController.navigate(R.id.nav_home)
                            preferences.edit().putString(Constants.CURRENT_SERVICE_ID, null).apply()
                            preferences.edit().remove(Constants.START_TIME).apply()
                            preferences.edit().remove(Constants.MULTIPLIER).apply()
                            preferences.edit().remove(Constants.POINTS).apply()
                    }
                }
            }
        }
    }

    private fun setIconNotificationButton(isNotificationMute: Boolean) {
        if (isNotificationMute) {
            if (Utils.isNewerVersion(Build.VERSION_CODES.M)) {
                notificationButton.backgroundTintList =
                    ColorStateList.valueOf(getColor(R.color.red))
            }
            notificationButton.setImageResource(R.drawable.notifications_off)
        } else {
            if (Utils.isNewerVersion(Build.VERSION_CODES.M)) {
                notificationButton.backgroundTintList =
                    ColorStateList.valueOf(getColor(R.color.secondary_dark))
            }
            notificationButton.setImageResource(R.drawable.notifications_active)
        }
    }

    private fun onNetWorkChange(isConnected: Boolean) {
        viewModel.changeNetWorkStatus(isConnected)
    }

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
    }

    override fun onStop() {
        super.onStop()
        val editor: SharedPreferences.Editor = preferences.edit()
        editor.putString(Constants.CURRENT_SERVICE_ID, viewModel.currentService.value?.id)
        editor.apply()
        if (mBound) {
            this.unbindService(connection)
            mBound = false
            LocalBroadcastManager.getInstance(this).unregisterReceiver(locationBroadcastReceiver)
        }
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
            if (Utils.isNewerVersion(Build.VERSION_CODES.M)) {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(resources.getColor(R.color.primary_light, null))
                alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(resources.getColor(R.color.primary_light, null))
            }
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

        nameDrawer.text = driver.name
        emailDrawer.text = driver.email
        driver.photoUrl.let { url ->
            Glide
                .with(this)
                .load(url)
                .placeholder(R.mipmap.ic_profile)
                .into(imageDrawer)
        }
    }

    private fun observeDriver(navView: NavigationView) {
        viewModel.driverStatus.observe(this) { driverUpdates ->
            when (driverUpdates) {
                is DriverUpdates.IsConnected -> {
                    switchConnect.isChecked = driverUpdates.connected
                    switchConnect.isEnabled = true
                    if (driverUpdates.connected) {
                        startLocationService()
                        switchConnect.setText(R.string.status_connected)
                    } else {
                        switchConnect.setText(R.string.status_disconnected)
                        stopLocationService()
                    }
                }

                is DriverUpdates.Connecting -> {
                    if (driverUpdates.connecting) {
                        switchConnect.setText(R.string.status_connecting)
                    }
                }

                else -> {}
            }
        }

        viewModel.driver.observe(this) {
            when (it) {
                is Driver -> {
                    this.driver = it
                    if (this.driver.device != null) {
                        if (this.deviceID != this.driver.device!!.id) {
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
                        this.driver.id?.let { it1 ->
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
                    viewModel.isConnected(it.id!!)
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
            if (grantResults.isEmpty() && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                finish()
            }
        }
    }

    private fun startLocationService() {
        Intent(this, LocationService::class.java).also { intent ->
            intent.putExtra(Driver.DRIVER_KEY, this.driver.id)
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
        ActivityCompat.requestPermissions(
            this, permissions, LocationHandler.PERMISSION_REQUEST_ACCESS_LOCATION
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.stopMonitoring()
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
