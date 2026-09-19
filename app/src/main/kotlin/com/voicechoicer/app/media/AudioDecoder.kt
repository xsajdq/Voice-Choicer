package com.voicechoicer.app.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Decodes the first audio track of a media file to mono PCM16, downmixing
 * multi-channel audio by simple averaging. Used only for the silence-based
 * fallback segmentation path (no subtitle file supplied) - the result never
 * touches storage, it's consumed in-memory by [com.voicechoicer.core.audio.SilenceSegmenter].
 */
class AudioDecoder @Inject constructor() {

    data class DecodedAudio(val pcm: ShortArray, val sampleRate: Int)

    fun decodeAudioTrack(path: String): DecodedAudio {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            val (trackIndex, format) = findAudioTrack(extractor)
                ?: error("Plik nie zawiera ścieżki dźwiękowej")
            extractor.selectTrack(trackIndex)
            return decode(extractor, format)
        } finally {
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Pair<Int, MediaFormat>? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i to format
        }
        return null
    }

    private fun decode(extractor: MediaExtractor, format: MediaFormat): DecodedAudio {
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val output = GrowableShortBuffer()
        val bufferInfo = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false

        try {
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputIndex >= 0 -> {
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            appendDownmixed(outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer(), channelCount, output)
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                    }
                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        sampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channelCount = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
        } finally {
            codec.stop()
            codec.release()
        }

        return DecodedAudio(output.toShortArray(), sampleRate)
    }

    private fun appendDownmixed(shortBuffer: java.nio.ShortBuffer, channelCount: Int, out: GrowableShortBuffer) {
        val remaining = shortBuffer.remaining()
        if (channelCount <= 1) {
            val chunk = ShortArray(remaining)
            shortBuffer.get(chunk)
            out.append(chunk)
            return
        }
        val frameCount = remaining / channelCount
        for (frame in 0 until frameCount) {
            var sum = 0
            for (c in 0 until channelCount) sum += shortBuffer.get()
            out.append((sum / channelCount).toShort())
        }
        // Drop any trailing partial frame (shouldn't normally happen).
        while (shortBuffer.hasRemaining()) shortBuffer.get()
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
    }
}
