package com.expensetracker.sms.parser

/**
 * Anonymized Indian bank/UPI SMS shaped from public DLT-style templates and
 * open-source parser docs (e.g. transaction-sms-parser README examples).
 */
object SampleSms {
    const val HDFC_DEBIT =
        "Rs 245.00 debited from a/c XXXX1234 on 12-01-24 at AMAZON via UPI. Bal: Rs 12,340.55. UPI Ref 401234567890"

    const val SBI_CREDIT =
        "INR 50000.00 credited to A/c XXXX9876 on 01-Feb-24 by NEFT from Acme Corp Salary. Avl Bal Rs 1,20,450.00 Ref N001234"

    const val ICICI_UPI_DEBIT =
        "Dear Customer, your ICICI Bank A/c XX789 has been debited with Rs 499.00 on 03/03/24. Info: UPI/408123/SWIGGY. Bal: Rs 5,231.10"

    const val AXIS_CARD =
        "Thank you for using AXIS Bank Credit Card ending 4321 for Rs. 1,299.00 on FLIPKART on 15-04-24. Avl limit Rs 48,701.00"

    const val KOTAK_FASTAG =
        "Rs 500 debited for FASTag recharge on 20-05-24 from a/c XX2222. Ref 501234567. Avl Bal Rs 1234.56"

    const val UPI_TO_MERCHANT =
        "UPI txn of Rs 199 to Zomato via GPay on 21-06-24 completed. UPI Ref 412345678901"

    /** From public transaction-sms-parser README example. */
    const val INR_DEBITED_ECS =
        "INR 2000 debited from A/c no. XX3423 on 05-02-19 07:27:11 IST at ECS PAY. Avl Bal- INR 2343.23."

    /** DLT-style template shape (SMS gateway bank samples). */
    const val BILLPAY_DEBIT =
        "Dear Customer, Rs.2,487.00 is debited from A/c XXXX6791 for BillPay/Credit Card payment via NetBanking on 23-07-26."

    const val IMPS_SELF_TRANSFER =
        "Acct XX126 debited with INR 4,600.00 on 23-Jul-2026 & Acct XX791 credited. IMPS: XXX410XX."

    /**
     * Card-side bill-payment posting — duplicate of the bank debit SMS.
     * Must be ignored entirely (not booked as Transfer/income/spend).
     */
    const val CARD_PAYMENT_CREDITED =
        "Dear bank cardmember, Payment of Rs 2487 was credited to your card ending 1234 on 05/Aug/2026."

    /** Issuer acknowledgement — not a ledger movement. */
    const val WE_RECEIVED_PAYMENT_CREDIT_CARD =
        "Dear Customer, we have received payment of INR 12,500.00 towards your Credit Card XX1014 on 08-08-26. Thank you."

    /** Issuer posting ack without "received"/"thank you" — still not a bank movement. */
    const val CARD_PAYMENT_POSTED_ACK =
        "Dear Customer, payment of INR 8,000.00 towards your Credit Card XX1014 has been posted on 08-08-26. Avl limit Rs 42,000.00"

    /** Merchant/third-party ack mentioning the card rail — not a ledger movement. */
    const val MERCHANT_RECEIVED_PAYMENT_FROM_CARD =
        "We have received payment from credit card ending 4321 for Rs 1,299.00 at FLIPKART on 15-04-24. Order confirmed."

    /** Generic biller "payment received successfully" — not a bank movement. */
    const val BILLER_PAYMENT_RECEIVED_SUCCESS =
        "Payment of Rs 899.00 received successfully for your electricity bill. Ref BBPS123456."

    /** PPF contribution thank-you — confirmation only; must not create a ledger row. */
    const val PPF_CONTRIBUTION_RECEIVED_ACK =
        "ICICI Bank: Amount of INR 10,000 received in your PPF account XX9988 contribution. Thank you."

