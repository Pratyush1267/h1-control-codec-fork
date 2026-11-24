package dev.madhuraj.h1

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothCodecConfig
import android.bluetooth.BluetoothCodecStatus
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reads the Bluetooth audio codec the phone is using for the headphones, and the best codec both support.
 *
 * Android only exposes the codec classes publicly (API 33+); the call that returns them is hidden, so this
 * tries it and otherwise relies on the broadcast Android sends whenever the codec changes.
 */
class CodecMonitor(private val context: Context) {

    data class Codec(
        val name: String,
        val sampleRates: List<Int>,
        val bitDepths: List<Int>,
        /** LDAC bitrate mode, when the codec is LDAC. */
        val ldacMode: String? = null,
        val ldacQuality: LdacQuality? = null,
    ) {
        val isLdac get() = name == "LDAC"
    }

    data class State(
        val current: Codec? = null,
        val best: Codec? = null,
        /** Why nothing is shown yet, if so. */
        val note: String? = null,
    )

    data class QualityState(
        val requested: LdacQuality? = null,
        val association: AssociationDecision? = null,
        val error: String? = null,
    ) {
        val canSelect get() = association == AssociationDecision.ASSOCIATED
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _qualityState = MutableStateFlow(QualityState())
    val qualityState: StateFlow<QualityState> = _qualityState.asStateFlow()

    private val qualityController = CodecQualityController(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _autoReapply = MutableStateFlow(prefs.getBoolean(KEY_AUTO_REAPPLY, false))
    val autoReapply: StateFlow<Boolean> = _autoReapply.asStateFlow()

    private var lastAutoApplyAt = 0L
    private var pendingReapply = false

    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    private var a2dp: BluetoothProfile? = null
    private var address: String? = null
    private var receiverRegistered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            if (device != null && device.address != address) return
            intent.getParcelableExtra(BluetoothCodecStatus.EXTRA_CODEC_STATUS, BluetoothCodecStatus::class.java)
                ?.let(::publish)
        }
    }

