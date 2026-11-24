package com.example.h1companion.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests: no Android runtime needed. */
class CodecContractTest {

    @Test
    fun `quality values match the observed codecSpecific1 policy values`() {
        assertEquals(1000L, LdacQuality.OPTIMIZED_AUDIO.codecSpecific1)
        assertEquals(1001L, LdacQuality.BALANCED.codecSpecific1)
        assertEquals(1002L, LdacQuality.OPTIMIZED_CONNECTION.codecSpecific1)
        assertEquals(1003L, LdacQuality.BEST_EFFORT.codecSpecific1)
        assertEquals(LdacQuality.BEST_EFFORT, LdacQuality.from(1003L))
        assertEquals(null, LdacQuality.from(9999L))
    }

    @Test
    fun `default quality is labelled but is not a setter target`() {
        assertFalse(LdacQuality.DEFAULT in LdacQuality.settableValues())
        assertEquals(4, LdacQuality.settableValues().size)
    }

    @Test
    fun `pending request is not reported as observed`() {
        val state = CodecUiState(
            active = Observation(null, ObservationStatus.PENDING, "reflection", null),
            requestedQuality = LdacQuality.BALANCED,
        )
        assertFalse(state.qualityIsObserved(LdacQuality.BALANCED))
        assertEquals(LdacQuality.BALANCED, state.requestedQuality)
    }

    @Test
    fun `observed state reports only the current value`() {
        val observed = ActiveCodec(
            codecName = "LDAC",
            codecType = 4,
            sampleRate = 96000,
            bitsPerSample = 32,
            channelMode = 2,
            codecSpecific1 = 1000,
        )
        val state = CodecUiState(
            active = Observation(observed, ObservationStatus.OBSERVED, "getCodecStatus", 1L),
            requestedQuality = LdacQuality.BALANCED,
        )
        assertTrue(state.qualityIsObserved(LdacQuality.OPTIMIZED_AUDIO))
        assertFalse(state.qualityIsObserved(LdacQuality.BALANCED))
    }
}
