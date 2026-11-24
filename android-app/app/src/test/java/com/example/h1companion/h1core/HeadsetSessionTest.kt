package com.example.h1companion.h1core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adapted from H1 Control's HeadsetSessionTest (MIT, Copyright (c) 2026 Madhuraj),
 * pinned to commit a7d08b045164996c7b5153660eb7d640898b3242. Byte strings are the
 * upstream author's claimed real-H1 firmware 2.4.0 capture, not an independent observation.
 * Assertions were changed to check that a requested mode stays pending until a push.
 */
class HeadsetSessionTest {
    private fun hex(s: String): ByteArray =
        s.split(" ").filter { it.isNotBlank() }.map { it.toInt(16).toByte() }.toByteArray()

    private val statusResponse = hex(
        "FE DC BA 00 07 00 58 00 02 FF 02 00 26 16 02 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 " +
            "02 06 00 02 09 00 08 0A 00 00 00 00 00 00 00 0A 0D 00 00 00 00 00 00 00 00 00 1D 0E 03 00 00 00 00 00 00 00 " +
            "00 00 01 40 00 00 00 40 00 00 00 02 40 00 00 00 40 00 00 00 02 0F 00 EF",
    )

    private val ancAckAndPush = hex(
        "FE DC BA 00 08 00 02 00 02 EF FE DC BA 80 09 00 0D 01 FF 0A 0D 01 40 00 00 00 40 00 00 00 EF",
    )

    private fun session(sent: MutableList<ByteArray> = mutableListOf()) = HeadsetSession { sent += it }

    @Test
    fun `status request matches the verified probe bytes`() {
        assertEquals("FE DC BA C0 07 00 06 01 FF FF FF FF FF EF", Commands.getStatus(1).toHex())
    }

    @Test
    fun `parses battery and supported modes from the captured status`() {
        val s = session()
        s.onBytes(statusResponse)
        assertEquals(38, s.state.batteryPercent)
        assertEquals(NoiseMode.OFF, s.state.noiseMode)
        assertEquals(
            listOf(NoiseMode.OFF, NoiseMode.ANC, NoiseMode.TRANSPARENCY),
            s.state.supportedNoiseModes,
        )
        assertNull(s.state.gameMode)
    }

    @Test
    fun `requested mode stays pending until the headset reports it`() {
        val sent = mutableListOf<ByteArray>()
        val s = session(sent)
        s.onBytes(statusResponse)

        s.setNoiseMode(NoiseMode.ANC)
        assertEquals(NoiseMode.ANC, s.state.pendingNoiseMode)
        assertEquals(NoiseMode.OFF, s.state.noiseMode)
        assertEquals(
            "FE DC BA C0 08 00 0D 01 FF 0A 0D 01 40 00 00 00 40 00 00 00 EF",
            sent.last().toHex(),
        )

        s.onBytes(ancAckAndPush)
        assertEquals(NoiseMode.ANC, s.state.noiseMode)
        assertNull(s.state.pendingNoiseMode)
    }

    @Test
    fun `handles frames split across reads`() {
        val s = session()
        statusResponse.toList().chunked(5).forEach { s.onBytes(it.toByteArray()) }
        assertEquals(38, s.state.batteryPercent)
    }

    @Test
    fun `button events are last seen values not a fresh query`() {
        val s = session()
        s.onBytes(hex("85 86 87 02 04 01"))
        assertEquals(true, s.state.gameMode)
        s.onBytes(hex("85 86 87 02 04 00 85 86 87 02 05 06"))
        assertEquals(false, s.state.gameMode)
        assertEquals(true, s.state.spatialAudio)
        assertEquals(mapOf(4 to 0, 5 to 6), s.state.buttonFeatures)
    }

    @Test
    fun `skips unknown bytes and answers commands that want a response`() {
        val parser = FrameParser()
        val messages = parser.feed(hex("0C 07 55 55 AA BE 36 00 04 00 01 00 00 85 86 87 02 04 01"))
        assertTrue(messages[0] is Incoming.Unknown)
        assertTrue(messages[1] is Incoming.RealmeEvent)

        val sent = mutableListOf<ByteArray>()
        val s = session(sent)
        s.onBytes(hex("FE DC BA C0 09 00 02 07 FF EF"))
        assertEquals("FE DC BA 00 09 00 02 00 07 EF", sent.single().toHex())
    }
}
