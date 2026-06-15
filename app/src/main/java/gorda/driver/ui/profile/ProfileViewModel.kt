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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _driver = MutableLiveData<Driver>()

    val driver: LiveData<Driver> = savedStateHandle.getLiveData(Driver.TAG)

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _vehicleSelectInFlight = MutableLiveData(false)
    val vehicleSelectInFlight: LiveData<Boolean> = _vehicleSelectInFlight

    private var vehicleDataSource: ProfileVehicleDataSource = RetrofitProfileVehicleDataSource()
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO
    private var mainDispatcher: CoroutineDispatcher = Dispatchers.Main

    internal constructor(
        savedStateHandle: SavedStateHandle,
        vehicleDataSource: ProfileVehicleDataSource,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        mainDispatcher: CoroutineDispatcher = Dispatchers.Main
    ) : this(savedStateHandle) {
        this.vehicleDataSource = vehicleDataSource
        this.ioDispatcher = ioDispatcher
        this.mainDispatcher = mainDispatcher
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
        viewModelScope.launch(ioDispatcher) {
            try {
                vehicleDataSource.setSelectedVehicle(vehicleId)
                val updatedVehicles = runCatching { vehicleDataSource.getVehicles() }.getOrNull()
                withContext(mainDispatcher) {
                    val current = savedStateHandle.get<Driver>(Driver.TAG)
                    if (current != null) {
                        val updatedDriver = if (updatedVehicles != null) {
                            current.withVehicleRoster(updatedVehicles, fallbackSelectedVehicleId = vehicleId)
                        } else {
                            current.withSelectedVehicle(vehicleId)
                        }
                        _driver.value = updatedDriver
                        savedStateHandle[Driver.TAG] = updatedDriver
                    }
                    _vehicleSelectInFlight.value = false
                }
            } catch (e: DriverAppRequestException) {
                logError("selectVehicle failed: code=${e.code} message=${e.responseMessage}")
                withContext(mainDispatcher) {
                    _errorMessage.value = ERROR_SELECT
                    _vehicleSelectInFlight.value = false
                }
            } catch (e: Exception) {
                logError("selectVehicle exception: ${e.message}")
                withContext(mainDispatcher) {
                    _errorMessage.value = ERROR_SELECT
                    _vehicleSelectInFlight.value = false
                }
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private fun refreshVehicleRoster() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val vehicles = vehicleDataSource.getVehicles()
                withContext(mainDispatcher) {
                    val current = savedStateHandle.get<Driver>(Driver.TAG) ?: return@withContext
                    val updatedDriver = current.withVehicleRoster(vehicles)
                    _driver.value = updatedDriver
                    savedStateHandle[Driver.TAG] = updatedDriver
                }
            } catch (e: Exception) {
                logWarn("refreshVehicleRoster failed: ${e.message}")
            }
        }
    }

    private fun logError(message: String) {
        runCatching { Log.e(TAG, message) }
    }

    private fun logWarn(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private fun Driver.withSelectedVehicle(vehicleId: String): Driver {
        val updatedRoster = roster.map { vehicle ->
            vehicle.copy(is_selected = vehicle.id == vehicleId)
        }

        return copy(
            roster = updatedRoster,
            selected_vehicle = updatedRoster.firstOrNull { it.id == vehicleId }
                ?: selected_vehicle
        )
    }

    private fun Driver.withVehicleRoster(
        vehicles: List<Vehicle>,
        fallbackSelectedVehicleId: String? = null
    ): Driver {
        val selectedId = vehicles.firstOrNull { it.is_selected }?.id
            ?: fallbackSelectedVehicleId
            ?: selected_vehicle?.id

        val normalizedRoster = vehicles.map { vehicle ->
            if (selectedId == null || vehicle.is_selected == (vehicle.id == selectedId)) {
                vehicle
            } else {
                vehicle.copy(is_selected = vehicle.id == selectedId)
            }
        }

        return copy(
            roster = normalizedRoster,
            selected_vehicle = normalizedRoster.firstOrNull { it.id == selectedId }
                ?: selected_vehicle
        )
    }

    companion object {
        private const val TAG = "ProfileViewModel"
        const val ERROR_SELECT = "select_error"
    }
}

internal interface ProfileVehicleDataSource {
    suspend fun setSelectedVehicle(vehicleId: String)
    suspend fun getVehicles(): List<Vehicle>
}

internal class RetrofitProfileVehicleDataSource(
    private val apiService: MasterDataApiService = MasterDataRetrofit
        .getRetrofit()
        .create(MasterDataApiService::class.java)
) : ProfileVehicleDataSource {
    override suspend fun setSelectedVehicle(vehicleId: String) {
        DriverAppRequestRunner.execute("driver-app/me/selected-vehicle") { authorization ->
            apiService.setSelectedVehicle(authorization, SetSelectedVehicleRequest(vehicleId))
        }
    }

    override suspend fun getVehicles(): List<Vehicle> {
        val response = DriverAppRequestRunner.execute("driver-app/me/vehicles") { authorization ->
            apiService.getVehicles(authorization)
        }

        return response.body()?.data?.vehicles.orEmpty().map { it.toVehicle() }
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
}
