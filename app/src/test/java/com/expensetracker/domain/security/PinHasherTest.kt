package com.expensetracker.domain.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PinHasherTest {

    @Test
    fun `matches returns true for the correct pin`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash("1234", salt)
        assertThat(PinHasher.matches("1234", salt, hash)).isTrue()
    }

    @Test
    fun `matches returns false for a wrong pin`() {
        val salt = PinHasher.generateSalt()
        val hash = PinHasher.hash("1234", salt)
        assertThat(PinHasher.matches("4321", salt, hash)).isFalse()
    }

    @Test
    fun `same pin with different salts produces different hashes`() {
        val saltA = PinHasher.generateSalt()
        val saltB = PinHasher.generateSalt()
        assertThat(saltA).isNotEqualTo(saltB)
        assertThat(PinHasher.hash("9999", saltA)).isNotEqualTo(PinHasher.hash("9999", saltB))
    }

    @Test
    fun `generateSalt produces unique values`() {
        val salts = (1..20).map { PinHasher.generateSalt() }
        assertThat(salts.toSet()).hasSize(salts.size)
    }

    @Test
    fun `hash is deterministic for the same pin and salt`() {
        val salt = PinHasher.generateSalt()
        assertThat(PinHasher.hash("0000", salt)).isEqualTo(PinHasher.hash("0000", salt))
    }
}
