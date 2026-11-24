package com.example.h1companion.codec

import java.util.Locale

/** Domain view of an association, independent of Android's AssociationInfo. */
data class AssociationRecord(
    val deviceMac: String?,
    val selfManaged: Boolean,
    val id: Int? = null,
)

enum class AssociationDecision {
    ASSOCIATED,
    NOT_ASSOCIATED,
    SELF_MANAGED_NOT_ACCEPTED,
}

/**
 * On Android 16 the Bluetooth service accepts a non-self-managed association whose
 * MAC matches the calling package's device; self-managed associations do not qualify.
 * A bond alone is not sufficient.
 */
object AssociationEvaluator {
    fun evaluate(records: List<AssociationRecord>, deviceMac: String): AssociationDecision {
        val expected = deviceMac.lowercase(Locale.ROOT)
        val matching = records.filter {
            it.deviceMac?.lowercase(Locale.ROOT) == expected
        }
        if (matching.isEmpty()) return AssociationDecision.NOT_ASSOCIATED
        if (matching.any { !it.selfManaged }) return AssociationDecision.ASSOCIATED
        return AssociationDecision.SELF_MANAGED_NOT_ACCEPTED
    }
}

/**
 * Version qualification from the retained AOSP snapshots, not an OEM/Mainline guarantee.
 * Android 12/13 snapshots had no CDM gate on these Binder methods; Android 14+ added it
 * for the setter, and the Android 15 r36 / Android 16 snapshots gate the getter too.
 */
object AssociationPolicy {
    fun requiredForSet(sdkInt: Int): Boolean = sdkInt >= 34
    fun requiredForGet(sdkInt: Int): Boolean = sdkInt >= 35
}
