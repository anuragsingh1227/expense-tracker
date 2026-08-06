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

    /** Card account credit for a bill payment — not spend income. */
    const val CARD_PAYMENT_CREDITED =
        "Dear bank cardmember, Payment of Rs 2487 was credited to your card ending 1234 on 05/Aug/2026."

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

    const val BALANCE_ONLY =
        "Your a/c XX1234 Avl Bal Rs 12,340.55 as on 12-01-24. Do not share OTP with anyone."

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
