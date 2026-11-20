package app.tvmusic.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.delay

data class PlayerSnapshot(
    val hasMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val title: String = "",
    val subtitle: String = "",
    val artwork: ByteArray? = null,
    /** Position of the current track within [queue]. */
    val index: Int = 0,
    /** Tracks in the order they will play (shuffled order when shuffle is on). */
    val queue: List<QueueItem> = emptyList(),
    val position: Long = 0,
    val duration: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val error: String? = null,
)

/** [mediaIndex] is the item's index in the player's playlist, used to jump to it. */
data class QueueItem(val mediaIndex: Int, val title: String)

/** Playlist indices in play order, following the player's shuffle order when shuffle is on. */
private fun playOrder(p: Player): List<Int> {
    val t = p.currentTimeline
    if (t.isEmpty) return emptyList()
    val shuffle = p.shuffleModeEnabled
    return generateSequence(t.getFirstWindowIndex(shuffle)) { i ->
        t.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, shuffle).takeIf { it != C.INDEX_UNSET }
    }.toList()
}

private fun snapshot(p: Player): PlayerSnapshot {
    val md = p.mediaMetadata
    val queue = playOrder(p).map { i ->
        QueueItem(i, p.getMediaItemAt(i).mediaMetadata.let { (it.title ?: it.displayTitle ?: "").toString() })
    }
    val artistLine = listOfNotNull(md.artist ?: md.albumArtist, md.albumTitle).joinToString(" — ")
    return PlayerSnapshot(
        hasMedia = p.mediaItemCount > 0,
        isPlaying = p.isPlaying,
        title = (md.title ?: md.displayTitle ?: "").toString(),
        subtitle = artistLine.ifEmpty { md.subtitle?.toString() ?: "" },
        artwork = md.artworkData,
        index = queue.indexOfFirst { it.mediaIndex == p.currentMediaItemIndex }.coerceAtLeast(0),
        queue = queue,
        position = p.currentPosition,
        duration = p.duration.coerceAtLeast(0),
        shuffle = p.shuffleModeEnabled,
        repeatMode = p.repeatMode,
        error = p.playerError?.let { e -> generateSequence<Throwable>(e) { it.cause }.mapNotNull { it.message }.lastOrNull() ?: e.errorCodeName },
    )
}

@Composable
fun rememberPlayerSnapshot(player: Player?): PlayerSnapshot {
    var snap by remember { mutableStateOf(PlayerSnapshot()) }
    DisposableEffect(player) {
        if (player == null) {
            snap = PlayerSnapshot()
            return@DisposableEffect onDispose { }
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { snap = snapshot(player) }
        }
        player.addListener(listener)
        snap = snapshot(player)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        while (true) {
            delay(500)
            snap = snap.copy(position = p.currentPosition, duration = p.duration.coerceAtLeast(0))
        }
    }
    return snap
}

@Composable
private fun Artwork(data: ByteArray?, size: Int) {
    val bmp = remember(data) { data?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, null, Modifier.size(size.dp), contentScale = ContentScale.Crop)
        else Icon(Icons.Filled.MusicNote, null, Modifier.size((size / 2).dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun NowPlayingScreen(s: PlayerSnapshot, player: Player?) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }

    Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                Artwork(s.artwork, 200)
                Column(Modifier.weight(1f)) {
                    Text(s.title.ifEmpty { "Nothing playing" }, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(6.dp))
                    Text(s.subtitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (s.queue.isNotEmpty()) Text("Track ${s.index + 1} of ${s.queue.size}", style = MaterialTheme.typography.bodyMedium)
                    s.error?.let { Text(it, color = MaterialTheme.colorScheme.error, maxLines = 3) }
                }
            }
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                progress = { if (s.duration > 0) (s.position.toFloat() / s.duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatTime(s.position)); Spacer(Modifier.weight(1f)); Text(formatTime(s.duration))
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TvIconButton(Icons.Filled.Shuffle, "Shuffle", { player?.shuffleModeEnabled = !s.shuffle }, active = s.shuffle)
                TvIconButton(Icons.Filled.SkipPrevious, "Previous", { player?.seekToPrevious() })
                TvIconButton(Icons.Filled.Replay10, "Back 10 seconds", { player?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) } })
                TvIconButton(
                    if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (s.isPlaying) "Pause" else "Play",
                    { player?.let { if (it.isPlaying) it.pause() else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() } } },
                    Modifier.focusRequester(playFocus), big = true,
                )
                TvIconButton(Icons.Filled.Forward10, "Forward 10 seconds", { player?.let { it.seekTo(it.currentPosition + 10_000) } })
                TvIconButton(Icons.Filled.SkipNext, "Next", { player?.seekToNext() })
                TvIconButton(
                    if (s.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat",
                    {
                        player?.repeatMode = when (s.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    },
                    active = s.repeatMode != Player.REPEAT_MODE_OFF,
                )
            }
        }

        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("Up next", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            val listState = rememberLazyListState()
            LaunchedEffect(s.index) { if (s.queue.isNotEmpty()) listState.animateScrollToItem((s.index - 1).coerceAtLeast(0)) }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(s.queue) { i, item ->
                    TvCard({ player?.seekTo(item.mediaIndex, 0); player?.play() }, Modifier.fillMaxWidth(), selected = i == s.index) {
                        Text("${i + 1}", Modifier.width(36.dp), color = if (i == s.index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(s: PlayerSnapshot, player: Player?, onOpen: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TvCard(onOpen, Modifier.weight(1f)) {
            Artwork(s.artwork, 48)
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(s.subtitle, "${formatTime(s.position)} / ${formatTime(s.duration)}").filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                )
            }
        }
        TvIconButton(Icons.Filled.SkipPrevious, "Previous", { player?.seekToPrevious() })
        TvIconButton(if (s.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/pause", {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
        })
        TvIconButton(Icons.Filled.SkipNext, "Next", { player?.seekToNext() })
    }
}
