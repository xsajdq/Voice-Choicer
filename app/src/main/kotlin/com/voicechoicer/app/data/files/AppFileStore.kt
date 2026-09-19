package com.voicechoicer.app.data.files

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All processing (MediaExtractor/MediaCodec/MediaMuxer) needs stable,
 * randomly-seekable local files. We copy anything picked through the
 * document picker into app-private storage once at import time so we never
 * have to worry about a content:// Uri's permission grant expiring or not
 * supporting random access.
 */
@Singleton
class AppFileStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val videosDir get() = File(context.filesDir, "videos").apply { mkdirs() }
    private val takesDir get() = File(context.filesDir, "takes").apply { mkdirs() }
    private val exportsDir get() = File(context.filesDir, "exports").apply { mkdirs() }

    fun importVideo(source: Uri, suggestedExtension: String = "mp4"): File {
        val dest = File(videosDir, "${UUID.randomUUID()}.$suggestedExtension")
        context.contentResolver.openInputStream(source)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Nie udało się otworzyć wybranego pliku wideo")
        return dest
    }

    fun readText(source: Uri): String {
        return context.contentResolver.openInputStream(source)?.use { it.reader(Charsets.UTF_8).readText() }
            ?: error("Nie udało się odczytać pliku napisów")
    }

    fun newTakeFile(fragmentId: Long): File = File(takesDir, "take_${fragmentId}_${UUID.randomUUID()}.wav")

    fun newExportFile(projectId: Long): File = File(exportsDir, "dub_${projectId}_${System.currentTimeMillis()}.mp4")

    fun deleteQuietly(path: String?) {
        if (path == null) return
        runCatching { File(path).delete() }
    }
}
