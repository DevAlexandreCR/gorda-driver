package gorda.driver.ui.profile

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import gorda.driver.interfaces.Vehicle
import gorda.driver.interfaces.VehicleColor
import gorda.driver.models.Driver
import gorda.driver.services.masterData.MasterDataApiService
import gorda.driver.services.masterData.RosterVehicle
import gorda.driver.services.masterData.SetSelectedVehicleRequest
import gorda.driver.services.retrofit.DriverAppRequestException
import gorda.driver.services.retrofit.DriverAppRequestRunner
import gorda.driver.services.retrofit.MasterDataRetrofit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _driver = MutableLiveData<Driver>()

    val driver: LiveData<Driver> = savedStateHandle.getLiveData(Driver.TAG)

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _vehicleSelectInFlight = MutableLiveData(false)
    val vehicleSelectInFlight: LiveData<Boolean> = _vehicleSelectInFlight

    private val apiService: MasterDataApiService by lazy {
        MasterDataRetrofit.getRetrofit().create(MasterDataApiService::class.java)
    }

    fun setDriver(driver: Driver) {
        val current = savedStateHandle.get<Driver>(Driver.TAG)
        _driver.postValue(driver)
        savedStateHandle[Driver.TAG] = driver
        if (current?.id != driver.id) {
            refreshVehicleRoster()
        }
    }

    fun selectVehicle(vehicleId: String) {
        _vehicleSelectInFlight.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DriverAppRequestRunner.execute("driver-app/me/selected-vehicle") { authorization ->
                    apiService.setSelectedVehicle(authorization, SetSelectedVehicleRequest(vehicleId))
                }
                val current = savedStateHandle.get<Driver>(Driver.TAG)
                if (current != null) {
                    val updatedRoster = current.roster.map { v ->
                        v.copy(is_selected = v.id == vehicleId)
                    }
                    val updatedDriver = current.copy(
                        roster = updatedRoster,
                        selected_vehicle = updatedRoster.firstOrNull { it.id == vehicleId }
                            ?: current.selected_vehicle
                    )
                    savedStateHandle[Driver.TAG] = updatedDriver
                }
                _vehicleSelectInFlight.postValue(false)
            } catch (e: DriverAppRequestException) {
                Log.e(TAG, "selectVehicle failed: code=${e.code} message=${e.responseMessage}")
                _errorMessage.postValue(ERROR_SELECT)
                _vehicleSelectInFlight.postValue(false)
            } catch (e: Exception) {
                Log.e(TAG, "selectVehicle exception: ${e.message}")
                _errorMessage.postValue(ERROR_SELECT)
                _vehicleSelectInFlight.postValue(false)
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private fun refreshVehicleRoster() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = DriverAppRequestRunner.execute("driver-app/me/vehicles") { authorization ->
                    apiService.getVehicles(authorization)
                }
                val vehicles = response.body()?.data?.vehicles.orEmpty().map { it.toVehicle() }
                val current = savedStateHandle.get<Driver>(Driver.TAG) ?: return@launch
                val selectedVehicle = vehicles.firstOrNull { it.is_selected }
                val updatedDriver = current.copy(
                    roster = vehicles,
                    selected_vehicle = selectedVehicle
                )
                _driver.postValue(updatedDriver)
                savedStateHandle[Driver.TAG] = updatedDriver
            } catch (e: Exception) {
                Log.w(TAG, "refreshVehicleRoster failed: ${e.message}")
            }
        }
    }

    private fun RosterVehicle.toVehicle(): Vehicle {
        return Vehicle(
            id = id,
            brand = brand.orEmpty(),
            model = model.orEmpty(),
            plate = plate,
            color = color?.let { VehicleColor(hex = it.hex, name = it.name) },
            is_selectable = is_selectable,
            is_selected = is_selected
        )
    }

    companion object {
        private const val TAG = "ProfileViewModel"
        const val ERROR_SELECT = "select_error"
    }
}
