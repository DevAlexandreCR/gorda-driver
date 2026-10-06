package gorda.driver.ui.service.apply

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ApplyViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun showCheckingAssignmentSetsCheckingAssignmentState() {
        val viewModel = ApplyViewModel()

        viewModel.showCheckingAssignment()

        assertEquals(ApplyViewModel.ApplyUiState.CheckingAssignment, viewModel.uiState.value)
    }

    @Test
    fun isAwaitingAssignmentOutcomeIsTrueWhileCheckingAssignment() {
        val viewModel = ApplyViewModel()

        viewModel.showCheckingAssignment()

        assertTrue(viewModel.isAwaitingAssignmentOutcome())
    }

    @Test
    fun isAwaitingAssignmentOutcomeIsTrueWhileAssignedPreparingService() {
        val viewModel = ApplyViewModel()

        viewModel.showAssignedPreparingService()

        assertTrue(viewModel.isAwaitingAssignmentOutcome())
    }

    @Test
    fun isAwaitingAssignmentOutcomeIsFalseForOtherStates() {
        val viewModel = ApplyViewModel()

        viewModel.showAppliedWaitingAssignment("Downtown")

        assertFalse(viewModel.isAwaitingAssignmentOutcome())
    }
}
