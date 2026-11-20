package app.tvmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import app.tvmusic.data.Source
import app.tvmusic.data.SourceStore
import app.tvmusic.data.SourceType

fun SourceType.icon() = when (this) {
    SourceType.SFTP -> Icons.Filled.Terminal
    SourceType.SMB -> Icons.Filled.Lan
    SourceType.FOLDER -> Icons.Filled.Folder
    SourceType.LIBRARY -> Icons.Filled.LibraryMusic
}

@Composable
fun HomeScreen(onOpen: (Source) -> Unit, onEdit: (Source) -> Unit) {
    val saved by SourceStore.sources.collectAsState()
    val sources = listOf(Source.LIBRARY) + saved
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column {
        Text("TV Music", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Add SFTP", { onEdit(Source(type = SourceType.SFTP, port = 22)) }, icon = Icons.Filled.Add)
            TvButton("Add SMB share", { onEdit(Source(type = SourceType.SMB, port = 445)) }, icon = Icons.Filled.Add)
            TvButton("Add local folder", { onEdit(Source(type = SourceType.FOLDER)) }, icon = Icons.Filled.Add)
        }
        Spacer(Modifier.height(20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(sources, key = { _, s -> s.id }) { i, s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvCard(
                        onClick = { onOpen(s) },
                        modifier = Modifier.weight(1f).let { if (i == 0) it.focusRequester(first) else it },
                    ) {
                        Icon(s.type.icon(), null, Modifier.size(32.dp))
                        Column(Modifier.padding(vertical = 2.dp)) {
                            Text(s.displayName, style = MaterialTheme.typography.titleLarge)
                            Text(s.subtitle, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (s.type != SourceType.LIBRARY) {
                        TvIconButton(Icons.Filled.Edit, "Edit ${s.displayName}", { onEdit(s) })
                    }
                }
            }
            item { Spacer(Modifier.fillMaxWidth().height(8.dp)) }
        }
    }
}
