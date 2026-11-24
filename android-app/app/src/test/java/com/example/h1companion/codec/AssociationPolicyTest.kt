package com.example.h1companion.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for association evaluation and SDK version qualification. */
class AssociationPolicyTest {

    @Test
    fun `empty records are not associated`() {
        assertEquals(
            AssociationDecision.NOT_ASSOCIATED,
            AssociationEvaluator.evaluate(emptyList(), "AA:BB:CC:DD:EE:FF"),
        )
    }

    @Test
    fun `matching non self managed record is associated`() {
        val records = listOf(AssociationRecord("AA:BB:CC:DD:EE:FF", selfManaged = false))
        assertEquals(
            AssociationDecision.ASSOCIATED,
            AssociationEvaluator.evaluate(records, "AA:BB:CC:DD:EE:FF"),
        )
    }

    @Test
    fun `only matching self managed records are not accepted`() {
        val records = listOf(
            AssociationRecord("AA:BB:CC:DD:EE:FF", selfManaged = true, id = 1),
            AssociationRecord("AA:BB:CC:DD:EE:FF", selfManaged = true, id = 2),
        )
        assertEquals(
            AssociationDecision.SELF_MANAGED_NOT_ACCEPTED,
            AssociationEvaluator.evaluate(records, "AA:BB:CC:DD:EE:FF"),
        )
    }

    @Test
    fun `mixed self managed and non self managed records are associated`() {
        val records = listOf(
            AssociationRecord("AA:BB:CC:DD:EE:FF", selfManaged = true, id = 1),
            AssociationRecord("11:22:33:44:55:66", selfManaged = false, id = 2),
            AssociationRecord("AA:BB:CC:DD:EE:FF", selfManaged = false, id = 3),
        )
        assertEquals(
            AssociationDecision.ASSOCIATED,
            AssociationEvaluator.evaluate(records, "AA:BB:CC:DD:EE:FF"),
        )
    }

    @Test
    fun `mac comparison is case insensitive`() {
        val records = listOf(AssociationRecord("aa:bb:cc:dd:ee:ff", selfManaged = false))
        assertEquals(
            AssociationDecision.ASSOCIATED,
            AssociationEvaluator.evaluate(records, "AA:BB:CC:DD:EE:FF"),
        )

        val selfManagedOnly = listOf(AssociationRecord("AA:BB:CC:DD:EE:FF", selfManaged = true))
        assertEquals(
            AssociationDecision.SELF_MANAGED_NOT_ACCEPTED,
            AssociationEvaluator.evaluate(selfManagedOnly, "aa:bb:cc:dd:ee:ff"),
        )
    }

    @Test
    fun `records for a different mac are ignored`() {
        val records = listOf(
            AssociationRecord("11:22:33:44:55:66", selfManaged = false),
            AssociationRecord("22:33:44:55:66:77", selfManaged = true),
        )
        assertEquals(
            AssociationDecision.NOT_ASSOCIATED,
            AssociationEvaluator.evaluate(records, "AA:BB:CC:DD:EE:FF"),
        )
    }

    @Test
    fun `records with a null mac are ignored`() {
        val records = listOf(
            AssociationRecord(null, selfManaged = false),
            AssociationRecord(null, selfManaged = true),
        )
        assertEquals(
            AssociationDecision.NOT_ASSOCIATED,
            AssociationEvaluator.evaluate(records, "AA:BB:CC:DD:EE:FF"),
        )
    }

    @Test
    fun `setter gate starts at sdk 34`() {
        assertFalse(AssociationPolicy.requiredForSet(33))
        assertTrue(AssociationPolicy.requiredForSet(34))
        assertTrue(AssociationPolicy.requiredForSet(35))
        assertTrue(AssociationPolicy.requiredForSet(36))
    }

    @Test
    fun `getter gate starts at sdk 35`() {
        assertFalse(AssociationPolicy.requiredForGet(33))
        assertFalse(AssociationPolicy.requiredForGet(34))
        assertTrue(AssociationPolicy.requiredForGet(35))
        assertTrue(AssociationPolicy.requiredForGet(36))
    }
}
