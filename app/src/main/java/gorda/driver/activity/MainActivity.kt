package gorda.driver.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.CompoundButton
import android.widget.Switch
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import gorda.driver.R
import gorda.driver.databinding.ActivityMainBinding
import gorda.driver.models.Driver
import gorda.driver.repositories.DriverRepository
import gorda.driver.services.firebase.Auth
import gorda.driver.services.firebase.FirebaseInitializeApp

@SuppressLint("UseSwitchCompatOrMaterialCode")
class MainActivity : AppCompatActivity() {

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
                    DriverRepository.getDriver(s, this.setDriver)
                    DriverRepository.isConnected(s) {
                        this.switchConnect.isChecked = it
                        if (it) {
                            this.switchConnect.setText(R.string.status_connected)
                        } else {
                            this.switchConnect.setText(R.string.status_disconnected)
                        }
                    }
                }
            }
        }
    }

    private fun onSignInResult(result: FirebaseAuthUIAuthenticationResult) {
        if (result.resultCode == RESULT_OK) {
            // Successfully signed in
            val user = Auth.getCurrentUser()
            user?.uid.let { s ->
                if (s != null) {
                    Log.d("debug", "setting the driver $s")
                    DriverRepository.getDriver(s, this.setDriver)
                }
            }
            // ...
        } else {
            // Sign in failed. If response is null the user canceled the
            // sign-in flow using the back button. Otherwise check
            // response.getError().getErrorCode() and handle the error.
            // ...
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
                }
                .addOnCanceledListener {
                    switch.isChecked = false
                }
        } else {
            this.driver.disconnect()
                .addOnSuccessListener {
                    switch.setText(R.string.status_disconnected)
                }
                .addOnFailureListener {
                    switch.isChecked = true
                }
        }
    }
}