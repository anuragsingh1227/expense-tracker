package com.expensetracker.sms.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BankSendersTest {

    @Test
    fun `identifies major and neo-bank headers`() {
        assertThat(BankSenders.identify("VM-HDFCBK")).isEqualTo("HDFC")
        assertThat(BankSenders.identify("AD-ICICIB")).isEqualTo("ICICI")
        assertThat(BankSenders.identify("JD-SBIINB")).isEqualTo("SBI")
        assertThat(BankSenders.identify("AX-FEDERA")).isEqualTo("Federal Bank")
        assertThat(BankSenders.identify("VM-RBLBNK")).isEqualTo("RBL")
        assertThat(BankSenders.identify("AD-BANDHAN")).isEqualTo("Bandhan")
        assertThat(BankSenders.identify("VM-JUPITER")).isEqualTo("Jupiter")
        assertThat(BankSenders.identify("AD-FIMONEY")).isEqualTo("Fi")
        assertThat(BankSenders.identify("AX-IDBIBK-S")).isEqualTo("IDBI")
        assertThat(BankSenders.identify("VM-GPAY")).isEqualTo("Google Pay")
    }

    @Test
    fun `allBankNames includes expanded coverage`() {
        val names = BankSenders.allBankNames()
        assertThat(names).containsAtLeast("Federal Bank", "RBL", "Fi", "Jupiter", "HDFC")
    }
}
