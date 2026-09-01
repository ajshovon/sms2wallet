package me.shovon.sms2wallet.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive, plain-JUnit tests for [PushStateTransitions]. The single invariant that matters
 * most in this app - a transaction is never sent to the Wallet API twice - is proven here for
 * every reachable (state, outcome) pair rather than spot-checked, since the Wallet API has no
 * idempotency key to fall back on if the state machine gets this wrong.
 */
class PushStateMachineTest {

    private val allOutcomes: List<SendOutcome> = listOf(
        SendOutcome.Success(recordId = "wallet-record-synthetic-1"),
        SendOutcome.ValidationRejected(msg = "synthetic validation error"),
        SendOutcome.Retryable(msg = "synthetic network error"),
        SendOutcome.Ambiguous(msg = "synthetic timeout")
    )

    @Test
    fun `isSendable is true only for QUEUED`() {
        for (state in PushState.entries) {
            val expected = state == PushState.QUEUED
            assertEquals("isSendable($state)", expected, PushStateTransitions.isSendable(state))
        }
    }

    @Test
    fun `no outcome from any state ever produces QUEUED`() {
        for (state in PushState.entries) {
            for (outcome in allOutcomes) {
                val result = PushStateTransitions.next(state, outcome)
                assertFalse(
                    "next($state, $outcome) produced QUEUED, which would make an already-processed row sendable again",
                    result == PushState.QUEUED
                )
            }
        }
    }

    @Test
    fun `PUSHED is a hard terminal state regardless of outcome`() {
        for (outcome in allOutcomes) {
            val result = PushStateTransitions.next(PushState.PUSHED, outcome)
            assertEquals(
                "next(PUSHED, $outcome) must stay PUSHED so an already-pushed row can never become sendable again",
                PushState.PUSHED,
                result
            )
            assertFalse(PushStateTransitions.isSendable(result))
        }
    }

    @Test
    fun `once PUSHED is reached it can never become sendable via any subsequent outcome`() {
        // Simulate every possible sequence of two outcomes applied after a Success.
        val afterSuccess = PushStateTransitions.next(PushState.SENDING, SendOutcome.Success("wallet-record-synthetic-2"))
        assertEquals(PushState.PUSHED, afterSuccess)

        for (secondOutcome in allOutcomes) {
            val result = PushStateTransitions.next(afterSuccess, secondOutcome)
            assertEquals(PushState.PUSHED, result)
            assertFalse(PushStateTransitions.isSendable(result))
        }
    }

    @Test
    fun `Ambiguous always lands in NEEDS_VERIFY, never QUEUED, except from the terminal PUSHED state`() {
        for (state in PushState.entries) {
            val result = PushStateTransitions.next(state, SendOutcome.Ambiguous("synthetic timeout"))
            assertFalse("Ambiguous from $state must never yield QUEUED", result == PushState.QUEUED)
            if (state == PushState.PUSHED) {
                assertEquals(PushState.PUSHED, result)
            } else {
                assertEquals("Ambiguous from $state", PushState.NEEDS_VERIFY, result)
            }
        }
    }

    @Test
    fun `Success always lands in PUSHED regardless of starting state`() {
        for (state in PushState.entries) {
            val result = PushStateTransitions.next(state, SendOutcome.Success("wallet-record-synthetic-3"))
            assertEquals(PushState.PUSHED, result)
        }
    }

    @Test
    fun `ValidationRejected lands in FAILED_PERMANENT unless already PUSHED`() {
        for (state in PushState.entries) {
            val result = PushStateTransitions.next(state, SendOutcome.ValidationRejected("synthetic validation error"))
            val expected = if (state == PushState.PUSHED) PushState.PUSHED else PushState.FAILED_PERMANENT
            assertEquals("ValidationRejected from $state", expected, result)
        }
    }

    @Test
    fun `Retryable lands in FAILED_RETRYABLE unless already PUSHED, and never directly back in QUEUED`() {
        for (state in PushState.entries) {
            val result = PushStateTransitions.next(state, SendOutcome.Retryable("synthetic network error"))
            val expected = if (state == PushState.PUSHED) PushState.PUSHED else PushState.FAILED_RETRYABLE
            assertEquals("Retryable from $state", expected, result)
            assertTrue(
                "FAILED_RETRYABLE must require an explicit separate requeue action, not be auto-sendable",
                !PushStateTransitions.isSendable(result)
            )
        }
    }
}
