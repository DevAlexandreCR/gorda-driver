package gorda.driver.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HomeViewModel.isSelfServiceEntryVisible] gates the "start own trip" entry action
 * (add-driver-self-service design D4, task 4.2; fix-driver-fee-service-zombie-ticker design D6,
 * task 5.1): the app shows it only while the driver session is connected, eligible per
 * `canGoOnline`, and has no active trip. [HomeViewModel.resolveSelfServiceStart] gates the start
 * action itself on the same "active trip" condition. The API remains the authoritative check on
 * creation; these are purely the app-side rules.
 */
class HomeViewModelTest {

    @Test
    fun `connected and eligible with no active trip shows the entry`() {
        assertTrue(
            HomeViewModel.isSelfServiceEntryVisible(
                connected = true,
                canGoOnline = true,
                hasActiveTrip = false
            )
        )
    }

    @Test
    fun `disconnected hides the entry even if otherwise eligible`() {
        assertFalse(
            HomeViewModel.isSelfServiceEntryVisible(
                connected = false,
                canGoOnline = true,
                hasActiveTrip = false
            )
        )
    }

    @Test
    fun `ineligible hides the entry even if connected`() {
        assertFalse(
            HomeViewModel.isSelfServiceEntryVisible(
                connected = true,
                canGoOnline = false,
                hasActiveTrip = false
            )
        )
    }

    @Test
    fun `disconnected and ineligible hides the entry`() {
        assertFalse(
            HomeViewModel.isSelfServiceEntryVisible(
                connected = false,
                canGoOnline = false,
                hasActiveTrip = false
            )
        )
    }

    @Test
    fun `active trip hides the entry even if connected and eligible`() {
        assertFalse(
            HomeViewModel.isSelfServiceEntryVisible(
                connected = true,
                canGoOnline = true,
                hasActiveTrip = true
            )
        )
    }

    @Test
    fun `active current service hides the entry and blocks the start`() {
        assertFalse(
            HomeViewModel.isSelfServiceEntryVisible(
                connected = true,
                canGoOnline = true,
                hasActiveTrip = true
            )
        )
        assertEquals(
            HomeViewModel.SelfServiceStartResolution.BLOCK_ACTIVE_TRIP,
            HomeViewModel.resolveSelfServiceStart(
                hasCurrentService = true,
                hasPendingSelfServiceTrip = false
            )
        )
    }

    @Test
    fun `pending unsynced self trip hides the entry and blocks the start`() {
        assertFalse(
            HomeViewModel.isSelfServiceEntryVisible(
                connected = true,
                canGoOnline = true,
                hasActiveTrip = true
            )
        )
        assertEquals(
            HomeViewModel.SelfServiceStartResolution.BLOCK_ACTIVE_TRIP,
            HomeViewModel.resolveSelfServiceStart(
                hasCurrentService = false,
                hasPendingSelfServiceTrip = true
            )
        )
    }

    @Test
    fun `no active trip shows the entry and allows the start`() {
        assertTrue(
            HomeViewModel.isSelfServiceEntryVisible(
                connected = true,
                canGoOnline = true,
                hasActiveTrip = false
            )
        )
        assertEquals(
            HomeViewModel.SelfServiceStartResolution.ALLOW,
            HomeViewModel.resolveSelfServiceStart(
                hasCurrentService = false,
                hasPendingSelfServiceTrip = false
            )
        )
    }
}
