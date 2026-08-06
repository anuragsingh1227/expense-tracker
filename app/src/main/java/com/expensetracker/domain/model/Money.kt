package com.expensetracker.domain.model

import java.math.BigDecimal
import java.math.RoundingMode

@JvmInline
value class Money(val amount: BigDecimal) {
    init {
        require(amount.scale() <= 2) { "Money must have at most 2 decimal places" }
    }

    operator fun plus(other: Money): Money =
        Money(amount.add(other.amount).setScale(2, RoundingMode.HALF_UP))

    operator fun minus(other: Money): Money =
        Money(amount.subtract(other.amount).setScale(2, RoundingMode.HALF_UP))

    operator fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    fun formatInr(): String {
        val hasFraction = amount.stripTrailingZeros().scale() > 0
        val absAmount = amount.abs().setScale(if (hasFraction) 2 else 0, RoundingMode.HALF_UP)
        val plain = absAmount.toPlainString()
        val (intPart, fracPart) = if (plain.contains('.')) {
            val idx = plain.indexOf('.')
            plain.substring(0, idx) to plain.substring(idx + 1)
        } else {
            plain to ""
        }
        val grouped = groupIndian(intPart)
        val sign = if (amount.signum() < 0) "-" else ""
        val fraction = if (fracPart.isNotEmpty()) ".$fracPart" else ""
        return "$sign₹$grouped$fraction"
    }

    private fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.substring(0, digits.length - 3)
        val tail = digits.substring(digits.length - 3)
        val headGrouped = buildString {
            var s = head
            while (s.length > 2) {
                insert(0, "," + s.substring(s.length - 2))
                s = s.substring(0, s.length - 2)
            }
            insert(0, s)
        }
        return "$headGrouped,$tail"
    }

    companion object {
        val ZERO: Money = Money(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))

        fun ofRupees(value: String): Money =
            Money(BigDecimal(value.replace(",", "")).setScale(2, RoundingMode.HALF_UP))

        fun ofRupees(value: Long): Money =
            Money(BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP))
    }
}
