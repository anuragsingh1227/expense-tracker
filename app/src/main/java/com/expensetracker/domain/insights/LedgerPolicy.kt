package com.expensetracker.domain.insights

/**
 * Financial-ledger rules this offline SMS tracker follows.
 *
 * Drawn from common fintech ledger practice (append-only facts, one economic
 * event → one journal row, derived balances, idempotent ingest, reconciliation
 * against the bank's own movement alerts — not merchant/processor copy):
 *
 * 1. **Source of truth = account/card movement SMS.** A row is booked only when
 *    the user's bank or card account was debited, credited, spent, refunded, or
 *    reversed. Balances and dashboards are derived from those rows.
 *
 * 2. **One economic event, one row.** Card-issuer or merchant acknowledgements
 *    ("we have received your payment", "thank you for your payment", "payment
 *    credited to your card") are **not** ledger entries — they duplicate the
 *    savings/current debit (or the card-spend alert). Rejected in
 *    [com.expensetracker.sms.parser.TransactionGate].
 *
 * 3. **Classify, don't double-count.** Card *purchases* are spend. Paying a card
 *    bill from a bank account is [com.expensetracker.sms.parser.Categories.TRANSFER]
 *    (own-money move). Refunds/reversals offset spend via [LedgerBuckets], never
 *    inflate income.
 *
 * 4. **Idempotent ingest.** Duplicate SMS share a dedupe hash; retries must not
 *    create a second economic effect ([com.expensetracker.sms.parser.SmsParser]).
 *
 * 5. **Precise money.** Amounts use scale-2 decimal rupees ([com.expensetracker.domain.model.Money]),
 *    never floating point.
 *
 * 6. **Corrections via new facts.** Cleanup re-evaluates raw SMS against the gate;
 *    users edit/delete rows rather than silently mutating historical amounts in place.
 */
object LedgerPolicy
