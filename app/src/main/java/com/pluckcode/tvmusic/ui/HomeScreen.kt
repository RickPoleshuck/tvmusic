package com.pluckcode.tvmusic.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.pluckcode.tvmusic.browse.cachedTracks
import com.pluckcode.tvmusic.browse.browserFor
import com.pluckcode.tvmusic.data.Source
import com.pluckcode.tvmusic.data.SourceStore
import com.pluckcode.tvmusic.data.SourceType
import com.pluckcode.tvmusic.data.TrackCache
import kotlin.coroutines.cancellation.CancellationException

fun SourceType.icon() = when (this) {
    SourceType.SFTP -> Icons.Filled.Terminal
    SourceType.SMB -> Icons.Filled.Lan
    SourceType.FOLDER -> Icons.Filled.Folder
    SourceType.LIBRARY -> Icons.Filled.LibraryMusic
}

@Composable
fun HomeScreen(player: Player?, onOpen: (Source) -> Unit, onEdit: (Source) -> Unit, onPlaying: () -> Unit) {
    val ctx = LocalContext.current
    val saved by SourceStore.sources.collectAsState()
    val sources = listOf(Source.LIBRARY) + saved
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column {
        // Drawn above the list so the menu button's focus label isn't hidden behind the first card.
        Row(Modifier.fillMaxWidth().zIndex(1f), verticalAlignment = Alignment.CenterVertically) {
            Text("TV Music", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
            ShuffleAllButton("Shuffle all music", player, onPlaying) { onFound ->
                // Walk every source in turn; one that is offline or unreadable is skipped rather than failing the rest.
                val items = mutableListOf<MediaItem>()
                var firstError: Exception? = null
                for (s in sources) {
                    val before = items.size
                    try {
                        cachedTracks(ctx, s) { onFound(before + it) }.mapTo(items) { it.toMediaItem(s.displayName) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (firstError == null) firstError = e
                    }
                }
                if (items.isEmpty()) firstError?.let { throw it }
                items
            }
            Spacer(Modifier.width(12.dp))
            TvIconButton(Icons.Filled.Refresh, "Rescan all music", {
                TrackCache.clearAll()
                Toast.makeText(ctx, "Music will be rescanned on the next shuffle", Toast.LENGTH_SHORT).show()
            }, label = "Rescan")
            Spacer(Modifier.width(12.dp))
            ConfigMenu(saved, onEdit)
        }
        Spacer(Modifier.height(20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(sources, key = { _, s -> s.id }) { i, s ->
                TvCard(
                    onClick = { onOpen(s) },
                    modifier = Modifier.fillMaxWidth().let { if (i == 0) it.focusRequester(first) else it },
                ) {
                    Icon(s.type.icon(), null, Modifier.size(32.dp))
                    Column(Modifier.padding(vertical = 2.dp)) {
                        Text(s.displayName, style = MaterialTheme.typography.titleLarge)
                        Text(s.subtitle, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Spacer(Modifier.fillMaxWidth().height(8.dp)) }
        }
    }
}

/** Hamburger menu holding source configuration: adding new sources and editing saved ones. */
@Composable
private fun ConfigMenu(saved: List<Source>, onEdit: (Source) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val firstItem = remember { FocusRequester() }
    val pick: (Source) -> Unit = { open = false; onEdit(it) }

    Box {
        TvIconButton(Icons.Filled.Menu, "Settings", { open = true })
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            LaunchedEffect(Unit) { runCatching { firstItem.requestFocus() } }
            Column(
                Modifier.width(320.dp).padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TvButton("Add SFTP", { pick(Source(type = SourceType.SFTP, port = 22)) }, Modifier.fillMaxWidth().focusRequester(firstItem), icon = Icons.Filled.Add)
                TvButton("Add SMB share", { pick(Source(type = SourceType.SMB, port = 445)) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
                TvButton("Add local folder", { pick(Source(type = SourceType.FOLDER)) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Add)
                if (saved.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    saved.forEach { s ->
                        TvButton("Edit ${s.displayName}", { pick(s) }, Modifier.fillMaxWidth(), icon = Icons.Filled.Edit)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                TvButton("About", { open = false; showAbout = true }, Modifier.fillMaxWidth(), icon = Icons.Filled.Info)
            }
        }
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}
