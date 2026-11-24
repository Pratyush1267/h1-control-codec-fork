package dev.madhuraj.h1

import android.Manifest
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.madhuraj.h1.ui.H1Theme
import dev.madhuraj.h1.ui.MainScreen
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val controller get() = headsetController
    private var hasPermission by mutableStateOf(false)
    private val codecMonitor get() = (application as H1App).codecMonitor
    private val soundCheck = SoundCheck()
    private var association: CompanionAssociation? = null

    private companion object {
        const val REQ_ASSOCIATION = 1002
    }

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        onPermissionChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            H1Theme {
                val state by controller.state.collectAsStateWithLifecycle()
                val codec by codecMonitor.state.collectAsStateWithLifecycle()
                val quality by codecMonitor.qualityState.collectAsStateWithLifecycle()
                val autoReapply by codecMonitor.autoReapply.collectAsStateWithLifecycle()
                val wired by (application as H1App).wiredAudio.state.collectAsStateWithLifecycle()
                MainScreen(
                    state = state,
                    hasPermission = hasPermission,
                    onRequestPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionRequest.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                    },
                    pairedDevices = controller::pairedDevices,
                    onSelectDevice = { address ->
                        controller.selectDevice(address)
                        codecMonitor.start(address)
                    },
                    onConnect = controller::connect,
                    onRefreshHeadset = {
                        controller.refresh()
                        codecMonitor.refresh()
                    },
                    onNoiseMode = controller::setNoiseMode,
                    codec = codec,
                    quality = quality,
                    onQuality = codecMonitor::setQuality,
                    onAssociate = { startAssociation() },
                    autoReapply = autoReapply,
                    onAutoReapply = codecMonitor::setAutoReapply,
                    wired = wired,
                    soundCheck = soundCheck,
                )
            }
        }
    }

    private fun startAssociation() {
        if (Build.VERSION.SDK_INT < 33) return
        val address = controller.state.value.deviceAddress ?: return
        val device = getSystemService(BluetoothManager::class.java)?.adapter?.getRemoteDevice(address) ?: return
        val manager = getSystemService(CompanionDeviceManager::class.java) ?: return
        association = CompanionAssociation(this, manager, Executors.newSingleThreadExecutor(), REQ_ASSOCIATION)
        association?.start(device, object : AssociationUi {
            override fun onPending() = Unit
            override fun onCreated(info: android.companion.AssociationInfo?) = codecMonitor.refreshAssociation()
            override fun onFailed(reason: String) = Unit
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_ASSOCIATION) codecMonitor.refreshAssociation()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Debug hook: `adb shell am start -n dev.madhuraj.h1/.MainActivity --ez probe true`
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable && intent.getBooleanExtra("probe", false)) controller.probe()
        // Debug hook: `adb shell am start -n dev.madhuraj.h1/.MainActivity --es op C0 --es payload 020500`
        val op = intent.getStringExtra("op")?.toIntOrNull(16)
        val payload = intent.getStringExtra("payload")?.chunked(2)?.mapNotNull { it.toIntOrNull(16)?.toByte() }
        if (debuggable && op != null && payload != null) controller.sendRaw(op, payload.toByteArray())
    }

    override fun onStart() {
        super.onStart()
        onPermissionChanged()
    }

    override fun onStop() {
        super.onStop()
        codecMonitor.stop()
        soundCheck.stop()
    }

    private fun onPermissionChanged() {
        hasPermission = controller.hasPermission()
        if (!hasPermission) return

        controller.autoSelect()
        val address = controller.state.value.deviceAddress
        // On open: refresh() also brings up the control link; one status query only.
        controller.refresh()
        codecMonitor.start(address)
    }
}
