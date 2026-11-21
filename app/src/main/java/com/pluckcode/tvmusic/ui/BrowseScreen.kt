package com.pluckcode.tvmusic.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.pluckcode.tvmusic.browse.Entry
import com.pluckcode.tvmusic.browse.allTracks
import com.pluckcode.tvmusic.browse.browserFor
import com.pluckcode.tvmusic.data.PlayHistory
import com.pluckcode.tvmusic.data.Source
import com.pluckcode.tvmusic.net.describe
import kotlin.coroutines.cancellation.CancellationException

private data class Crumb(val path: String, val name: String, var returnIndex: Int = 0)

private sealed interface Load {
    data object Loading : Load
    data class Ok(val entries: List<Entry>) : Load
    data class Err(val message: String) : Load
}

internal val audioPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun BrowseScreen(source: Source, player: Player?, onPlaying: () -> Unit) {
    val ctx = LocalContext.current
    val browser = remember(source.id) { browserFor(ctx, source) }
    val crumbs = remember(source.id) { mutableStateListOf(Crumb("", source.displayName)) }
    var load by remember { mutableStateOf<Load>(Load.Loading) }
    var reload by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val focusTarget = remember { FocusRequester() }
    var focusIndex by remember { mutableIntStateOf(0) }

    var granted by remember {
        mutableStateOf(
            !source.usesMediaLibrary ||
                ContextCompat.checkSelfPermission(ctx, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    val crumb = crumbs.last()
    LaunchedEffect(crumb.path, reload, granted) {
        if (!granted) { permLauncher.launch(audioPermission); load = Load.Err("Permission to read music is needed."); return@LaunchedEffect }
        load = Load.Loading
        load = try {
            Load.Ok(browser.list(crumb.path))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Err(e.describe())
        }
        val entries = (load as? Load.Ok)?.entries ?: return@LaunchedEffect
        if (entries.isNotEmpty()) {
            focusIndex = crumb.returnIndex.coerceIn(0, entries.lastIndex) + HEADER_ITEMS
            listState.scrollToItem((focusIndex - 2).coerceAtLeast(0))
            withFrameNanos { }
            runCatching { focusTarget.requestFocus() }
        }
    }

    BackHandler(enabled = crumbs.size > 1) { crumbs.removeAt(crumbs.lastIndex) }

    fun play(files: List<Entry>, start: Int, shuffle: Boolean = false) {
        val p = player ?: return
        p.shuffleModeEnabled = shuffle
        p.setMediaItems(files.map { it.toMediaItem(crumb.name) }, if (shuffle) files.indices.random() else start, 0L)
        p.prepare()
        p.play()
        onPlaying()
    }

    Column {
        Text(
            crumbs.joinToString("  ›  ") { it.name },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1, overflow = TextOverflow.StartEllipsis,
        )
        Spacer(Modifier.height(12.dp))
        when (val l = load) {
            Load.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(); Text("Loading…")
            }
            is Load.Err -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(l.message, color = MaterialTheme.colorScheme.error)
                TvButton(
                    "Retry",
                    { if (!granted) permLauncher.launch(audioPermission) else reload++ },
                    Modifier.focusRequester(focusTarget),
                    icon = Icons.Filled.Refresh,
                )
                LaunchedEffect(Unit) { runCatching { focusTarget.requestFocus() } }
            }
            is Load.Ok -> {
                val files = l.entries.filter { !it.isDir }
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (files.isNotEmpty()) {
                                TvButton("Play all (${files.size})", { play(files, 0) }, icon = Icons.Filled.PlayArrow)
                                TvButton("Shuffle", { play(files, 0, shuffle = true) }, icon = Icons.Filled.Shuffle)
                            }
                            if (l.entries.any { it.isDir }) {
                                ShuffleAllButton("Shuffle everything here", player, onPlaying) { onFound ->
                                    browser.allTracks(crumb.path, onFound).map { it.toMediaItem(crumb.name) }
                                }
                            }
                            TvButton("Refresh", { PlayHistory.clearAll(); crumb.returnIndex = 0; reload++ }, icon = Icons.Filled.Refresh)
                        }
                    }
                    if (l.entries.isEmpty()) item { Text("No folders or audio files here.") }
                    itemsIndexed(l.entries, key = { _, e -> e.path }) { i, e ->
                        val idx = i + HEADER_ITEMS
                        TvCard(
                            onClick = {
                                if (e.isDir) {
                                    crumb.returnIndex = i
                                    crumbs.add(Crumb(e.path, e.name))
                                } else {
                                    crumb.returnIndex = i
                                    play(files, files.indexOf(e))
                                }
                            },
                            modifier = (if (idx == focusIndex) Modifier.focusRequester(focusTarget) else Modifier).fillMaxWidth(),
                        ) {
                            Icon(if (e.isDir) Icons.Filled.Folder else Icons.Filled.MusicNote, null)
                            Text(e.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private const val HEADER_ITEMS = 1

fun Entry.toMediaItem(folder: String): MediaItem {
    val uri = playUri!!
    return MediaItem.Builder()
        .setMediaId(uri.toString())
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                // displayTitle is only a fallback; ID3/Vorbis tags fill in title/artist/album once loaded.
                .setDisplayTitle(name.substringBeforeLast('.'))
                .setSubtitle(folder)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build()
        )
        .build()
}

