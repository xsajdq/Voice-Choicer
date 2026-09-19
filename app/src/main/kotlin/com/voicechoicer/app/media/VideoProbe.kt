package com.voicechoicer.app.media

import android.media.MediaMetadataRetriever
import javax.inject.Inject

class VideoProbe @Inject constructor() {

    fun durationMs(path: String): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }

    fun hasVideoTrack(path: String): Boolean {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"
        } finally {
            retriever.release()
        }
    }
}
