package com.voicechoicer.app.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Records a single take from the microphone as raw PCM16 mono, with a live
 * elapsed-time and peak-amplitude readout for the recording UI. The caller
 * is responsible for having already obtained RECORD_AUDIO permission.
 */
class TakeRecorder @Inject constructor() {

    data class RecordedAudio(val pcm: ShortArray, val sampleRate: Int)

    private val sampleRate = 44_100
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null
    private var buffer = GrowableShortBuffer()
    @Volatile private var recording = false

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude

    val isRecording: Boolean get() = recording

    @SuppressLint("MissingPermission")
    fun start() {
        check(!recording) { "Nagrywanie już trwa" }
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minBufferSize > 0) { "Nagrywanie audio nie jest wspierane na tym urządzeniu" }

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufferSize * 2,
        )
        buffer = GrowableShortBuffer()
        _elapsedMs.value = 0L
        _amplitude.value = 0f
        audioRecord = record
        recording = true
        record.startRecording()

        val startTime = System.currentTimeMillis()
        recordThread = thread(name = "voice-choicer-recorder") {
            val readBuffer = ShortArray(minBufferSize)
            while (recording) {
                val read = record.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    synchronized(this) { buffer.append(readBuffer, read) }
                    var peak = 0
                    for (i in 0 until read) peak = max(peak, abs(readBuffer[i].toInt()))
                    _amplitude.value = (peak / 32768f).coerceIn(0f, 1f)
                    _elapsedMs.value = System.currentTimeMillis() - startTime
                }
            }
        }
    }

    fun stop(): RecordedAudio {
        check(recording) { "Nagrywanie nie zostało rozpoczęte" }
        recording = false
        recordThread?.join(500)
        recordThread = null
        releaseRecord()
        val pcm = synchronized(this) { buffer.toShortArray() }
        return RecordedAudio(pcm, sampleRate)
    }

    fun cancel() {
        if (!recording) return
        recording = false
        recordThread?.join(500)
        recordThread = null
        releaseRecord()
    }

    private fun releaseRecord() {
        audioRecord?.apply {
            runCatching { stop() }
            release()
        }
        audioRecord = null
    }
}
