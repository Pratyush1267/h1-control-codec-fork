package dev.madhuraj.h1

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Context
import android.os.Build
import java.util.Locale

/** LDAC policies observed in the owner's Android 16 A2DP configuration. */
enum class LdacQuality(val codecSpecific1: Long, val label: String) {
    OPTIMIZED_AUDIO(1000, "Audio quality (990/909 kbps)"),
    BALANCED(1001, "Balanced (660/606 kbps)"),
    OPTIMIZED_CONNECTION(1002, "Connection (330/303 kbps)"),
    BEST_EFFORT(1003, "Best effort (adaptive)");

    companion object {
        fun from(value: Long): LdacQuality? = LdacQuality.entries.firstOrNull { it.codecSpecific1 == value }

        /** Android also uses legacy 0-3 values for the same policies after reconnect. */
        fun fromAny(value: Long): LdacQuality? = when (value) {
            1000L, 0L -> OPTIMIZED_AUDIO
            1001L, 1L -> BALANCED
            1002L, 2L -> OPTIMIZED_CONNECTION
            1003L, 3L -> BEST_EFFORT
            else -> from(value)
        }
    }
}

data class AssociationRecord(val mac: String?, val selfManaged: Boolean, val id: Int? = null)

enum class AssociationDecision {
    ASSOCIATED,
    NOT_ASSOCIATED,
    SELF_MANAGED_NOT_ACCEPTED,
}

/** The AOSP codec Binder gate accepts only non-self-managed, MAC-matching associations. */
object AssociationEvaluator {
    fun evaluate(records: List<AssociationRecord>, deviceMac: String): AssociationDecision {
        val expected = deviceMac.lowercase(Locale.ROOT)
        val matching = records.filter { it.mac?.lowercase(Locale.ROOT) == expected }
        if (matching.isEmpty()) return AssociationDecision.NOT_ASSOCIATED
        if (matching.any { !it.selfManaged }) return AssociationDecision.ASSOCIATED
        return AssociationDecision.SELF_MANAGED_NOT_ACCEPTED
    }
}

/** Version qualification from retained AOSP snapshots; not an OEM/Mainline guarantee. */
object AssociationPolicy {
    fun requiredForSet(sdkInt: Int): Boolean = sdkInt >= 34
}

/**
 * Reflection-based codec setter plus the companion-association check.
 * No root, privileged permission or hidden-API bypass is used.
 */
class CodecQualityController(private val context: Context) {

    fun associationDecision(device: BluetoothDevice): AssociationDecision {
        if (Build.VERSION.SDK_INT < 33) {
            // Snapshots for Android 12/13 have no CDM gate on these Binder methods.
            return AssociationDecision.ASSOCIATED
        }
        val manager = context.getSystemService(CompanionDeviceManager::class.java)
            ?: return AssociationDecision.NOT_ASSOCIATED
        val records = manager.getMyAssociations().map { info ->
            // isSelfManaged() itself is API 35; this fork compiles against SDK 34.
            val selfManaged = if (Build.VERSION.SDK_INT >= 35) {
                runCatching {
                    info.javaClass.getMethod("isSelfManaged").invoke(info) as? Boolean ?: false
                }.getOrDefault(false)
            } else {
                false
            }
            AssociationRecord(info.deviceMacAddress?.toString(), selfManaged, info.id)
        }
        return AssociationEvaluator.evaluate(records, device.address)
    }

    fun canSet(device: BluetoothDevice): Boolean =
        !AssociationPolicy.requiredForSet(Build.VERSION.SDK_INT) ||
            associationDecision(device) == AssociationDecision.ASSOCIATED

    fun setQuality(
        a2dp: BluetoothA2dp,
        device: BluetoothDevice,
        quality: LdacQuality,
    ): Result<Unit> = runCatching {
        if (!canSet(device)) error("Associate this H1 before changing playback quality")

        // Platform 9-arg order: codecType, priority, sampleRate, bits, channelMode, cs1..4.
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
        val config = constructor.newInstance(
            4,            // LDAC
            1_000_000,    // selected codec priority
            0x08,         // 96 kHz
            0x04,         // 32-bit
            0x02,         // stereo
            quality.codecSpecific1,
            0L, 0L, 0L,
        ) as BluetoothCodecConfig

        val setter = BluetoothA2dp::class.java.getMethod(
            "setCodecConfigPreference",
            BluetoothDevice::class.java,
            BluetoothCodecConfig::class.java,
        )
        setter.invoke(a2dp, device, config)
        Unit
    }
}
