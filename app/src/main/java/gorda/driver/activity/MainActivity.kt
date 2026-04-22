package gorda.driver.activity

import android.Manifest
import android.content.ActivityNotFoundException
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
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri
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
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import gorda.driver.R
import gorda.driver.background.LocationService
import gorda.driver.databinding.ActivityMainBinding
import gorda.driver.interfaces.DeviceInterface
import gorda.driver.interfaces.LocationUpdateInterface
import gorda.driver.location.LocationHandler
import gorda.driver.models.Driver
import gorda.driver.models.Service
import gorda.driver.services.firebase.Auth
import gorda.driver.services.firebase.Messaging
import gorda.driver.services.network.NetworkMonitor
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.service.ConnectionBroadcastReceiver
import gorda.driver.ui.service.LocationBroadcastReceiver
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.utils.Constants
import gorda.driver.utils.Constants.Companion.LOCATION_EXTRA
import gorda.driver.utils.ServiceHelper
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
    private var unsupportedVersionDialog: AlertDialog? = null
    private var driverLoadFailureDialog: AlertDialog? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private lateinit var deviceID: String
    private lateinit var deviceName: String
    private var driver: Driver? = null
    private lateinit var switchConnect: Switch
    private lateinit var lastLocation: Location
    private val viewModel: MainViewModel by viewModels()
    private val serviceObserverCoordinator = ServiceObserverCoordinator(
        startObservers = { viewModel.startServiceObservers() },
        stopObservers = { viewModel.stopServiceObservers() }
    )
    private var locationService: Messenger? = null
    private var mBound: Boolean = false
    private var lastStartedServiceAttemptId: Long = -1L
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            locationService = Messenger(service)
            mBound = true
            viewModel.onLocationServiceBound()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            mBound = false
            viewModel.onLocationServiceBindFailed()
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
                    viewModel.onInitialConnectionLocation(location)
                }
            }
        })

    @SuppressLint("HardwareIds")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        viewModel.initializePreferences(preferences)

        this.deviceID = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        this.deviceName = Build.MANUFACTURER + " " + Build.BRAND

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

        networkMonitor = NetworkMonitor(
            context = this,
            onNetworkChange = { isConnected ->
                onNetWorkChange(isConnected)
            }
        )

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
        switchConnect.isEnabled = false

        switchConnect.setOnClickListener {
            if (switchConnect.isChecked) {
                viewModel.requestConnect()
            } else {
                if (!viewModel.isNetWorkConnected.value) {
                    Toast.makeText(
                        this,
                        R.string.cannot_disconnect_no_network,
                        Toast.LENGTH_SHORT
                    ).show()
                    switchConnect.isChecked = true
                    return@setOnClickListener
                }
                viewModel.requestDisconnect()
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

        this.observeDriverLoadState(navView)
        bootstrapDriverSession(intent)

        // Observe StateFlow for network connectivity - show non-blocking alert
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isNetWorkConnected.collect { isConnected ->
                    if (!isConnected) {
                        showNetworkLostIndicator()
                    } else {
                        hideNetworkLostIndicator()
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.presenceState.collect { presence ->
                    renderPresenceState(presence)
                }
            }
        }

        viewModel.errorTimeout.observe(this) { error ->
            if (error) {
                Toast.makeText(this, R.string.error_timeout, Toast.LENGTH_SHORT).show()
                viewModel.setErrorTimeout(false)
            }
        }

        viewModel.errorMessageRes.observe(this) { messageRes ->
            messageRes?.let {
                Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                viewModel.consumeErrorMessage()
            }
        }

        viewModel.unsupportedVersion.observe(this) { unsupported ->
            if (unsupported) {
                showUnsupportedVersionDialog()
                viewModel.consumeUnsupportedVersion()
            }
        }

        viewModel.isLoading.observe(this) {
            if (it) {
                connectionBar.visibility = View.VISIBLE
            } else {
                connectionBar.visibility = View.GONE
            }
        }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bootstrapDriverSession(intent)
    }

    private fun removeFeeServiceData() {
        preferences.edit(true) { putString(Constants.CURRENT_SERVICE_ID, null) }
        preferences.edit(true) { remove(Constants.START_TIME) }
        preferences.edit(true) { remove(Constants.MULTIPLIER) }
        preferences.edit(true) { remove(Constants.POINTS) }
    }

    private fun showUnsupportedVersionDialog() {
        unsupportedVersionDialog?.let { dialog ->
            if (dialog.isShowing) {
                return
            }
        }

        unsupportedVersionDialog = showBlockingActionDialog(
            iconRes = R.drawable.ic_file_download_24,
            titleRes = R.string.unsupported_version_title,
            subtitleRes = R.string.unsupported_version_subtitle,
            messageRes = R.string.unsupported_version_message,
            primaryTextRes = R.string.update_app,
            secondaryTextRes = R.string.cancel,
            primaryIconRes = R.drawable.ic_file_download_24,
            secondaryIconRes = R.drawable.cancel_24,
            onPrimaryAction = {
                openPlayStore()
            }
        )

        unsupportedVersionDialog?.setOnDismissListener {
            unsupportedVersionDialog = null
        }
    }

    private fun showDriverLoadFailureDialog() {
        driverLoadFailureDialog?.let { dialog ->
            if (dialog.isShowing) {
                return
            }
        }

        driverLoadFailureDialog = showBlockingActionDialog(
            iconRes = R.drawable.ic_refresh_24,
            titleRes = R.string.driver_profile_error_title,
            subtitleRes = R.string.driver_profile_error_subtitle,
            messageRes = R.string.driver_profile_error_message,
            primaryTextRes = R.string.retry,
            secondaryTextRes = R.string.logout,
            primaryIconRes = R.drawable.ic_refresh_24,
            secondaryIconRes = R.drawable.ic_clear_24,
            onPrimaryAction = {
                bootstrapDriverSession()
            },
            onSecondaryAction = {
                Auth.logOut(this)
            }
        )

        driverLoadFailureDialog?.setOnDismissListener {
            driverLoadFailureDialog = null
        }
    }

    private fun dismissDriverLoadFailureDialog() {
        driverLoadFailureDialog?.dismiss()
        driverLoadFailureDialog = null
    }

    private fun showBlockingActionDialog(
        @DrawableRes iconRes: Int,
        @StringRes titleRes: Int,
        @StringRes subtitleRes: Int,
        @StringRes messageRes: Int,
        @StringRes primaryTextRes: Int,
        @StringRes secondaryTextRes: Int,
        @DrawableRes primaryIconRes: Int,
        @DrawableRes secondaryIconRes: Int,
        onPrimaryAction: () -> Unit,
        onSecondaryAction: () -> Unit = {}
    ): AlertDialog {
        val dialogView = layoutInflater.inflate(R.layout.dialog_blocking_action, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val iconContainer = dialogView.findViewById<MaterialCardView>(R.id.dialogIconContainer)
        val iconView = dialogView.findViewById<ImageView>(R.id.dialogIcon)
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val subtitleView = dialogView.findViewById<TextView>(R.id.dialogSubtitle)
        val messageView = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val primaryButton = dialogView.findViewById<MaterialButton>(R.id.btnPrimary)
        val secondaryButton = dialogView.findViewById<MaterialButton>(R.id.btnSecondary)

        iconContainer.setCardBackgroundColor(getColor(R.color.secondary))
        iconView.setImageResource(iconRes)
        titleView.setText(titleRes)
        subtitleView.setText(subtitleRes)
        messageView.setText(messageRes)
        primaryButton.setText(primaryTextRes)
        primaryButton.setIconResource(primaryIconRes)
        secondaryButton.setText(secondaryTextRes)
        secondaryButton.setIconResource(secondaryIconRes)

        primaryButton.setOnClickListener {
            dialog.dismiss()
            onPrimaryAction()
        }

        secondaryButton.setOnClickListener {
            dialog.dismiss()
            onSecondaryAction()
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }

    private fun openPlayStore() {
        val appPackageName = packageName
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            "market://details?id=$appPackageName".toUri()
        ).apply {
            setPackage("com.android.vending")
        }
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            "https://play.google.com/store/apps/details?id=$appPackageName".toUri()
        )

        try {
            startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(webIntent)
        }
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

    private fun showNetworkLostIndicator() {
        connectionBar.visibility = View.VISIBLE
        // Optional: Show a Toast or Snackbar
        Toast.makeText(this, R.string.network_lost_offline_mode, Toast.LENGTH_SHORT).show()
    }

    private fun hideNetworkLostIndicator() {
        connectionBar.visibility = View.GONE
        Toast.makeText(this, R.string.network_restored, Toast.LENGTH_SHORT).show()
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
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(
                connectionBroadcastReceiver,
                IntentFilter(ConnectionBroadcastReceiver.ACTION_CONNECTION)
            )
        if (ServiceHelper.isServiceRunning(this, LocationService::class.java)) {
            Intent(this, LocationService::class.java).also { intent ->
                bindService(intent, connection, Context.BIND_NOT_FOREGROUND)
            }
        }

        authStateListener = Auth.onAuthChanges { uuid ->
            if (uuid == null) {
                viewModel.handleAuthLost()
                val intent = Intent(this, StartActivity::class.java)
                startActivity(intent)
                finish()
            }
        }
        serviceObserverCoordinator.onActivityStarted()
        networkMonitor.startMonitoring()
    }

    override fun onStop() {
        authStateListener?.let(Auth::removeAuthChanges)
        authStateListener = null
        serviceObserverCoordinator.onActivityStopped()
        super.onStop()
        preferences.edit(true) {
            putString(Constants.CURRENT_SERVICE_ID, viewModel.currentService.value?.id)
        }
        if (mBound) {
            this.unbindService(connection)
            mBound = false
        }
        locationService = null
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationBroadcastReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(connectionBroadcastReceiver)
        networkMonitor.stopMonitoring()
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

    private fun observeDriverLoadState(navView: NavigationView) {
        viewModel.driverLoadState.observe(this) { state ->
            when (state) {
                MainViewModel.DriverLoadState.Idle -> {
                    switchConnect.isEnabled = false
                    serviceObserverCoordinator.onDriverNotLoaded()
                }
                is MainViewModel.DriverLoadState.Loading -> {
                    dismissDriverLoadFailureDialog()
                    driver = null
                    switchConnect.isEnabled = false
                    serviceObserverCoordinator.onDriverNotLoaded()
                    viewModel.stopPresenceObservation()
                }
                is MainViewModel.DriverLoadState.Loaded -> {
                    dismissDriverLoadFailureDialog()
                    handleDriverLoaded(navView, state.driver)
                    serviceObserverCoordinator.onDriverLoaded()
                }
                is MainViewModel.DriverLoadState.Failed -> {
                    handleDriverLoadFailed()
                }
            }
        }
    }

    private fun handleDriverLoaded(navView: NavigationView, loadedDriver: Driver) {
        this.driver = loadedDriver
        if (loadedDriver.device != null) {
            if (this.deviceID != loadedDriver.device!!.id) {
                Auth.logOut(this).addOnCompleteListener { completed ->
                    if (completed.isSuccessful) {
                        Toast.makeText(
                            this,
                            R.string.logout_first,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                return
            }
        } else {
            loadedDriver.id.let { driverId ->
                viewModel.updateDriverDevice(driverId, object : DeviceInterface {
                    override var id = deviceID
                    override var name = deviceName
                }).addOnFailureListener { exception ->
                    Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
        switchConnect.isEnabled = true
        setDrawerHeader(navView)
        viewModel.startPresenceObservation(loadedDriver.id)
    }

    private fun handleDriverLoadFailed() {
        driver = null
        switchConnect.isChecked = false
        switchConnect.isEnabled = false
        switchConnect.setText(R.string.status_disconnected)
        viewModel.handleDriverLoadFailed()
        serviceObserverCoordinator.onDriverNotLoaded()
        viewModel.stopPresenceObservation()
        if (ServiceHelper.isServiceRunning(this, LocationService::class.java)) {
            stopLocationService()
        }
        showDriverLoadFailureDialog()
    }

    private fun bootstrapDriverSession(sourceIntent: Intent = intent) {
        val driverId = resolveDriverId(sourceIntent) ?: return
        dismissDriverLoadFailureDialog()
        viewModel.getDriver(driverId)
        Messaging.init(driverId)
    }

    private fun resolveDriverId(sourceIntent: Intent = intent): String? {
        return sourceIntent.getStringExtra(Constants.DRIVER_ID_EXTRA) ?: Auth.getCurrentUserUUID()
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

    private fun startLocationService(): Boolean {
        if (Utils.isNewerVersion(Build.VERSION_CODES.S) && !Utils.isAppInForeground(this)) {
            return false
        }
        Intent(this, LocationService::class.java).also { intent ->
            intent.putExtra(Driver.DRIVER_KEY, this.driver?.id)
            if (Utils.isNewerVersion(Build.VERSION_CODES.O)) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
            val didBind = this.bindService(intent, connection, BIND_NOT_FOREGROUND)
            if (!didBind) {
                stopService(intent)
            }
            return didBind
        }
    }

    private fun stopLocationService() {
        val stopIntent = Intent(this, LocationService::class.java)
        if (mBound) {
            try {
                this.unbindService(connection)
            } catch (_: IllegalArgumentException) {
            }
            if (locationService != null) {
                val msg: Message = Message.obtain(null, LocationService.STOP_SERVICE_MSG, 0, 0)
                locationService?.send(msg)
            } else {
                stopService(stopIntent)
            }
        } else {
            stopService(stopIntent)
        }
        locationService = null
        mBound = false
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

    override fun onDestroy() {
        dismissDriverLoadFailureDialog()
        viewModel.stopPresenceObservation()
        super.onDestroy()
        networkMonitor.cleanup()
    }

    private fun renderPresenceState(presence: MainViewModel.DriverPresenceState) {
        if (viewModel.driverLoadState.value !is MainViewModel.DriverLoadState.Loaded) {
            lastStartedServiceAttemptId = -1L
            switchConnect.isEnabled = false
            switchConnect.isChecked = false
            switchConnect.setText(R.string.status_disconnected)
            return
        }

        val isConnecting = presence.phase in setOf(
            MainViewModel.DriverPresencePhase.PRECHECKING,
            MainViewModel.DriverPresencePhase.WAITING_FOR_BIND,
            MainViewModel.DriverPresencePhase.WAITING_FOR_LOCATION,
            MainViewModel.DriverPresencePhase.WRITING_PRESENCE,
            MainViewModel.DriverPresencePhase.WAITING_FOR_PRESENCE_ACK,
            MainViewModel.DriverPresencePhase.DISCONNECTING
        )
        val isBootstrappingLocation = presence.phase in setOf(
            MainViewModel.DriverPresencePhase.WAITING_FOR_BIND,
            MainViewModel.DriverPresencePhase.WAITING_FOR_LOCATION
        )

        switchConnect.isEnabled = !isConnecting
        switchConnect.isChecked = presence.desiredOnline

        when {
            presence.actualOnline -> {
                switchConnect.setText(R.string.status_connected)
            }
            presence.desiredOnline && presence.phase == MainViewModel.DriverPresencePhase.RECONNECTING -> {
                switchConnect.setText(R.string.status_reconnecting)
            }
            isConnecting -> {
                switchConnect.setText(R.string.status_connecting)
            }
            else -> {
                switchConnect.setText(R.string.status_disconnected)
            }
        }

        if (
            presence.phase == MainViewModel.DriverPresencePhase.WAITING_FOR_BIND &&
            lastStartedServiceAttemptId != presence.attemptId
        ) {
            lastStartedServiceAttemptId = presence.attemptId
            if (!startLocationService()) {
                viewModel.onLocationServiceBindFailed()
            }
        }

        if (
            presence.desiredOnline &&
            !isBootstrappingLocation &&
            !mBound &&
            !ServiceHelper.isServiceRunning(this, LocationService::class.java) &&
            LocationHandler.checkPermissions(this)
        ) {
            startLocationService()
        }

        if (
            !presence.desiredOnline &&
            ServiceHelper.isServiceRunning(this, LocationService::class.java)
        ) {
            stopLocationService()
        }
    }
}
