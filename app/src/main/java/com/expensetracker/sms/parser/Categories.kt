package com.expensetracker.sms.parser

object Categories {
    const val FOOD = "Food"
    const val GROCERIES = "Groceries"
    const val FUEL = "Fuel"
    const val SHOPPING = "Shopping"
    const val MEDICAL = "Medical"
    const val TRAVEL = "Travel"
    const val TRANSPORT = "Transport"
    const val ENTERTAINMENT = "Entertainment"
    const val INVESTMENT = "Investment"
    const val SALARY = "Salary"
    const val ATM = "ATM"
    const val UTILITIES = "Utilities"
    const val EMI = "EMI"
    const val INSURANCE = "Insurance"
    const val RECHARGE = "Recharge"
    const val SUBSCRIPTION = "Subscription"
    const val RENT = "Rent"
    const val TRANSFER = "Transfer"
    const val CASH_WITHDRAWAL = "Cash Withdrawal"
    const val EDUCATION = "Education"
    const val OTHERS = "Others"

    val defaults: List<String> = listOf(
        FOOD, GROCERIES, FUEL, SHOPPING, MEDICAL, TRAVEL, TRANSPORT, ENTERTAINMENT,
        INVESTMENT, SALARY, ATM, UTILITIES, EMI, INSURANCE, RECHARGE, SUBSCRIPTION,
        RENT, TRANSFER, CASH_WITHDRAWAL, EDUCATION, OTHERS,
    )
}
