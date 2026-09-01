package me.shovon.sms2wallet.domain.model

/**
 * Outcome of a single attempt to POST a transaction to the Wallet API. Produced by the
 * (separately-owned) network layer and fed into [PushStateTransitions.next] to compute the
 * transaction's next [PushState]. Deliberately does not carry HTTP status codes or exceptions
 * directly - callers classify the raw result into one of these four buckets first, which keeps
 * the state machine itself free of networking concerns and trivially unit-testable.
 */
sealed class SendOutcome {
    /** The API accepted the transaction and returned the id of the created Wallet record. */
    data class Success(val recordId: String) : SendOutcome()

    /** The API rejected the request as invalid (e.g. 4xx). Retrying with the same payload will not help. */
    data class ValidationRejected(val msg: String) : SendOutcome()

    /** A transient failure (e.g. no network, timeout before the request was sent, 5xx). Safe to requeue. */
    data class Retryable(val msg: String) : SendOutcome()

    /**
     * The request may or may not have reached the server and been processed - e.g. the
     * connection dropped while waiting for a response, or the client timed out after the
     * request was already written. Requires querying the API to reconcile; must never be
     * treated as a green light to retry the same POST.
     */
    data class Ambiguous(val msg: String) : SendOutcome()
}

/**
 * Pure, side-effect-free state machine governing how a transaction's [PushState] evolves.
 *
 * This object is the single source of truth for "is it safe to send this row" and "what state
 * should it move to after an attempt". Keeping it pure (no DB, no network) lets the invariant
 * that matters most - a transaction can never be sent twice - be verified exhaustively with
 * plain JUnit tests, independent of Room or Robolectric.
 */
object PushStateTransitions {

    /**
     * True only for [PushState.QUEUED]. This is the sole gate that lets `claimQueuedForSend`
     * pick up a row: every other state is either not yet approved for sending ([PushState.PARSED]),
     * already in flight or resolved ([PushState.SENDING], [PushState.PUSHED]), or requires a
     * decision before it can be attempted again ([PushState.FAILED_RETRYABLE],
     * [PushState.FAILED_PERMANENT], [PushState.NEEDS_VERIFY]).
     */
    fun isSendable(state: PushState): Boolean = state == PushState.QUEUED

    /**
     * Computes the next [PushState] given the current state and the outcome of a send attempt.
     *
     * [PushState.PUSHED] is a hard terminal state: once a row is confirmed pushed, no outcome
     * - however it arrives, including a stray or duplicated callback - can move it anywhere
     * else. This is what prevents a [SendOutcome.Retryable] or [SendOutcome.Ambiguous] result
     * that is mistakenly applied to an already-pushed row from ever making it eligible for a
     * second send: [isSendable] only accepts [PushState.QUEUED], and this function never
     * produces [PushState.QUEUED] directly for any outcome, nor does it ever move a
     * [PushState.PUSHED] row out of [PushState.PUSHED].
     *
     * Note that [SendOutcome.Retryable] resolves to [PushState.FAILED_RETRYABLE], not back to
     * [PushState.QUEUED] - requeuing a retryable failure is a distinct, explicit action taken
     * by the caller (e.g. a "retry" button or a backoff worker), never an implicit side effect
     * of observing the outcome.
     */
    fun next(current: PushState, outcome: SendOutcome): PushState {
        if (current == PushState.PUSHED) {
            // Terminal: a confirmed-pushed row is never revisited by this function again,
            // regardless of what outcome is reported against it.
            return PushState.PUSHED
        }
        return when (outcome) {
            is SendOutcome.Success -> PushState.PUSHED
            is SendOutcome.ValidationRejected -> PushState.FAILED_PERMANENT
            is SendOutcome.Retryable -> PushState.FAILED_RETRYABLE
            is SendOutcome.Ambiguous -> PushState.NEEDS_VERIFY
        }
    }
}