    /** NPS contribution received ack — confirmation only. */
    const val NPS_CONTRIBUTION_RECEIVED_ACK =
        "We have received your contribution of Rs 5,000 towards NPS Tier I. Thank you."

    /** Real PPF SI posting — book as DEBIT Investment (not Transfer/spend). */
    const val IDBI_PPF_SI_CREDIT =
        "SI Transaction of Rs 12000 successfully credited in PPF Ac No ****************0079 on 07/08/2026 -IDBI Bank"

    /** Explicit UPI self-transfer credit — Transfer, never spend/income. */
    const val AXIS_SELF_TRANSFER_CREDIT =
        "Axis Bank: Your A/c XX5566 is credited with INR 5,000 (UPI Ref 612345) from SELF TRANSFER."

    /** Card spend at online merchant — real expense. */
    const val HDFC_ZOMATO_CARD_SPEND =
        "HDFC Bank: You spent Rs. 549 at ZOMATO on Credit Card XX1234 on 2026-08-10. Noted txn?"

    /** Issuer payment-received against card — confirmation only. */
    const val SBI_CARD_PAYMENT_RECEIVED_AGAINST =
        "SBI Card: Payment of INR 10,000 received against your Credit Card XX1234 on 2026-08-12."

    /**
     * Axis compact UPI debit with amount *before* the verb ("INR X debited").
     * Must stay in the ledger (was dropped by the gate previously).
     */
    const val AXIS_INR_DEBITED_UPI_P2A = """
INR 2500.00 debited
A/c no. XX8291
08-08-26, 14:46:26
UPI/P2A/111991242206/LALAWMPUII
Not you? SMS BLOCKUPI Cust ID to 919951860002
Axis Bank
""".trimIndent()

    /** Public DLT-style spend alert (SMS Gateway Center sample shape). */
    const val CARD_SPENT_JIO =
        "Alert: You've spent INR 555.00 on your bank card **9123 at BD JIO MONEY on 05/08/2026 at 11:07 IST."

    /** Year-less month token — common in compact UPI SMS. */
    const val HDFC_UPI_NO_YEAR =
        "Rs.450.00 debited from A/c XX1234 on 15-APR for UPI/412839-BIGBASKET. Avl Bal Rs 8,200.00"

    const val EMI_SCHEDULED_SPAM =
        "Dear customer, your EMI of Rs. 99650.00 is scheduled for ECS clearance on 10-08-2026. Please keep sufficient balance."

    const val FX_REVERSAL_LIMIT_TRAP =
        "Your transaction of SGD 1.38 on HDFC Bank Credit Card ending 4321 is reversed. Available Credit Limit is Rs.2,26,151.86"

    const val FX_SPEND_WITH_INR_EQUIV =
        "USD 12.50 spent on AXIS Bank Credit Card ending 4321 at AMAZON. INR equiv Rs.1,045.00. Avl limit Rs 48,701.00"

    const val OTP_MESSAGE =
        "123456 is your OTP for HDFC Bank net banking login. Do not share with anyone."

    const val PROMOTIONAL =
        "Get 10% off on your next purchase at Myntra. T&C apply."

    const val HDFC_DEBIT_DUP =
        "Rs 245 debited from a/c XXXX1234 on 12-01-24 at AMAZON via UPI. Bal: Rs 12,340.55. UPI Ref 401234567890"

    const val GROWW_INVESTMENT =
        "Rs 5,000.00 debited from a/c XX4411 for GROWW purchase on 02-07-24. UPI Ref 512345678901. Avl Bal Rs 22,100.00"

    const val INSTAMART_GROCERY =
        "Rs 612.00 debited via UPI to SWIGGY INSTAMART on 03-07-24. Ref 612345678901. Bal Rs 8,200.50"

    const val ZEPTO_GROCERY =
        "UPI txn of Rs 349 to ZEPTO on 04-07-24 completed. UPI Ref 712345678901"

