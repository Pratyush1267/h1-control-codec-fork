package com.example.h1companion.codec

/**
 * Requested settings and observed state are never the same object. A remembered
 * preference is not proof that a policy was applied or that it survived reconnect.
 */
enum class ObservationStatus {
    OBSERVED,
    PENDING,
    STALE,
    UNAVAILABLE,
    UNKNOWN,
}

data class Observation<T>(
    val value: T?,
    val status: ObservationStatus,
    val source: String,
    val observedAtUtcMillis: Long?,
)

data class CodecUiState(
    val active: Observation<ActiveCodec> = Observation(null, ObservationStatus.UNKNOWN, "none", null),
    val requestedQuality: LdacQuality? = null,
    val lastAppliedQuality: LdacQuality? = null,
    val selectableCount: Int? = null,
    val error: CodecAccessError? = null,
) {
    /** Distinguish a pending request from a confirmed change in the UI. */
    fun qualityIsObserved(quality: LdacQuality): Boolean =
        active.status == ObservationStatus.OBSERVED && active.value?.ldacQuality == quality
}
