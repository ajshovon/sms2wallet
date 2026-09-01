package me.shovon.sms2wallet.domain.model

/**
 * Lifecycle state of a locally-parsed transaction on its way to the Wallet API.
 *
 * The Wallet REST API has no idempotency key and no client-supplied record id: posting the
 * same logical transaction twice creates two distinct rows on the server. Every state
 * transition in this app is therefore designed so that a transaction can be pushed at most
 * once, even across process death, crashes mid-request, or ambiguous network failures.
 *
 * State machine (see [PushStateTransitions] for the authoritative transition table):
 *
 * ```
 * PARSED --(user/queue confirms)--> QUEUED
 * QUEUED --(claimed for send, committed BEFORE the HTTP call)--> SENDING
 * SENDING --(2xx with a record id)--> PUSHED                 (terminal, never re-sent)
 * SENDING --(4xx validation failure)--> FAILED_PERMANENT      (terminal, never retried automatically)
 * SENDING --(network/5xx, no ambiguity)--> FAILED_RETRYABLE   (may return to QUEUED)
 * SENDING --(timeout / connection reset / unknown outcome)--> NEEDS_VERIFY
 * FAILED_RETRYABLE --(user/worker requeues)--> QUEUED
 * NEEDS_VERIFY --(reconciliation only, via GET against the API)--> PUSHED | FAILED_PERMANENT | QUEUED
 * ```
 */
enum class PushState {
    /** Freshly extracted from an SMS by a bank parser. Not yet reviewed or approved for send. */
    PARSED,

    /** Approved for sending. The ONLY state [PushStateTransitions.isSendable] accepts. */
    QUEUED,

    /**
     * A send attempt is in flight for this row. Committed to the database BEFORE the HTTP
     * call is made (see `TransactionDao.claimQueuedForSend`), so a process death or crash
     * during the request leaves the row stuck in `SENDING` rather than `QUEUED` - it is
     * therefore never picked up by another send pass and never double-sent. Recovery from
     * `SENDING` happens exclusively through reconciliation
     * (`TransactionDao.findOrphanedSending`), which decides based on elapsed time and,
     * where possible, a server-side lookup, not by blindly retrying.
     */
    SENDING,

    /** Confirmed created on the Wallet server. `walletRecordId` is set. Terminal - never re-sent. */
    PUSHED,

    /** The send failed for a transient reason (network error, 5xx, timeout with a clear negative). Safe to requeue. */
    FAILED_RETRYABLE,

    /** The send failed for a reason that will not change on retry (validation error, 4xx). Requires manual intervention. */
    FAILED_PERMANENT,

    /**
     * The outcome of the last send attempt is unknown - e.g. the request timed out or the
     * connection dropped after the request may have already reached the server. Must be
     * resolved by querying the Wallet API for a matching record, never by blindly retrying,
     * since a blind retry could create a duplicate.
     */
    NEEDS_VERIFY
}
