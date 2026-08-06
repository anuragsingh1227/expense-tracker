package com.expensetracker.sms.parser

/** Anonymized real-world-shaped Indian bank/UPI SMS bodies used across parser tests. */
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

    const val OTP_MESSAGE =
        "123456 is your OTP for HDFC Bank net banking login. Do not share with anyone."

    const val PROMOTIONAL =
        "Get 10% off on your next purchase at Myntra. T&C apply."

    // Duplicate of HDFC_DEBIT (received on second SIM)
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
}
