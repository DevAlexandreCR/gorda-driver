package gorda.driver.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [HomeViewModel.isSelfServiceEntryVisible] gates the "start own trip" entry action
 * (add-driver-self-service design D4, task 4.2): the app shows it only while the driver session
 * is both connected and eligible per `canGoOnline`. The API remains the authoritative check on
 * creation; this is purely the app-side visibility rule.
 */
class HomeViewModelTest {

    @Test
    fun `connected and eligible shows the entry`() {
        assertTrue(HomeViewModel.isSelfServiceEntryVisible(connected = true, canGoOnline = true))
    }

    @Test
    fun `disconnected hides the entry even if otherwise eligible`() {
        assertFalse(HomeViewModel.isSelfServiceEntryVisible(connected = false, canGoOnline = true))
    }

    @Test
    fun `ineligible hides the entry even if connected`() {
        assertFalse(HomeViewModel.isSelfServiceEntryVisible(connected = true, canGoOnline = false))
    }

    @Test
    fun `disconnected and ineligible hides the entry`() {
        assertFalse(HomeViewModel.isSelfServiceEntryVisible(connected = false, canGoOnline = false))
    }
}
