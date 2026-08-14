package com.expensetracker.data.repository

import com.expensetracker.sms.parser.SampleSms
import com.expensetracker.sms.parser.SmsParser
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpamCleanupTest {

    private val parser = SmsParser()

    @Test
    fun `manual cash entry with blank SMS is never purged`() {
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                rawSms = null,
                manuallyEdited = true,
                parser = parser,
            ),
        ).isFalse()
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                rawSms = "",
                manuallyEdited = true,
                parser = parser,
            ),
        ).isFalse()
    }

    @Test
    fun `blank SMS without manual flag is still kept`() {
        // Paste-less cash rows and pre-flag legacy manuals must not be deleted.
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                rawSms = null,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isFalse()
    }

    @Test
    fun `user-edited imported SMS is never purged even if body looks like spam`() {
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                rawSms = SampleSms.OTP_MESSAGE,
                manuallyEdited = true,
                parser = parser,
            ),
        ).isFalse()
    }

    @Test
    fun `OTP and due-reminder SMS are purged`() {
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                rawSms = SampleSms.OTP_MESSAGE,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isTrue()
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                rawSms = SampleSms.DUE_REMINDER_SPAM,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isTrue()
    }

    @Test
    fun `real debit SMS is kept`() {
        assertThat(
            TransactionRepositoryImpl.shouldPurgeImportedSms(
                rawSms = SampleSms.HDFC_DEBIT,
                manuallyEdited = false,
                parser = parser,
            ),
        ).isFalse()
    }
}