    fun start(deviceAddress: String?) {
        address = deviceAddress
        refreshAssociation()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            _state.value = State(note = "Codec details need Android 13 or later.")
            return
        }
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(ACTION_CODEC_CONFIG_CHANGED),
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }
        if (a2dp == null) {
            adapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    a2dp = proxy
                    refresh()
                    if (pendingReapply) {
                        pendingReapply = false
                        Log.i(TAG, "autoCheck proxy back -> apply")
                        applyStoredQualityIfNeeded()
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    a2dp = null
                }
            }, BluetoothProfile.A2DP)
        } else {
            refresh()
        }
    }

    fun stop() {
        if (receiverRegistered) {
            context.unregisterReceiver(receiver)
            receiverRegistered = false
        }
        a2dp?.let { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        a2dp = null
    }

    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    private fun currentStatus(): BluetoothCodecStatus? {
        val proxy = a2dp as? BluetoothA2dp ?: return null
        val device = address?.let { adapter?.getRemoteDevice(it) } ?: return null
        return try {
            proxy.javaClass.getMethod("getCodecStatus", BluetoothDevice::class.java)
                .invoke(proxy, device) as? BluetoothCodecStatus
        } catch (t: Throwable) {
            val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
            Log.i(TAG, "getCodecStatus unavailable: $cause")
            null
        }
    }

    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    fun refresh() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val status = currentStatus()
        if (status != null) {
            publish(status)
        } else if (_state.value.current == null) {
            _state.value = State(note = "This phone doesn't let apps read the codec directly. It appears here when the codec next changes, e.g. after reconnecting the headphones.")
        }
    }

    /** Updates the companion-association state used to gate direct quality changes. */
    fun refreshAssociation() {
        val device = address?.let { adapter?.getRemoteDevice(it) }
        val decision = device?.let { qualityController.associationDecision(it) }
            ?: AssociationDecision.NOT_ASSOCIATED
        _qualityState.value = _qualityState.value.copy(association = decision)
    }

    /** Sends one setter call, stores the preference, then reads back once. */
    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    fun setQuality(quality: LdacQuality) {
        val proxy = a2dp as? BluetoothA2dp
        val device = address?.let { adapter?.getRemoteDevice(it) }
        if (proxy == null || device == null) {
            _qualityState.value = _qualityState.value.copy(error = "Connect the headphones first")
            return
        }
        refreshAssociation()
        if (_qualityState.value.association != AssociationDecision.ASSOCIATED) {
            _qualityState.value = _qualityState.value.copy(
                error = "Associate this H1 first (system consent)",
            )
            return
        }
        val result = qualityController.setQuality(proxy, device, quality)
        result.fold(
            onSuccess = {
                prefs.edit().putLong(KEY_QUALITY, quality.codecSpecific1).apply()
                _qualityState.value = _qualityState.value.copy(requested = quality, error = null)
                refresh()
            },
            onFailure = { error ->
                _qualityState.value = _qualityState.value.copy(error = error.message ?: "Codec change failed")
            },
        )
    }

    fun setAutoReapply(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REAPPLY, enabled).apply()
        _autoReapply.value = enabled
        if (enabled) applyStoredQualityIfNeeded()
    }

    /** One guarded setter call after reconnect. No retries, no loops. */
    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    fun applyStoredQualityIfNeeded() {
        Log.i(TAG, "autoCheck begin auto=${_autoReapply.value} pref=${prefs.getLong(KEY_QUALITY, -1L)}")
        if (!_autoReapply.value) return
        val wanted = LdacQuality.fromAny(prefs.getLong(KEY_QUALITY, -1L)) ?: return
        val now = System.currentTimeMillis()
        if (now - lastAutoApplyAt < AUTO_COOLDOWN_MS) {
            Log.i(TAG, "autoCheck cooldown")
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        if (a2dp == null) {
            Log.i(TAG, "autoCheck proxy null -> pending")
            // onStop() closes the proxy; wait for it to come back, then apply once.
            pendingReapply = true
            address?.let { start(it) }
            return
        }
        val proxy = a2dp as? BluetoothA2dp ?: return
        val device = address?.let { adapter?.getRemoteDevice(it) }
        if (device == null) {
            Log.i(TAG, "autoCheck device null address=$address")
            return
        }
        refreshAssociation()
        if (_qualityState.value.association != AssociationDecision.ASSOCIATED) {
            Log.i(TAG, "autoCheck association=${_qualityState.value.association}")
            return
        }

        val status = currentStatus()
        if (status == null) {
            Log.i(TAG, "autoCheck status null")
            return
        }
        val config = status.codecConfig
        if (config == null) {
            Log.i(TAG, "autoCheck config null")
            return
        }
        Log.i(TAG, "autoCheck current=${config.codecSpecific1} type=${config.codecType} wanted=${wanted.codecSpecific1}")
        if (config.codecType != 4) return // LDAC only
        if (LdacQuality.fromAny(config.codecSpecific1) == wanted) return

        lastAutoApplyAt = now
        qualityController.setQuality(proxy, device, wanted).fold(
            onSuccess = {
                _qualityState.value = _qualityState.value.copy(requested = wanted, error = null)
                refresh()
            },
            onFailure = { error ->
                _qualityState.value = _qualityState.value.copy(
                    error = "Auto reapply failed: ${error.message ?: "unknown"}",
                )
            },
        )
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun publish(status: BluetoothCodecStatus) {
        val current = status.codecConfig?.toCodec(includeLdacMode = true)
        val best = status.codecsSelectableCapabilities.maxByOrNull { rank(it.codecType) }?.toCodec(includeLdacMode = false)
        _state.value = State(current = current, best = best)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun BluetoothCodecConfig.toCodec(includeLdacMode: Boolean): Codec {
        val name = codecName(codecType)
        return Codec(
            name = name,
            sampleRates = SAMPLE_RATES.filter { (flag, _) -> sampleRate and flag != 0 }.map { it.second },
            bitDepths = BIT_DEPTHS.filter { (flag, _) -> bitsPerSample and flag != 0 }.map { it.second },
            ldacMode = if (includeLdacMode && name == "LDAC") ldacMode(codecSpecific1) else null,
            ldacQuality = if (includeLdacMode && name == "LDAC") LdacQuality.from(codecSpecific1) else null,
        )
    }

    private companion object {
        const val TAG = "H1Codec"
        const val PREFS_NAME = "h1_quality"
        const val KEY_QUALITY = "codec_specific1"
        const val KEY_AUTO_REAPPLY = "auto_reapply"
        const val AUTO_COOLDOWN_MS = 10_000L
        const val ACTION_CODEC_CONFIG_CHANGED = "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED"

        val SAMPLE_RATES = listOf(0x01 to 44_100, 0x02 to 48_000, 0x04 to 88_200, 0x08 to 96_000, 0x10 to 176_400, 0x20 to 192_000)
        val BIT_DEPTHS = listOf(0x1 to 16, 0x2 to 24, 0x4 to 32)

        fun codecName(type: Int) = when (type) {
            0 -> "SBC"
            1 -> "AAC"
            2 -> "aptX"
            3 -> "aptX HD"
            4 -> "LDAC"
            6 -> "LC3"
            7 -> "Opus"
            18, 19 -> "LHDC"
            else -> "Codec $type"
        }

        /** Rough quality order, used to pick the best codec both devices support. */
        fun rank(type: Int) = when (type) {
            4, 18, 19 -> 5
            3 -> 4
            2 -> 3
            1 -> 2
            0 -> 1
            else -> 0
        }

        /** AOSP stores the LDAC mode as 1000–1003; some phones store 0–3. */
        fun ldacMode(value: Long) = when (value) {
            1000L, 0L -> "990 kbps (best quality)"
            1001L, 1L -> "660 kbps (balanced)"
            1002L, 2L -> "330 kbps (connection first)"
            1003L, 3L -> "Adaptive bitrate"
            else -> null
        }
    }
}
