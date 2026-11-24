package com.example.h1companion.h1link

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import com.example.h1companion.h1core.HeadsetSession
import com.example.h1companion.h1core.HeadsetState
import com.example.h1companion.h1core.NoiseMode
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns one RFCOMM link to the selected H1 and feeds bytes into the Bluetooth-free
 * h1core session. Written from the approach documented by H1 Control (MIT), but
 * deliberately NOT a copy of its controller: there is no periodic polling, no
 * automatic reconnect, and no setter path unless [allowWrites] is explicitly true.
 *
 * Session calls stay on one executor. The read loop only reads bytes and posts them
 * back to that executor, so parsing and state changes are single-threaded.
 */
class H1Link(
    private val context: Context,
    private val onState: (UiState) -> Unit,
    private val allowWrites: Boolean = false,
) {
    enum class Link { DISCONNECTED, CONNECTING, CONNECTED }

    data class UiState(
        val link: Link = Link.DISCONNECTED,
        val deviceAddress: String? = null,
        val headset: HeadsetState = HeadsetState(),
        val error: String? = null,
    )

    private val commands: ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "h1-commands") }
    private val reader: ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "h1-reader") }

    @Volatile
    private var socket: BluetoothSocket? = null

    @Volatile
    private var session: HeadsetSession? = null

    @Volatile
    private var state = UiState()

    fun currentState(): UiState = state

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Selects a bonded device address. Does not connect or pair automatically. */
    fun selectDevice(address: String) {
        disconnect()
        publish { it.copy(deviceAddress = address, error = null) }
    }

    /** Opens one RFCOMM connection to the previously selected device. */
    fun connect() {
        if (socket?.isConnected == true) return
        val address = state.deviceAddress
        if (address == null) {
            publish { it.copy(error = "Choose the headphones first.") }
            return
        }
        if (!hasPermission()) {
            publish { it.copy(error = "BLUETOOTH_CONNECT permission is required.") }
            return
        }
        publish { it.copy(link = Link.CONNECTING, error = null) }
        commands.execute { openSocket(address) }
    }

    /** Sends exactly one status request. No periodic polling is performed. */
    fun refresh() {
        commands.execute {
            val current = session
            if (current == null) {
                publish { it.copy(error = "Not connected.") }
            } else {
                current.requestStatus()
            }
        }
    }

    /** Disabled unless the owner explicitly unlocks writes and validates reversal. */
    fun setNoiseMode(mode: NoiseMode) {
        if (!allowWrites) {
            publish { it.copy(error = "Headset setters are disabled in this build.") }
            return
        }
        commands.execute { session?.setNoiseMode(mode) }
    }

    fun disconnect() {
        commands.execute { closeSocket() }
    }

    @SuppressLint("MissingPermission")
    private fun openSocket(address: String) {
        val adapter: BluetoothAdapter? =
            (context.getSystemService(BluetoothManager::class.java))?.adapter
        if (adapter == null || !adapter.isEnabled) {
            publish { it.copy(link = Link.DISCONNECTED, error = "Bluetooth is unavailable or off.") }
            return
        }
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(address)
        } catch (error: IllegalArgumentException) {
            publish { it.copy(link = Link.DISCONNECTED, error = "Invalid device address.") }
            return
        }
        val newSocket = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
        } catch (error: IOException) {
            publish { it.copy(link = Link.DISCONNECTED, error = "RFCOMM connection failed.") }
            return
        } catch (error: SecurityException) {
            publish { it.copy(link = Link.DISCONNECTED, error = "Bluetooth permission is required.") }
            return
        }

        val output = newSocket.outputStream
        val newSession = HeadsetSession { bytes ->
            try {
                output.write(bytes)
                output.flush()
            } catch (error: IOException) {
                publish { it.copy(error = "Write failed.") }
            }
        }
        newSession.onStateChanged = { headset -> publish { it.copy(headset = headset) } }
        newSession.onMessage = { /* redacted local diagnostics can be attached here */ }

        socket = newSocket
        session = newSession
        publish { it.copy(link = Link.CONNECTED, error = null) }
        reader.execute { readLoop(newSocket) }
    }

    private fun readLoop(activeSocket: BluetoothSocket) {
        val buffer = ByteArray(1024)
        try {
            val input = activeSocket.inputStream
            while (socket === activeSocket) {
                val count = input.read(buffer)
                if (count < 0) break
                val chunk = buffer.copyOf(count)
                commands.execute { session?.onBytes(chunk) }
            }
        } catch (error: IOException) {
            // A closed link is reported once by closeSocket; no automatic retry.
        } finally {
            if (socket === activeSocket) closeSocket()
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        session = null
        publish { it.copy(link = Link.DISCONNECTED) }
    }

    @Synchronized
    private fun publish(transform: (UiState) -> UiState) {
        val next = transform(state)
        state = next
        onState(next)
    }

    private companion object {
        /** Standard SPP UUID; on the H1 it maps to RFCOMM channel 1 (JieLi RCSP). */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }
}
