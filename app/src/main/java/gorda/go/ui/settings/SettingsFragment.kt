package gorda.go.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import gorda.go.R
import gorda.go.utils.Constants
import gorda.go.utils.Utils


class SettingsFragment : PreferenceFragmentCompat() {

    private lateinit var resultLauncher: ActivityResultLauncher<Intent>

    companion object {
        const val PERMISSIONS_STORAGE = 200
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK && it.data != null) {
                val ringtone = it.data!!.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                val preSelectedRingTone = getRingtonePreferenceValue()
                if (ringtone != null) {
                    setRingtonePreferenceValue(ringtone.toString())
                } else {
                    setRingtonePreferenceValue(preSelectedRingTone)
                }
            }
        }
    }

    override fun onDestroy() {
        resultLauncher.unregister()
        super.onDestroy()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == Constants.NOTIFICATION_RINGTONE) {
            val defaultRingTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            preference.setDefaultValue(defaultRingTone.toString())
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)

            resultLauncher.launch(intent)
            return true
        } else {
            return super.onPreferenceTreeClick(preference)
        }
    }

    override fun onStart() {
        super.onStart()
        checkPermissions()
    }

    private fun checkPermissions() {
        var permissions = arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (Utils.isNewerVersion(Build.VERSION_CODES.TIRAMISU)) {
            permissions = permissions.plus(Manifest.permission.READ_MEDIA_AUDIO)
        }

        if (Utils.isNewerVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            permissions += Manifest.permission.FOREGROUND_SERVICE_LOCATION
        }
        permissions.iterator().forEach { permission ->
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    permission
                ) == PackageManager.PERMISSION_DENIED) {
                ActivityCompat.requestPermissions(
                    requireActivity(), permissions, PERMISSIONS_STORAGE
                )
            }
        }
    }

    private fun getRingtonePreferenceValue(): String {
        val defaultRingTone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        return if (preferenceManager.sharedPreferences != null) {
            preferenceManager.sharedPreferences!!.getString(
                Constants.NOTIFICATION_RINGTONE,
                defaultRingTone.toString()
            ) as String
        } else {
            defaultRingTone.toString()
        }
    }

    private fun setRingtonePreferenceValue(ringTone: String) {
        preferenceManager.sharedPreferences?.edit()
            ?.putString(Constants.NOTIFICATION_RINGTONE, ringTone)
            ?.apply()
    }
}