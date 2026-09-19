package com.voicechoicer.app.media

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import com.voicechoicer.core.audio.TimelineClip
import com.voicechoicer.core.audio.TimelineMixer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Builds the final "dubbed" video: original video track copied through
 * untouched (no re-encode, so no quality loss and it's fast), original
 * audio dropped entirely and replaced by the mixed take recordings placed
 * at their fragments' timestamps. The source file is never modified, so it
 * stays available for an "original vs. dubbed" comparison in the UI.
 */
class DubExporter @Inject constructor() {

    private data class EncodedPacket(val data: ByteArray, val presentationTimeUs: Long, val flags: Int)

    fun export(
        sourceVideoPath: String,
        outputPath: String,
        totalDurationMs: Long,
        clips: List<TimelineClip>,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        onProgress: (Float) -> Unit = {},
    ) {
        onProgress(0.05f)
        val mixedPcm = TimelineMixer.mix(totalDurationMs, sampleRate, clips)
        onProgress(0.35f)
        val (audioFormat, audioPackets) = encodeAac(mixedPcm, sampleRate)
        onProgress(0.7f)
        muxVideoAndAudio(sourceVideoPath, outputPath, audioFormat, audioPackets)
        onProgress(1f)
    }

    private fun encodeAac(pcm: ShortArray, sampleRate: Int): Pair<MediaFormat, List<EncodedPacket>> {
        val requestedFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(requestedFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val pcmBytes = ByteArray(pcm.size * 2)
        ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm)

        val packets = mutableListOf<EncodedPacket>()
        var negotiatedFormat: MediaFormat? = null
        val bufferInfo = MediaCodec.BufferInfo()

        var readOffset = 0
        var presentationTimeUs = 0L
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex)!!
                        inputBuffer.clear()
                        val chunkSize = minOf(inputBuffer.capacity(), pcmBytes.size - readOffset)
                        if (chunkSize <= 0) {
                            encoder.queueInputBuffer(inputIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            inputBuffer.put(pcmBytes, readOffset, chunkSize)
                            encoder.queueInputBuffer(inputIndex, 0, chunkSize, presentationTimeUs, 0)
                            readOffset += chunkSize
                            val sampleCount = chunkSize / 2
                            presentationTimeUs += sampleCount * 1_000_000L / sampleRate
                        }
                    }
                }

                val outputIndex = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (bufferInfo.size > 0 && !isConfig) {
                            val outputBuffer = encoder.getOutputBuffer(outputIndex)!!
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val data = ByteArray(bufferInfo.size)
                            outputBuffer.get(data)
                            packets += EncodedPacket(data, bufferInfo.presentationTimeUs, bufferInfo.flags)
                        }
                        encoder.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        negotiatedFormat = encoder.outputFormat
                    }
                }
            }
        } finally {
            encoder.stop()
            encoder.release()
        }

        return (negotiatedFormat ?: requestedFormat) to packets
    }

    private fun muxVideoAndAudio(
        sourceVideoPath: String,
        outputPath: String,
        audioFormat: MediaFormat,
        audioPackets: List<EncodedPacket>,
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(sourceVideoPath)
        val videoTrackIndex = (0 until extractor.trackCount).first { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
        }
        val videoFormat = extractor.getTrackFormat(videoTrackIndex)
        extractor.selectTrack(videoTrackIndex)

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val muxerVideoTrack = muxer.addTrack(videoFormat)
        val muxerAudioTrack = muxer.addTrack(audioFormat)
        muxer.start()

        try {
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                bufferInfo.set(0, size, extractor.sampleTime, extractorFlagsToMuxerFlags(extractor.sampleFlags))
                muxer.writeSampleData(muxerVideoTrack, buffer, bufferInfo)
                extractor.advance()
            }

            for (packet in audioPackets) {
                val packetBuffer = ByteBuffer.wrap(packet.data)
                val info = MediaCodec.BufferInfo().apply {
                    set(0, packet.data.size, packet.presentationTimeUs, packet.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
                }
                muxer.writeSampleData(muxerAudioTrack, packetBuffer, info)
            }
        } finally {
            extractor.release()
            muxer.stop()
            muxer.release()
        }
    }

    private fun extractorFlagsToMuxerFlags(sampleFlags: Int): Int {
        var flags = 0
        if (sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
        return flags
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44_100
        private const val TIMEOUT_US = 10_000L
    }
}
