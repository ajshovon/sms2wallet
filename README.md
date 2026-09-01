# SMS2Wallet

Reads Bangladeshi bank and mobile-financial-service transaction SMS on your phone,
parses them, and pushes them into [Wallet by BudgetBakers](https://web.budgetbakers.com)
through its REST API — so you stop typing transactions by hand.

> Independent, unaffiliated client of the BudgetBakers public API.
> Requires a Wallet **Premium** subscription, since API tokens are a Premium feature.

## What it does

- **Parses SMS from 9 BD providers** — bKash, Nagad, Rocket, Upay, Tap, City Bank,
  BRAC Bank, EBL, MTB — via the [bd-sms-parsers](https://github.com/ajshovon/bd-sms-parsers) library.
- **Per-parser control.** Each provider has two independent switches: whether it runs
  at all, and whether its transactions push to Wallet automatically or wait for review.
- **Review queue.** Anything not auto-pushed lands in a queue you can edit, approve, or dismiss.
- **Reminders.** A configurable daily nudge to log cash spending that never produced an SMS,
  with a quick-add sheet that writes straight to Wallet.
- **Never creates duplicates.** See below — this is the hard part.

## Duplicate safety

The Wallet API has no idempotency key and no client-supplied record ID, so re-posting
the same transaction silently creates a second record. SMS2Wallet defends in three layers:

1. **Ingest dedup** — a unique index on a hash of the SMS means the same message
   can never be parsed into two rows, however many times you rescan.
2. **A send state machine** — a row moves to `SENDING` *before* the HTTP call, and only
   `QUEUED` rows are eligible to send. A crash mid-push can never re-send on restart.
3. **Reconciliation** — when a connection drops mid-flight, the outcome is genuinely
   unknown. Rather than retry blindly, the app queries Wallet for a matching record
   written by this integration and adopts it if found.

## Permissions

| Permission | Why |
|---|---|
| `READ_SMS`, `RECEIVE_SMS` | Read transaction SMS. Messages are parsed on-device; only the resulting transaction is ever sent to Wallet. |
| `INTERNET` | Talk to the Wallet API. |
| `POST_NOTIFICATIONS` | Reminders and review prompts. |
| `SCHEDULE_EXACT_ALARM` | Fire the reminder at the time you picked. Optional — without it, reminders still work, just within about 15 minutes. |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule reminders after a reboot. |

Your API token is encrypted with a hardware-backed Android Keystore key and never
leaves the device except as the `Authorization` header to BudgetBakers.

## Building

```bash
git clone --recurse-submodules https://github.com/ajshovon/sms2wallet.git
cd sms2wallet
./gradlew :app:assembleDebug
```

Already cloned without submodules? `git submodule update --init --recursive`.

## License

GNU AGPL-3.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE).
