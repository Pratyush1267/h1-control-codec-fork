package com.example.h1companion.codec

import android.bluetooth.BluetoothCodecConfig

/**
 * Builds an A2DP codec configuration for the hidden framework setter.
 *
 * The platform 9-argument constructor order was confirmed twice:
 *  - decompiled CodecConfigFactory.build in the owner's installed 2.6.1 app passes
 *    createLegacy(codecType, 1000000, sampleRate, bitsPerSample, channelMode,
 *    codecSpecific1, 0, 0, 0);
 *  - an incorrectly ordered config was rejected by AOSP A2dpCodecConfig as
 *    "invalid codec" during the bounded shell experiment.
 *
 * This class deliberately does not request BLUETOOTH_PRIVILEGED or use a hidden-API
 * bypass. Unsupported platforms return a failed Result instead of throwing at call sites.
 */
internal object CodecConfigFactory {
    private const val CODEC_TYPE_LDAC = 4
    private const val SAMPLE_RATE_96000 = 0x8
    private const val BITS_PER_SAMPLE_32 = 0x4
    private const val CHANNEL_MODE_STEREO = 0x2
    private const val SELECTED_CODEC_PRIORITY = 1_000_000

    fun buildLdac(
        quality: LdacQuality,
        sampleRate: Int = SAMPLE_RATE_96000,
        bitsPerSample: Int = BITS_PER_SAMPLE_32,
        channelMode: Int = CHANNEL_MODE_STEREO,
    ): Result<BluetoothCodecConfig> = runCatching {
        val constructor = BluetoothCodecConfig::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
        )
        constructor.isAccessible = true
        constructor.newInstance(
            CODEC_TYPE_LDAC,
            SELECTED_CODEC_PRIORITY,
            sampleRate,
            bitsPerSample,
            channelMode,
            quality.codecSpecific1,
            0L,
            0L,
            0L,
        ) as BluetoothCodecConfig
    }
}
