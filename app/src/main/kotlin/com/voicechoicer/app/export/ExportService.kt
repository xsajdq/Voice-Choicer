package com.voicechoicer.app.export

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.voicechoicer.app.MainActivity
import com.voicechoicer.app.R
import com.voicechoicer.app.VoiceChoicerApp
import com.voicechoicer.app.data.ProjectRepository
import com.voicechoicer.app.media.DubExporter
import com.voicechoicer.core.audio.TimelineClip
import com.voicechoicer.core.audio.Wav
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ExportService : Service() {

    @Inject lateinit var repository: ProjectRepository
    @Inject lateinit var dubExporter: DubExporter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val projectId = intent?.getLongExtra(EXTRA_PROJECT_ID, -1) ?: -1
        if (projectId <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(0))
        job = scope.launch { runExport(projectId) }
        return START_NOT_STICKY
    }

    private suspend fun runExport(projectId: Long) {
        try {
            val project = repository.getProject(projectId) ?: return
            val fragments = repository.getFragments(projectId)
            val selectedTakes = repository.getSelectedTakesForProject(projectId).associateBy { it.fragmentId }

            val clips = fragments.mapNotNull { fragment ->
                val take = selectedTakes[fragment.id] ?: return@mapNotNull null
                val decoded = Wav.decode(java.io.File(take.wavFilePath).readBytes())
                TimelineClip(startMs = fragment.startMs, pcm = decoded.pcm)
            }

            val outputFile = repository.newExportFile(projectId)
            dubExporter.export(
                sourceVideoPath = project.localVideoPath,
                outputPath = outputFile.absolutePath,
                totalDurationMs = project.durationMs,
                clips = clips,
                onProgress = { progress ->
                    updateNotification((progress * 100).toInt())
                },
            )
            repository.setDubbedVideoPath(projectId, outputFile.absolutePath)
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(progressPercent: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, VoiceChoicerApp.EXPORT_CHANNEL_ID)
            .setContentTitle(getString(R.string.export_notification_title))
            .setContentText(getString(R.string.export_notification_progress, progressPercent))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progressPercent, false)
            .setContentIntent(openAppIntent)
            .build()
    }

    private fun updateNotification(progressPercent: Int) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(progressPercent))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val EXTRA_PROJECT_ID = "project_id"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context, projectId: Long) {
            val intent = Intent(context, ExportService::class.java).putExtra(EXTRA_PROJECT_ID, projectId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
