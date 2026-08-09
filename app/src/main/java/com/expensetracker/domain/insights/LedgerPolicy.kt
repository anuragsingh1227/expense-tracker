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
 * 2. **One economic event, one row.** Acknowledgements are **not** ledger entries:
 *    card-issuer/merchant "we have received your payment", "payment credited to
 *    your card", and investment "contribution received" (PPF/NPS/SIP) thank-yous.
 *    They duplicate the source debit or SI posting. Rejected in
 *    [com.expensetracker.sms.parser.TransactionGate].
 *
 * 3. **Classify, don't double-count.** Card *purchases* are spend. Paying a card
 *    bill or an explicit self-transfer is [com.expensetracker.sms.parser.Categories.TRANSFER].
 *    Real PPF SI / "credited in PPF" postings are Investment (not spend).
 *    Refunds/reversals offset spend via [LedgerBuckets], never inflate income.
 *    When both legs of an own-account move share a UPI/NEFT/IMPS reference,
 *    [SelfTransferLinker] removes the pair from Activity (zero net worth change).
 *    Broker "refund initiated" SMS are ignored; a matching Investment debit +
 *    bank return credit is cleared by [InvestmentReturnLinker] (net zero).
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
