package gorda.driver.ui.profile

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import gorda.driver.interfaces.Vehicle
import gorda.driver.models.Driver
import gorda.driver.services.retrofit.DriverAppRequestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun selectVehicleSuccessDoesNotEmitErrorAndUpdatesSelectedVehicle() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val backendRoster = listOf(
            Vehicle(
                id = "vehicle-1",
                plate = "AAA111",
                is_selectable = true,
                is_selected = false
            ),
            Vehicle(
                id = "vehicle-2",
                plate = "BBB222",
                is_selectable = true,
                is_selected = true
            )
        )
        val viewModel = ProfileViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    Driver.TAG to driver(
                        roster = listOf(
                            Vehicle(
                                id = "vehicle-1",
                                plate = "AAA111",
                                is_selectable = true,
                                is_selected = true
                            ),
                            Vehicle(
                                id = "vehicle-2",
                                plate = "BBB222",
                                is_selectable = true,
                                is_selected = false
                            )
                        ),
                        selectedVehicleId = "vehicle-1"
                    )
                )
            ),
            vehicleDataSource = FakeProfileVehicleDataSource(
                vehicles = backendRoster
            ),
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher
        )

        viewModel.selectVehicle("vehicle-2")
        advanceUntilIdle()

        val updatedDriver = viewModel.driver.value
        assertNull(viewModel.errorMessage.value)
        assertFalse(viewModel.vehicleSelectInFlight.value ?: true)
        assertEquals("vehicle-2", updatedDriver?.selected_vehicle?.id)
        assertEquals(
            listOf(false, true),
            updatedDriver?.roster?.map { it.is_selected }
        )
        assertEquals(1, updatedDriver?.roster?.count { it.id == "vehicle-2" && it.is_selected })
    }

    @Test
    fun selectVehicleFailureEmitsSelectionError() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val viewModel = ProfileViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(
                    Driver.TAG to driver(
                        roster = listOf(
                            Vehicle(
                                id = "vehicle-1",
                                plate = "AAA111",
                                is_selectable = true,
                                is_selected = true
                            ),
                            Vehicle(
                                id = "vehicle-2",
                                plate = "BBB222",
                                is_selectable = true,
                                is_selected = false
                            )
                        ),
                        selectedVehicleId = "vehicle-1"
                    )
                )
            ),
            vehicleDataSource = FakeProfileVehicleDataSource(
                selectVehicleException = DriverAppRequestException(
                    endpoint = "driver-app/me/selected-vehicle",
                    baseUrl = "https://example.test",
                    code = 422,
                    responseMessage = "vehicle_not_eligible",
                    hasCurrentUser = true
                )
            ),
            ioDispatcher = dispatcher,
            mainDispatcher = dispatcher
        )

        viewModel.selectVehicle("vehicle-2")
        advanceUntilIdle()

        assertEquals(ProfileViewModel.ERROR_SELECT, viewModel.errorMessage.value)
        assertEquals("vehicle-1", viewModel.driver.value?.selected_vehicle?.id)
        assertTrue(viewModel.driver.value?.roster?.first { it.id == "vehicle-1" }?.is_selected == true)
        assertFalse(viewModel.vehicleSelectInFlight.value ?: true)
    }

    private fun driver(
        roster: List<Vehicle>,
        selectedVehicleId: String
    ): Driver {
        return Driver(
            id = "driver-1",
            name = "Driver",
            email = "driver@example.com",
            phone = "3000000000",
            roster = roster,
            selected_vehicle = roster.first { it.id == selectedVehicleId }
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeProfileVehicleDataSource(
    private val vehicles: List<Vehicle> = emptyList(),
    private val selectVehicleException: Exception? = null
) : ProfileVehicleDataSource {
    override suspend fun setSelectedVehicle(vehicleId: String) {
        selectVehicleException?.let { throw it }
    }

    override suspend fun getVehicles(): List<Vehicle> = vehicles
}
