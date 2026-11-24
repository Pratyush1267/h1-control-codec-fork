package com.example.h1companion.codec

import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus

/** A phone-side observation, with no headset/local DSP claims attached. */
data class ActiveCodec(
    val codecName: String,
    val codecType: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val channelMode: Int,
    val codecSpecific1: Long,
) {
    val ldacQuality: LdacQuality? get() = LdacQuality.from(codecSpecific1)
}

/**
 * Reads codec status through reflection, mirroring BluetoothCodecStatusCompat in
 * the validated app. Missing methods/capabilities remain null rather than guessed.
 */
class CodecStatusReader {

    fun read(status: BluetoothCodecStatus?): ActiveCodec? {
        if (status == null) return null
        val config = invoke(status, "getCodecConfig", emptyArray()) as? BluetoothCodecConfig
            ?: return null
        return ActiveCodec(
            codecName = readString(config, "getCodecName") ?: "unknown",
            codecType = readInt(config, "getCodecType") ?: -1,
            sampleRate = readInt(config, "getSampleRate") ?: -1,
            bitsPerSample = readInt(config, "getBitsPerSample") ?: -1,
            channelMode = readInt(config, "getChannelMode") ?: -1,
            codecSpecific1 = readLong(config, "getCodecSpecific1") ?: -1L,
        )
    }

    fun selectableCount(status: BluetoothCodecStatus?): Int? {
        if (status == null) return null
        val list = invoke(status, "getCodecsSelectableCapabilities", emptyArray()) as? List<*>
        return list?.size
    }

    private fun invoke(receiver: Any, name: String, parameterTypes: Array<Class<*>>): Any? = runCatching {
        receiver.javaClass.getMethod(name, *parameterTypes).invoke(receiver)
    }.getOrNull()

    private fun readInt(receiver: Any, name: String): Int? =
        (invoke(receiver, name, emptyArray()) as? Number)?.toInt()

    private fun readLong(receiver: Any, name: String): Long? =
        (invoke(receiver, name, emptyArray()) as? Number)?.toLong()

    private fun readString(receiver: Any, name: String): String? =
        invoke(receiver, name, emptyArray()) as? String
}
