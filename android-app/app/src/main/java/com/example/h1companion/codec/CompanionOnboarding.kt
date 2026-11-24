package com.example.h1companion.codec

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Intent
import android.os.Build
import android.content.IntentSender
import java.util.concurrent.Executor

interface AssociationCallbacks {
    fun onUserConsentRequired()
    fun onReady(info: AssociationInfo?)
    fun onFailed(reason: String)
}

/**
 * Builds the user-approved companion association our app needs.
 *
 * The system, not the app, shows the consent UI. We filter strictly to the selected
 * Bluetooth address and explicitly request a NON-self-managed association, because
 * self-managed associations do not satisfy Android 16's codec Binder check.
 */
class CompanionOnboarding(
    private val activity: Activity,
    private val manager: CompanionDeviceManager,
    private val executor: Executor,
    private val requestCode: Int,
) {
    fun currentRecords(): List<AssociationRecord> = if (Build.VERSION.SDK_INT >= 33) {
        manager.getMyAssociations().map {
            // AssociationInfo.isSelfManaged() itself arrived at API 35.
            val selfManaged = if (Build.VERSION.SDK_INT >= 35) it.isSelfManaged else false
            AssociationRecord(it.deviceMacAddress?.toString(), selfManaged, it.id)
        }
    } else {
        @Suppress("DEPRECATION")
        manager.getAssociations().map { AssociationRecord(it, false, null) }
    }

    fun decisionFor(device: BluetoothDevice): AssociationDecision =
        AssociationEvaluator.evaluate(currentRecords(), device.address)

    fun start(device: BluetoothDevice, displayName: String, callbacks: AssociationCallbacks) {
        if (decisionFor(device) == AssociationDecision.ASSOCIATED) {
            callbacks.onReady(null)
            return
        }
        val request = AssociationRequest.Builder()
            .addDeviceFilter(
                BluetoothDeviceFilter.Builder().setAddress(device.address).build()
            )
            .setSingleDevice(true)
            .setDisplayName(displayName)
            .setSelfManaged(false)
            .build()

        manager.associate(request, executor, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                callbacks.onUserConsentRequired()
                activity.startIntentSenderForResult(intentSender, requestCode, null, 0, 0, 0)
            }

            override fun onAssociationCreated(info: AssociationInfo) {
                callbacks.onReady(info)
            }

            override fun onFailure(error: CharSequence?) {
                callbacks.onFailed(error?.toString() ?: "Association failed")
            }
        })
    }

    /** Optionally corroborate the callback via Activity result EXTRA_ASSOCIATION. */
    fun associationFromResult(data: Intent?): AssociationInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            data?.getParcelableExtra(
                CompanionDeviceManager.EXTRA_ASSOCIATION,
                AssociationInfo::class.java,
            )
        } else {
            null
        }

    /** Access gates for CodecController; never auto-grant or widen permissions. */
    fun codecWriteGate(): (BluetoothDevice) -> Boolean = { device ->
        !AssociationPolicy.requiredForSet(Build.VERSION.SDK_INT) ||
            decisionFor(device) == AssociationDecision.ASSOCIATED
    }

    fun codecReadGate(): (BluetoothDevice) -> Boolean = { device ->
        !AssociationPolicy.requiredForGet(Build.VERSION.SDK_INT) ||
            decisionFor(device) == AssociationDecision.ASSOCIATED
    }
}
