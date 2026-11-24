package dev.madhuraj.h1

import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.IntentSender
import java.util.concurrent.Executor

interface AssociationUi {
    fun onPending()
    fun onCreated(info: AssociationInfo?)
    fun onFailed(reason: String)
}

/** Starts the system-controlled companion consent flow for the selected H1. */
class CompanionAssociation(
    private val activity: Activity,
    private val manager: CompanionDeviceManager,
    private val executor: Executor,
    private val requestCode: Int,
) {
    fun start(device: BluetoothDevice, callbacks: AssociationUi) {
        val request = AssociationRequest.Builder()
            .addDeviceFilter(BluetoothDeviceFilter.Builder().setAddress(device.address).build())
            .setSingleDevice(true)
            .setDisplayName("realme TechLife Studio H1")
            .setSelfManaged(false)
            .build()

        try {
            manager.associate(request, executor, object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    callbacks.onPending()
                    activity.startIntentSenderForResult(intentSender, requestCode, null, 0, 0, 0)
                }

                override fun onAssociationCreated(info: AssociationInfo) {
                    callbacks.onCreated(info)
                }

                override fun onFailure(error: CharSequence?) {
                    callbacks.onFailed(error?.toString() ?: "Association failed")
                }
            })
        } catch (error: Throwable) {
            // System consent can be unavailable; show it instead of crashing the app.
            callbacks.onFailed(error.message ?: "Association request failed")
        }
    }

    fun fromResult(data: android.content.Intent?): AssociationInfo? =
        data?.getParcelableExtra(
            CompanionDeviceManager.EXTRA_ASSOCIATION,
            AssociationInfo::class.java,
        )
}
