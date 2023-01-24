package gorda.driver.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaPlayer
import android.os.*
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.*
import com.bumptech.glide.Glide
import com.google.android.material.navigation.NavigationView
import gorda.driver.R
import gorda.driver.background.LocationService
import gorda.driver.databinding.ActivityMainBinding
import gorda.driver.interfaces.LocationUpdateInterface
import gorda.driver.location.LocationHandler
import gorda.driver.models.Driver
import gorda.driver.models.Service
import gorda.driver.services.firebase.Auth
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.LocationBroadcastReceiver
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.utils.Constants
import gorda.driver.utils.Constants.Companion.LOCATION_EXTRA


@SuppressLint("UseSwitchCompatOrMaterialCode")
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var preferences: SharedPreferences
    private lateinit var player: MediaPlayer
    private lateinit var cancelPlayer: MediaPlayer
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getPreferences(MODE_PRIVATE)

        intent.getStringExtra(Constants.DRIVER_ID_EXTRA)?.let {
            viewModel.getDriver(it)
        }

        player = MediaPlayer.create(this, R.raw.assigned_service)
        cancelPlayer = MediaPlayer.create(this, R.raw.cancel_service)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_profile, R.id.nav_current_service, R.id.nav_about
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        navView.setNavigationItemSelectedListener { item ->
            if (item.itemId == R.id.logout) {
                driver.id?.let { viewModel.disconnect(driver) }
                Auth.logOut(this).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val intent = Intent(this, StartActivity::class.java)
                        startActivity(intent)
                    }
                }
            }
            NavigationUI.onNavDestinationSelected(item, navController)
            drawerLayout.closeDrawer(GravityCompat.START)
            true
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

        observeDriver(navView)

        viewModel.currentService.observe(this) { currentService ->
            currentService?.status?.let { status ->
                when (status) {
                    Service.STATUS_IN_PROGRESS -> {
                        if (Auth.getCurrentUserUUID() == currentService.driver_id) {
                            val notifyId = preferences.getInt(
                                Constants.SERVICES_NOTIFICATION_ID,
                                currentService.created_at.toInt()
                            )
                            if (notifyId != currentService.created_at.toInt())
                                playSound(currentService.created_at.toInt())
                            navController.navigate(R.id.nav_current_service)
                        }
                    }
                    Service.STATUS_CANCELED -> {
                        val cancelNotifyId = preferences.getInt(
                            Constants.CANCEL_SERVICES_NOTIFICATION_ID,
                            currentService.created_at.toInt()
                        )
                        if (cancelNotifyId != currentService.created_at.toInt())
                            playCancelSound(currentService.created_at.toInt())
                        if (navController.currentDestination?.id == R.id.nav_current_service)
                            navController.navigate(R.id.nav_home)
                    }
                    else -> {
                        if (navController.currentDestination?.id == R.id.nav_current_service)
                            navController.navigate(R.id.nav_home)
                        val editor: SharedPreferences.Editor = preferences.edit()
                        editor.putString(Constants.CURRENT_SERVICE_ID, null)
                        editor.apply()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                locationBroadcastReceiver,
                IntentFilter(LocationBroadcastReceiver.ACTION_LOCATION_UPDATES)
            )
        LocationHandler.getLastLocation()?.let {
            it.addOnSuccessListener { loc ->
                viewModel.updateLocation(loc)
            }
        }
        Intent(this, LocationService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_NOT_FOREGROUND)
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

    private fun showDisClosure(): Unit {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
        driver.photoUrl?.let { url ->
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
            applicationContext.startService(intent)
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

    private fun playSound(notifyId: Int) {
        player.start()
        val editor: SharedPreferences.Editor = preferences.edit()
        editor.putInt(Constants.SERVICES_NOTIFICATION_ID, notifyId)
        editor.apply()
    }

    private fun playCancelSound(notifyId: Int) {
        cancelPlayer.start()
        val editor: SharedPreferences.Editor = preferences.edit()
        editor.putInt(Constants.CANCEL_SERVICES_NOTIFICATION_ID, notifyId)
        editor.putString(Constants.CURRENT_SERVICE_ID, null)
        editor.apply()
    }

    override fun onBackPressed() {
        if (navController.currentDestination != null && navController.currentDestination?.id != R.id.nav_home) {
            super.onBackPressed()
        }
    }
}
