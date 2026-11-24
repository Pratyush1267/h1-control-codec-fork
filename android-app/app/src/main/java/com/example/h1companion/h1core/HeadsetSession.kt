// Adapted from H1 Control (https://github.com/madhuraj0/h1-control), commit
// a7d08b045164996c7b5153660eb7d640898b3242, MIT License, Copyright (c) 2026 Madhuraj.
// Changes: package renamed; setNoiseMode no longer optimistically reports success.
// A requested mode stays pending until the headset reports it. See
// research/realme-studio-h1/THIRD-PARTY-NOTICES.md.
package com.example.h1companion.h1core

enum class NoiseMode(val code: Int) {
    OFF(0),
    ANC(1),
    TRANSPARENCY(2);

    companion object {
        fun fromCode(code: Int): NoiseMode? = NoiseMode.entries.firstOrNull { it.code == code }
    }
}

/** Everything known about the headphones. `null` means "not reported yet". */
data class HeadsetState(
    val batteryPercent: Int? = null,
    /** Last mode actually reported by the headphones, not a requested value. */
    val noiseMode: NoiseMode? = null,
    /** A set command was sent; its result is not yet observed. */
    val pendingNoiseMode: NoiseMode? = null,
    val supportedNoiseModes: List<NoiseMode> = emptyList(),
    /** Only known after the user toggles it with the button. Can be stale. */
    val gameMode: Boolean? = null,
    /** Only known after the user toggles it with the button. Can be stale. */
    val spatialAudio: Boolean? = null,
    /** Last value of every realme button notification seen, keyed by feature id. */
    val buttonFeatures: Map<Int, Int> = emptyMap(),
)

/** Builders for the commands the app sends. */
object Commands {
    private val ALL_ATTRIBUTES = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())

    /** Reads every attribute of the public function group (battery, noise mode, …). */
    fun getStatus(sn: Int): ByteArray =
        Rcsp.encodeCommand(Rcsp.Op.GET_SYS_INFO, sn, byteArrayOf(Rcsp.FUNCTION_PUBLIC.toByte()) + ALL_ATTRIBUTES)

    /** Reads the device's capability report (feature flags, firmware, …). Diagnostic. */
    fun getTargetInfo(sn: Int): ByteArray =
        Rcsp.encodeCommand(Rcsp.Op.TARGET_INFO, sn, ALL_ATTRIBUTES + byteArrayOf(0))

    /** Sets the noise mode. [gains] is the 8-byte gain block the headphones list for that mode. */
    fun setNoiseMode(sn: Int, mode: NoiseMode, gains: ByteArray): ByteArray {
        require(gains.size == 8) { "gains must be 8 bytes" }
        val value = byteArrayOf(mode.code.toByte()) + gains
        val tlv = byteArrayOf((value.size + 1).toByte(), Rcsp.Attr.CURRENT_NOISE_MODE.toByte()) + value
        return Rcsp.encodeCommand(Rcsp.Op.SET_SYS_INFO, sn, byteArrayOf(Rcsp.FUNCTION_PUBLIC.toByte()) + tlv)
    }

    internal val DEFAULT_ACTIVE_GAINS = byteArrayOf(0x40, 0, 0, 0, 0x40, 0, 0, 0)
    internal val OFF_GAINS = ByteArray(8)
}

/** Parses the `<len> <attr> <data…>` list that follows the function byte in sys-info payloads. */
object SysInfo {
    fun parse(payload: ByteArray): Map<Int, ByteArray> {
        val result = mutableMapOf<Int, ByteArray>()
        var i = 1 // payload[0] is the function group
        while (i + 1 < payload.size) {
            val len = payload[i].u()
            if (len == 0 || i + 1 + len > payload.size) break
            result[payload[i + 1].u()] = payload.copyOfRange(i + 2, i + 1 + len)
            i += 1 + len
        }
        return result
    }
}

/**
 * Protocol state machine for one connection. Has no Bluetooth code: the platform feeds received bytes
 * into [onBytes] and supplies [transport] to write bytes out. Not thread-safe; call it from one thread.
 */
class HeadsetSession(private val transport: (ByteArray) -> Unit) {
    private val parser = FrameParser()
    private var sn = 0
    private val gains = mutableMapOf<NoiseMode, ByteArray>()

    var state = HeadsetState()
        private set

