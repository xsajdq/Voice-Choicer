package com.voicechoicer.app.ui.recording

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Plays just the [startMs, endMs) slice of the source clip, with picture and
 * original sound, so a player can see/hear exactly how the line was
 * delivered before recording their own take. Plays automatically once as
 * soon as it's ready (so opening the recording sheet immediately shows the
 * original delivery with no extra taps needed).
 *
 * While [muteWhileRecording] is true - the mic is actually recording - the
 * picture keeps playing (and looping over [startMs, endMs) rather than
 * freezing at the end) so the player can keep watching the character's lips
 * for timing reference, but the original audio is muted so the phone's own
 * speaker output doesn't bleed into the take.
 */
@Composable
fun VideoSnippetPlayer(
    videoPath: String,
    startMs: Long,
    endMs: Long,
    muteWhileRecording: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var isPlaying by remember { mutableStateOf(false) }
    val latestEndMs = rememberUpdatedState(endMs)

    LaunchedEffect(videoPath, startMs) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
        player.prepare()
        player.seekTo(startMs)
        player.play()
        isPlaying = true
    }

    LaunchedEffect(player, muteWhileRecording) {
        player.volume = if (muteWhileRecording) 0f else 1f
        if (muteWhileRecording) {
            // Recording just started: keep the visual lip-sync reference moving regardless of
            // whatever state playback was already in (paused, finished, mid-scrub, ...).
            if (player.currentPosition >= latestEndMs.value || player.currentPosition < startMs) {
                player.seekTo(startMs)
            }
            player.play()
            isPlaying = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(player, isPlaying, muteWhileRecording) {
        while (isActive && isPlaying) {
            if (player.currentPosition >= latestEndMs.value) {
                if (muteWhileRecording) {
                    player.seekTo(startMs)
                } else {
                    player.pause()
                    isPlaying = false
                }
            }
            delay(50)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Oryginalny fragment", style = MaterialTheme.typography.labelLarge)
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    this.player = player
                    useController = false
                }
            },
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = {
                    player.seekTo(startMs)
                    player.play()
                    isPlaying = true
                },
                enabled = !muteWhileRecording,
            ) {
                Icon(Icons.Filled.Replay, contentDescription = "Odtwórz od początku fragmentu")
            }
            IconButton(
                onClick = {
                    if (isPlaying) {
                        player.pause()
                        isPlaying = false
                    } else {
                        if (player.currentPosition >= endMs || player.currentPosition < startMs) player.seekTo(startMs)
                        player.play()
                        isPlaying = true
                    }
                },
                enabled = !muteWhileRecording,
            ) {
                if (isPlaying) {
                    Icon(Icons.Filled.Pause, contentDescription = "Pauza")
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Odtwórz")
                }
            }
            if (muteWhileRecording) {
                Text("(bez dźwięku podczas nagrywania)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
