package com.example.h1companion.codec

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothDevice

/** Failure modes kept explicit so the UI can distinguish unavailable from denied. */
sealed class CodecAccessError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MethodUnavailable(val method: String, cause: Throwable? = null) :
        CodecAccessError("$method is not available on this platform", cause)

    class PermissionDenied(cause: Throwable? = null) :
        CodecAccessError("Bluetooth/companion authorization was denied", cause)

    class InvalidConfig(cause: Throwable? = null) :
        CodecAccessError("The service rejected the codec configuration", cause)

    class AssociationMissing(cause: Throwable? = null) :
        CodecAccessError("No eligible companion association for this headset", cause)

    class Unavailable(cause: Throwable? = null) :
        CodecAccessError("Codec access is unavailable on this build", cause)
}

/**
 * Reflection-only access to the hidden-but-public A2DP codec methods.
 *
 * The owner's Android 16 phone has BLUETOOTH_CONNECT plus a user-approved companion
 * association for the H1. No root, ADB grant or BLUETOOTH_PRIVILEGED is involved.
 * The A2DP methods are resolved with getMethod and invoked directly; setAccessible is
 * only used on the BluetoothCodecConfig constructor, matching the validated app.
 */
class CodecController(
    private val readGate: (BluetoothDevice) -> Boolean = { true },
    private val writeGate: (BluetoothDevice) -> Boolean = { true },
) {

    private val getCodecStatus: Result<java.lang.reflect.Method> by lazy {
        runCatching {
            BluetoothA2dp::class.java.getMethod("getCodecStatus", BluetoothDevice::class.java)
        }.recoverCatching { throw CodecAccessError.MethodUnavailable("getCodecStatus", it) }
    }

    private val setCodecConfigPreference: Result<java.lang.reflect.Method> by lazy {
        runCatching {
            BluetoothA2dp::class.java.getMethod(
                "setCodecConfigPreference",
                BluetoothDevice::class.java,
                BluetoothCodecConfig::class.java,
            )
        }.recoverCatching {
            throw CodecAccessError.MethodUnavailable("setCodecConfigPreference", it)
        }
    }

    fun readStatus(a2dp: BluetoothA2dp?, device: BluetoothDevice?): Result<BluetoothCodecStatus?> {
        if (a2dp == null || device == null) return Result.success(null)
        if (!readGate(device)) {
            return Result.failure(CodecAccessError.AssociationMissing())
        }
        val method = getCodecStatus.getOrNull()
            ?: return Result.failure(CodecAccessError.MethodUnavailable("getCodecStatus"))
        return runCatching {
            method.invoke(a2dp, device) as? BluetoothCodecStatus
        }.mapError()
    }

    fun setQuality(
        a2dp: BluetoothA2dp?,
        device: BluetoothDevice?,
        quality: LdacQuality,
    ): Result<Unit> {
        if (a2dp == null || device == null) return Result.failure(CodecAccessError.Unavailable())
        if (!writeGate(device)) {
            return Result.failure(CodecAccessError.AssociationMissing())
        }
        if (quality !in LdacQuality.settableValues()) {
            return Result.failure(CodecAccessError.InvalidConfig())
        }
        val config = CodecConfigFactory.buildLdac(quality).getOrNull()
            ?: return Result.failure(CodecAccessError.InvalidConfig())
        val method = setCodecConfigPreference.getOrNull()
            ?: return Result.failure(
                CodecAccessError.MethodUnavailable("setCodecConfigPreference")
            )
        return runCatching {
            method.invoke(a2dp, device, config)
            Unit
        }.mapError()
    }

    private fun <T> Result<T>.mapError(): Result<T> = recoverCatching { error ->
        val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
        throw when {
            cause is SecurityException -> CodecAccessError.PermissionDenied(cause)
            cause is IllegalArgumentException -> CodecAccessError.InvalidConfig(cause)
            else -> CodecAccessError.Unavailable(cause)
        }
    }
}
