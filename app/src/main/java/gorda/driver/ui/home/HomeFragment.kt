package gorda.driver.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.R
import gorda.driver.databinding.FragmentHomeBinding
import gorda.driver.interfaces.LocType
import gorda.driver.models.Service
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.ServiceAdapter
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.dataclasses.ServiceUpdates
import gorda.driver.utils.Constants
import gorda.driver.utils.Utils

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()
    private var location: Location? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var alertReceiver: BroadcastReceiver
    private lateinit var preferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel: HomeViewModel by viewModels()

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        this.recyclerView = binding.listServices
        val showMapFromService: (location: LocType) -> Unit = { location ->
            mainViewModel.setServiceUpdateStartLocation(location)
            findNavController().navigate(R.id.nav_map)
        }
        val apply: (service: Service, location: LocType) -> Unit = { service, location ->
            mainViewModel.setServiceUpdateApply(service)
            mainViewModel.setServiceUpdateStartLocation(location)
            val bundle = bundleOf("service" to service)
            findNavController().navigate(R.id.nav_apply, bundle)
        }
        val serviceAdapter = ServiceAdapter(requireContext(), showMapFromService, apply)
        this.recyclerView.adapter = serviceAdapter
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = getString(it)
        }

        homeViewModel.serviceList.observe(viewLifecycleOwner) { updates ->
            when (updates) {
                is ServiceUpdates.SetList -> {
                    location?.let { loc ->
                        updates.services.sortWith(compareBy { service ->
                            val location = Location("last")
                            location.latitude = service.start_loc.lat
                            location.longitude = service.start_loc.lng

                            loc.distanceTo(location).toInt()
                        })
                    }
                    serviceAdapter.submitList(updates.services)
                }
                is ServiceUpdates.StopListen -> {
                    serviceAdapter.submitList(mutableListOf())
                }
                else -> {}
            }
        }

        mainViewModel.lastLocation.observe(viewLifecycleOwner) {
            when (it) {
                is LocationUpdates.LastLocation -> {
                    location = it.location
                    serviceAdapter.lastLocation = it.location
                    serviceAdapter.notifyDataSetChanged()
                }
            }
        }

        mainViewModel.isNetWorkConnected.observe(viewLifecycleOwner) {
            if (it) {
                this.recyclerView.visibility = View.VISIBLE
            } else {
                this.recyclerView.visibility = View.GONE
            }
        }

        mainViewModel.driverStatus.observe(viewLifecycleOwner) {
            when (it) {
                is DriverUpdates.IsConnected -> {
                    if (it.connected) {
                        this.recyclerView.visibility = View.VISIBLE
                        homeViewModel.startListenServices()
                    } else {
                        homeViewModel.stopListenServices()
                        this.recyclerView.visibility = View.GONE
                    }
                }
                else -> {}
            }
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        alertReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                showAlerts()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (Utils.isNewerVersion(Build.VERSION_CODES.TIRAMISU)) {
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                alertReceiver,
                IntentFilter(Constants.ALERT_ACTION)
            )
        }
        showAlerts()
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(alertReceiver)
    }

    private fun showAlerts() {
        val notificationsJson = preferences.getString(Constants.ALERT_ACTION, "[]")
        val notificationsArray = try {
            org.json.JSONArray(notificationsJson)
        } catch (e: Exception) {
            org.json.JSONArray()
        }
        val now = System.currentTimeMillis()
        val validNotifications = org.json.JSONArray()
        val container = view?.findViewById<LinearLayout>(R.id.alerts_container)
        container?.removeAllViews()
        // Show all valid notifications, most recent first
        val validList = mutableListOf<org.json.JSONObject>()
        for (i in 0 until notificationsArray.length()) {
            val obj = notificationsArray.optJSONObject(i)
            val duration = obj?.optString("duration")?.toLongOrNull() ?: 15L
            val timestamp = obj?.optLong("timestamp") ?: System.currentTimeMillis()
            val minutesPassed = (now - timestamp) / 60000L
            if (minutesPassed <= duration) {
                validList.add(obj)
            }
        }
        // Sort by timestamp descending (most recent first)
        validList.sortByDescending { it.optLong("timestamp") }
        for (obj in validList) {
            val title = obj.optString("title", getString(R.string.app_name))
            val body = obj.optString("body", "")
            val alertView = layoutInflater.inflate(R.layout.custom_alert, container, false)
            alertView.findViewById<TextView>(R.id.alert_title).text = title
            alertView.findViewById<TextView>(R.id.alert_body).text = body
            val closeBtn = alertView.findViewById<View>(R.id.alert_close)
            closeBtn.setOnClickListener {
                removeNotificationFromPrefs(obj)
                container?.removeView(alertView)
            }
            container?.addView(alertView)
            validNotifications.put(obj)
        }
        preferences.edit(true) { putString(Constants.ALERT_ACTION, validNotifications.toString()) }
    }

    private fun removeNotificationFromPrefs(notification: org.json.JSONObject) {
        val notificationsJson = preferences.getString(Constants.ALERT_ACTION, "[]")
        val notificationsArray = try {
            org.json.JSONArray(notificationsJson)
        } catch (e: Exception) {
            org.json.JSONArray()
        }
        val newArray = org.json.JSONArray()
        for (i in 0 until notificationsArray.length()) {
            val obj = notificationsArray.optJSONObject(i)
            if (obj != null && obj.toString() != notification.toString()) {
                newArray.put(obj)
            }
        }
        preferences.edit(true) { putString(Constants.ALERT_ACTION, newArray.toString()) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