    var onStateChanged: ((HeadsetState) -> Unit)? = null

    /** Every decoded message, for logging. */
    var onMessage: ((Incoming) -> Unit)? = null

    fun requestStatus() = transport(Commands.getStatus(nextSn()))

    fun requestTargetInfo() = transport(Commands.getTargetInfo(nextSn()))

    /** Sends an arbitrary RCSP command. Diagnostic use only. */
    fun sendRaw(opcode: Int, payload: ByteArray) = transport(Rcsp.encodeCommand(opcode, nextSn(), payload))

    /** Requests a mode change. Observed state is not changed until the headset reports it. */
    fun setNoiseMode(mode: NoiseMode) {
        transport(Commands.setNoiseMode(nextSn(), mode, gainsFor(mode)))
        update(state.copy(pendingNoiseMode = mode))
    }

    fun onBytes(data: ByteArray) {
        parser.feed(data).forEach(::handle)
    }

    private fun handle(message: Incoming) {
        onMessage?.invoke(message)
        when (message) {
            is Incoming.RcspResponse -> {
                if (message.opcode == Rcsp.Op.GET_SYS_INFO && message.status == Rcsp.STATUS_OK) {
                    applySysInfo(message.payload)
                }
                if (message.opcode == Rcsp.Op.SET_SYS_INFO && message.status != Rcsp.STATUS_OK) {
                    update(state.copy(pendingNoiseMode = null))
                }
            }
            is Incoming.RcspCommand -> {
                if (message.opcode == Rcsp.Op.SYS_INFO_PUSH) applySysInfo(message.payload)
                if (message.needsResponse) transport(Rcsp.encodeResponse(message.opcode, Rcsp.STATUS_OK, message.sn))
            }
            is Incoming.RealmeEvent -> {
                val feature = message.feature ?: return
                val value = message.value ?: return
                var next = state.copy(buttonFeatures = state.buttonFeatures + (feature to value))
                when (feature) {
                    RealmeFrame.FEATURE_GAME_MODE -> next = next.copy(gameMode = value != 0)
                    RealmeFrame.FEATURE_SPATIAL_AUDIO -> next = next.copy(spatialAudio = value != 0)
                }
                update(next)
            }
            is Incoming.Unknown -> Unit
        }
    }

    private fun applySysInfo(payload: ByteArray) {
        if (payload.isEmpty() || payload[0].u() != Rcsp.FUNCTION_PUBLIC) return
        var next = state
        for ((attr, data) in SysInfo.parse(payload)) {
            when (attr) {
                Rcsp.Attr.BATTERY -> data.firstOrNull()?.let { next = next.copy(batteryPercent = it.u().coerceIn(0, 100)) }
                Rcsp.Attr.CURRENT_NOISE_MODE -> {
                    NoiseMode.fromCode(data.firstOrNull()?.u() ?: -1)?.let { observed ->
                        next = next.copy(
                            noiseMode = observed,
                            pendingNoiseMode = if (next.pendingNoiseMode == observed) null else next.pendingNoiseMode,
                        )
                    }
                }
                Rcsp.Attr.ALL_NOISE_MODES -> next = next.copy(supportedNoiseModes = parseAllNoiseModes(data))
            }
        }
        update(next)
    }

    /** `<count>` then `count` entries of `<mode> <8 gain bytes>`. */
    private fun parseAllNoiseModes(data: ByteArray): List<NoiseMode> {
        val count = data.firstOrNull()?.u() ?: return emptyList()
        val modes = mutableListOf<NoiseMode>()
        for (i in 0 until count) {
            val start = 1 + i * 9
            if (start + 9 > data.size) break
            val mode = NoiseMode.fromCode(data[start].u()) ?: continue
            gains[mode] = data.copyOfRange(start + 1, start + 9)
            modes += mode
        }
        return modes
    }

    private fun gainsFor(mode: NoiseMode): ByteArray =
        gains[mode] ?: if (mode == NoiseMode.OFF) Commands.OFF_GAINS else Commands.DEFAULT_ACTIVE_GAINS

    private fun nextSn(): Int {
        sn = (sn + 1) and 0xFF
        return sn
    }

    private fun update(next: HeadsetState) {
        if (next == state) return
        state = next
        onStateChanged?.invoke(next)
    }
}
