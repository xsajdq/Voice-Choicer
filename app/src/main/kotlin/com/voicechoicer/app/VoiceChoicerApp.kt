package com.voicechoicer.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VoiceChoicerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createExportNotificationChannel()
    }

    private fun createExportNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            EXPORT_CHANNEL_ID,
            "Eksport dubbingu",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Postęp budowania filmu z nagranymi głosami"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val EXPORT_CHANNEL_ID = "export_channel"
    }
}