    const val CASHBACK_SPAM =
        "Get up to Rs 500 cashback on UPI spends this weekend. Shop now. T&C apply."

    const val LOAN_OFFER_SPAM =
        "Congratulations! Pre-approved loan offer of Rs 2,00,000. Apply now for instant approval."

    const val DUE_REMINDER_SPAM =
        "Your credit card payment due date is 12-08-24. Min amt due Rs 1,250. Outstanding Rs 18,400."

    /** ICICI auto-debit due notice — future tense, not a completed spend. */
    const val ICICI_AMOUNT_DUE_NOTICE =
        "Total Amount of INR 34,757.45 is due on ICICI Bank Credit Card XX1014. Amount will be debited from Savings Account XX293 on 02-Aug-26. Please ignore if paid."

    /** Credit-limit raise promo — must not book Rs300000 as a debit. */
    const val ICICI_CREDIT_LIMIT_RAISE =
        "Manage spends effectively by increasing the limit on ICICI Bank Credit Card XX1014 from Rs300000 to Rs1000000. SMS CRLIM 1014 to 5676766 to raise the limit"

    /**
     * Real card spend that also appends a dispute helpline — merchant must stay
     * Zomato, not "dispute call 18001080/…".
     */
    const val ICICI_CARD_SPEND_WITH_DISPUTE =
        "Thank you for using your ICICI Bank Credit Card XX1014 for INR 1,122.00 at ZOMATO on 01-Aug-26. To dispute call 18001080/9215676766."

    const val BALANCE_ONLY =
        "Your a/c XX1234 Avl Bal Rs 12,340.55 as on 12-01-24. Do not share OTP with anyone."

    /** Refund must offset spend, not count as income — even with a known merchant name. */
    const val AMAZON_REFUND_CREDITED =
        "Rs 1,499.00 has been credited to your ICICI Bank A/c XX789 as refund from AMAZON on 09-08-26. Ref 900123456."

    /** Reversal wording without the word "credited" — must still parse as a refund. */
    const val CARD_TXN_REVERSED_NO_CREDIT_WORD =
        "Your transaction of Rs 799.00 on ICICI Bank Credit Card XX1014 at MYNTRA has been reversed. Ref 900654321."

    /** Auto-pay of a card bill phrased as "payment to credit card" — must be Transfer. */
    const val PAYMENT_TO_CREDIT_CARD_TRANSFER =
        "Rs 3,200.00 debited from A/c XX1234 for payment to Credit Card XX1014 via NetBanking on 09-08-26."

    /** ICICI card spend using "is used for" — no "debited" verb. */
    const val ICICI_CARD_IS_USED =
        "ICICI Bank Credit Card XX1014 is used for Rs 2,450.00 at AMAZON on 05-Aug-26. Avl limit Rs 46,251.00"

    /** ICICI card spend using "has been used for a transaction of". */
    const val ICICI_CARD_HAS_BEEN_USED =
        "Dear Customer, your ICICI Bank Credit Card XX1014 has been used for a transaction of Rs 1,122.00 on 01-Aug-26 at ZOMATO. Avl limit: Rs 48,701.00"

    /** UPI debit with merchant name after the UPI reference number. */
    const val UPI_WITH_NAMED_MERCHANT =
        "Rs 450.00 debited from A/c XX1234 on 07-Aug-26 for UPI/412839-SWIGGY. Avl Bal Rs 8,200.00"

    /** UPI debit with only a reference number — no merchant name. */
    const val UPI_REF_ONLY =
        "Rs 1,000.00 debited from A/c XX1234 on 05-Aug-26 for UPI/123456789012. Avl Bal Rs 8,200.00"

    /** IDFC-style received-payment — money IN, must be CREDIT + Transfer (never spend/income). */
    const val IDFC_RECEIVED_UPI =
        "Rs 1,000.00 received in your IDFC FIRST Bank Account XX293 from anurag singh-1@okicici on 07-08-26. Call 180010888 for dispute. UPI: 807123456789"

