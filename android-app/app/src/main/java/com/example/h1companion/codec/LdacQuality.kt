package com.example.h1companion.codec

/**
 * LDAC playback-quality policies.
 *
 * Values are the codecSpecific1 values observed in the installed 2.6.1 app and the
 * active Android A2DP configuration on the tested Android 16 phone. They describe a
 * configured policy, not a measured radio bitrate.
 */
enum class LdacQuality(val codecSpecific1: Long, val label: String) {
    OPTIMIZED_AUDIO(1000, "Optimized for audio quality (990/909 kbps)"),
    BALANCED(1001, "Balanced audio and connection quality (660/606 kbps)"),
    OPTIMIZED_CONNECTION(1002, "Optimized for connection quality (330/303 kbps)"),
    BEST_EFFORT(1003, "Best effort (adaptive bit rate)"),
    DEFAULT(0, "Default");

    companion object {
        fun from(codecSpecific1: Long): LdacQuality? =
            LdacQuality.entries.firstOrNull { it.codecSpecific1 == codecSpecific1 }

        /** Only explicit policies are valid setter targets; DEFAULT is a fallback label. */
        fun settableValues(): List<LdacQuality> = listOf(
            OPTIMIZED_AUDIO,
            BALANCED,
            OPTIMIZED_CONNECTION,
            BEST_EFFORT,
        )
    }
}
