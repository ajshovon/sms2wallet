package me.shovon.sms2wallet.presentation.screens.dashboard

import me.shovon.sms2wallet.presentation.model.DashboardUiState
import me.shovon.sms2wallet.presentation.model.QuickAddUiState
import me.shovon.sms2wallet.presentation.model.RateLimitUiState
import me.shovon.sms2wallet.presentation.model.TokenHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardUiStateTest {

    @Test
    fun `default DashboardUiState is not refreshing and has safe defaults`() {
        val state = DashboardUiState()
        assertFalse(state.isRefreshing)
        assertFalse(state.isLoading)
        assertEquals(0, state.pushedToday)
        assertEquals(0, state.pushedThisWeek)
        assertEquals(0, state.pendingReviewCount)
        assertEquals(TokenHealth.UNKNOWN, state.tokenHealth)
    }

    @Test
    fun `isRefreshing reflects pull to refresh state`() {
        val refreshingState = DashboardUiState(isRefreshing = true)
        assertTrue(refreshingState.isRefreshing)

        val idleState = refreshingState.copy(isRefreshing = false)
        assertFalse(idleState.isRefreshing)
    }

    @Test
    fun `rate limit fraction calculates accurately`() {
        val budget = RateLimitUiState(used = 150, limit = 300)
        assertEquals(0.5f, budget.fraction, 0.001f)

        val emptyBudget = RateLimitUiState(used = 0, limit = 0)
        assertEquals(0.0f, emptyBudget.fraction, 0.001f)
    }

    @Test
    fun `quick add submission check`() {
        val emptyInput = QuickAddUiState(input = "  ", isParsing = false)
        assertFalse(emptyInput.canSubmit)

        val parsingInput = QuickAddUiState(input = "Coffee 50", isParsing = true)
        assertFalse(parsingInput.canSubmit)

        val readyInput = QuickAddUiState(input = "Coffee 50", isParsing = false)
        assertTrue(readyInput.canSubmit)
    }
}
