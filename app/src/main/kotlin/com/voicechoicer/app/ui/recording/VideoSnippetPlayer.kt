package com.voicechoicer.app.ui.recording

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/** Plays just the [startMs, endMs) slice of the source clip so a player can watch the original delivery before recording. */
@Composable
fun VideoSnippetPlayer(videoPath: String, startMs: Long, endMs: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var isPlaying by remember { mutableStateOf(false) }
    val latestEndMs = rememberUpdatedState(endMs)

    LaunchedEffect(videoPath) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
        player.prepare()
        player.seekTo(startMs)
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    LaunchedEffect(player, isPlaying) {
        while (isActive && isPlaying) {
            if (player.currentPosition >= latestEndMs.value) {
                player.pause()
                isPlaying = false
            }
            delay(50)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            IconButton(onClick = {
                player.seekTo(startMs)
                player.play()
                isPlaying = true
            }) {
                Icon(Icons.Filled.Replay, contentDescription = "Odtwórz od początku fragmentu")
            }
            IconButton(onClick = {
                if (player.currentPosition >= endMs || player.currentPosition < startMs) player.seekTo(startMs)
                player.play()
                isPlaying = true
            }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Odtwórz")
            }
        }
    }
}
