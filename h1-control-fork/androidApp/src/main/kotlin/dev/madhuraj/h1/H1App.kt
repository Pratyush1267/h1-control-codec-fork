package dev.madhuraj.h1

import android.app.Application
import android.content.Context
import dev.madhuraj.h1.core.NoiseMode

class H1App : Application() {
    val controller by lazy { HeadsetController(this) }
    val codecMonitor by lazy { CodecMonitor(this) }
    val wiredAudio by lazy { WiredAudio(this) }

    override fun onCreate() {
        super.onCreate()
        controller.onLinkRestored = {
            android.util.Log.i("H1Auto", "link restored -> reapply")
            codecMonitor.applyStoredQualityIfNeeded()
        }
    }
}

val Context.headsetController: HeadsetController
    get() = (applicationContext as H1App).controller

val NoiseMode.label: String
    get() = when (this) {
        NoiseMode.OFF -> "Off"
        NoiseMode.TRANSPARENCY -> "Transparency"
        NoiseMode.ANC -> "Noise cancelling"
    }
