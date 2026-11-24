package com.example.h1companion.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.h1companion.codec.AssociationCallbacks
import com.example.h1companion.codec.CompanionOnboarding
import com.example.h1companion.h1link.H1Link
import java.util.concurrent.Executors

/**
 * Minimal manual test surface. No headset setters are exposed.
 * All Bluetooth actions require a button press by the user.
 */
class MainActivity : Activity() {
    private lateinit var link: H1Link
    private lateinit var status: TextView
    private lateinit var addressField: EditText
    private var onboarding: CompanionOnboarding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        link = H1Link(this, { state -> runOnUiThread { status.text = describe(state) } })

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setPadding(24, 24, 24, 24)
        }
        status = TextView(this).apply { textSize = 16f; setTextColor(Color.BLACK) }
        addressField = EditText(this).apply { hint = "H1 Bluetooth MAC address" }

        layout.addView(status)
        layout.addView(addressField)
        layout.addView(button("Grant Bluetooth permission") { requestBluetoothPermission() })
        layout.addView(button("Associate H1 (system consent)") { associate() })
        layout.addView(button("Connect RFCOMM") { connect() })
        layout.addView(button("Refresh status (one query)") { link.refresh() })
        layout.addView(button("Disconnect") { link.disconnect() })
        setContentView(layout)
        status.text = describe(link.currentState())
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply { text = label; setOnClickListener { action() } }

    private fun describe(state: H1Link.UiState): String = buildString {
        append("Link: ").append(state.link).append('\n')
        append("Device: ").append(state.deviceAddress ?: "not selected").append('\n')
        append("Battery: ").append(state.headset.batteryPercent ?: "unknown").append('\n')
        append("Noise mode: ").append(state.headset.noiseMode ?: "unknown").append('\n')
        append("Pending: ").append(state.headset.pendingNoiseMode ?: "none").append('\n')
        append("Error: ").append(state.error ?: "none")
    }

    private fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQ_PERMISSION)
        } else {
            Toast.makeText(this, "Bluetooth permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectedDevice(): BluetoothDevice? {
        val address = addressField.text.toString().trim()
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        return try {
            adapter?.getRemoteDevice(address)
        } catch (error: IllegalArgumentException) {
            Toast.makeText(this, "Invalid MAC address", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun connect() {
        val device = selectedDevice() ?: return
        link.selectDevice(device.address)
        link.connect()
    }

    private fun associate() {
        if (Build.VERSION.SDK_INT < 33) {
            Toast.makeText(this, "Companion association requires Android 13+", Toast.LENGTH_SHORT).show()
            return
        }
        val device = selectedDevice() ?: return
        val manager = getSystemService(CompanionDeviceManager::class.java) ?: return
        onboarding = CompanionOnboarding(this, manager, Executors.newSingleThreadExecutor(), REQ_ASSOCIATION)
        onboarding?.start(device, "realme TechLife Studio H1", object : AssociationCallbacks {
            override fun onUserConsentRequired() {
                runOnUiThread { status.text = "System consent dialog shown" }
            }

            override fun onReady(info: android.companion.AssociationInfo?) {
                runOnUiThread { status.text = "Association ready" }
            }

            override fun onFailed(reason: String) {
                runOnUiThread { status.text = "Association failed: $reason" }
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSION) {
            Toast.makeText(this, "Permission result received", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ASSOCIATION) {
            val info = onboarding?.associationFromResult(data)
            runOnUiThread { status.text = if (info != null) "Association result received" else "Association result cancelled" }
        }
    }

    private companion object {
        const val REQ_PERMISSION = 1001
        const val REQ_ASSOCIATION = 1002
    }
}