    /** Auto-pay activation notice — future debit, NOT a completed transaction; must be ignored. */
    const val AUTOPAY_WILL_BE_DEBITED_NOTICE =
        "Dear Customer, INR 1,631.00 will be debited from Account XX293 on 07-08-25 towards your ICICI Bank Credit Card XX1014. Auto Pay is activated for your credit card account. RMHBK S"

    /** Axis compact multi-line NEFT debit — must parse (was dropped by gate). */
    const val AXIS_DEBIT_INR_NEFT =
        "Debit INR 17383.00\nAxis Bank A/c XX8291\n15-06-26 20:43:26\nNEFT/MB/AXOMB16602145999/V\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002"

    /** Axis compact card-payment debit from savings — Transfer, not spend. */
    const val AXIS_DEBIT_INR_CRD_PMNT =
        "Debit INR 3868.40\nAxis Bank A/c XX8291\n02-07-26 10:41:54\nCRD-PMNT-530562****0887\nWhatsApp BAL to 917036165000\nNot You? SMS BLOCKALL CustID to 919951860002"

    /** Matching credit leg of an own-account NEFT (owner name ANURAG in body). */
    const val OWN_ACCOUNT_NEFT_CREDIT_ANURAG =
        "Credit INR 17383.00\nICICI Bank A/c XX293\n15-06-26 20:45:10\nNEFT/MB/AXOMB16602145999/V ANURAG SINGH"

    /**
     * Card-side "thank you for payment" confirmation of a credit-card auto-debit —
     * duplicates the bank-side debit SMS below; must be ignored entirely.
     */
    const val CARD_AUTODEBIT_THANK_YOU_DUPLICATE =
        "Dear Customer, thank you for your payment of INR 29,004.46 towards ICICI Bank Credit Card Account XX4104 through Auto Debit from Account XX6293 on 02-Jun-26"

    /** Bank-side debit SMS for the same auto-debit — this is the one to keep, as Transfer. */
    const val ICICI_ACC_DEBITED_ATD_AUTO_DEBIT =
        "ICICI Bank Acc XX293 debited Rs. 29,004.46 on 02-Jun-26 InfoATD*Auto Debi.Avl Bal Rs. 3,12,078.24.To dispute call 18002662 or SMS BLOCK 293 to 9215676766"

    /** Multi-month batch for ledger / paste-import tests (blank-line separated). */
    const val LEDGER_BATCH = """
INR 52000.00 credited to A/c XXXX9876 on 01-Jul-26 by NEFT Salary. Avl Bal Rs 80,000.00

Rs 1,200.00 debited from a/c XX1234 on 03-Jul-26 at SWIGGY via UPI. UPI Ref 7001001

Rs 5,000.00 debited from a/c XX4411 for GROWW purchase on 05-Jul-26. UPI Ref 7001002

Rs 890.00 debited via UPI to ZEPTO on 12-Jul-26. Ref 7001003

Rs 2,400.00 debited from a/c XX1234 on 18-Jul-26 at AMAZON via UPI. UPI Ref 7001004

INR 52000.00 credited to A/c XXXX9876 on 01-Aug-26 by NEFT Salary. Avl Bal Rs 90,000.00

Rs 420.00 debited from a/c XX1234 on 02-Aug-26 at SWIGGY via UPI. UPI Ref 8001001

Rs 612.00 debited via UPI to SWIGGY INSTAMART on 03-Aug-26. Ref 8001002

Rs 5,000.00 debited from a/c XX4411 for GROWW purchase on 04-Aug-26. UPI Ref 8001003

Rs 1,299.00 spent on AXIS Bank Credit Card ending 4321 at FLIPKART on 05-Aug-26

Get up to Rs 500 cashback on UPI spends this weekend. Shop now.
"""
}
