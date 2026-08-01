package gorda.driver.ui.service.current

import gorda.driver.interfaces.RideFees
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CurrentServiceViewModel.shouldShowSelfServiceCancel] gates the windowed cancel button for
 * self-service trips (add-driver-self-service design D6, task 4.5): normal assigned services must
 * never show it, and a self-service trip only shows it while within the configurable cancel
 * window delivered through the ride-fees snapshot.
 */
class CurrentServiceViewModelTest {

    @Test
    fun `within window shows cancel`() {
        val result = CurrentServiceViewModel.shouldShowSelfServiceCancel(
            isSelfService = true,
            isCancelable = true,
            startedAtEpochSeconds = 1_000L,
            nowEpochSeconds = 1_060L,
            cancelWindowSeconds = 120
        )

        assertTrue(result)
    }

    @Test
    fun `exactly at the window boundary still shows cancel`() {
        val result = CurrentServiceViewModel.shouldShowSelfServiceCancel(
            isSelfService = true,
            isCancelable = true,
            startedAtEpochSeconds = 1_000L,
            nowEpochSeconds = 1_120L,
            cancelWindowSeconds = 120
        )

        assertTrue(result)
    }

    @Test
    fun `past the window hides cancel`() {
        val result = CurrentServiceViewModel.shouldShowSelfServiceCancel(
            isSelfService = true,
            isCancelable = true,
            startedAtEpochSeconds = 1_000L,
            nowEpochSeconds = 1_121L,
            cancelWindowSeconds = 120
        )

        assertFalse(result)
    }

    @Test
    fun `non self-service never shows cancel even well within the window`() {
        val result = CurrentServiceViewModel.shouldShowSelfServiceCancel(
            isSelfService = false,
            isCancelable = true,
            startedAtEpochSeconds = 1_000L,
            nowEpochSeconds = 1_000L,
            cancelWindowSeconds = 120
        )

        assertFalse(result)
    }

    @Test
    fun `locally disqualified trip hides cancel regardless of the window`() {
        val result = CurrentServiceViewModel.shouldShowSelfServiceCancel(
            isSelfService = true,
            isCancelable = false,
            startedAtEpochSeconds = 1_000L,
            nowEpochSeconds = 1_000L,
            cancelWindowSeconds = 120
        )

        assertFalse(result)
    }

    @Test
    fun `missing start time hides cancel`() {
        val result = CurrentServiceViewModel.shouldShowSelfServiceCancel(
            isSelfService = true,
            isCancelable = true,
            startedAtEpochSeconds = null,
            nowEpochSeconds = 1_000L,
            cancelWindowSeconds = 120
        )

        assertFalse(result)
    }

    @Test
    fun `default ride-fees snapshot window is honored at its own boundary`() {
        val defaultWindow = RideFees().selfServiceCancelWindow

        val withinDefault = CurrentServiceViewModel.shouldShowSelfServiceCancel(
            isSelfService = true,
            isCancelable = true,
            startedAtEpochSeconds = 0L,
            nowEpochSeconds = defaultWindow.toLong(),
            cancelWindowSeconds = defaultWindow
        )
        val pastDefault = CurrentServiceViewModel.shouldShowSelfServiceCancel(
            isSelfService = true,
            isCancelable = true,
            startedAtEpochSeconds = 0L,
            nowEpochSeconds = defaultWindow.toLong() + 1,
            cancelWindowSeconds = defaultWindow
        )

        assertTrue(withinDefault)
        assertFalse(pastDefault)
    }
}
