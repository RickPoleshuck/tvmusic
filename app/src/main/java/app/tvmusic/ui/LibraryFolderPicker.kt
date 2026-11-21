package app.tvmusic.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import app.tvmusic.browse.libraryBrowser
import app.tvmusic.net.describe

/**
 * In-app folder chooser for TVs without a system folder picker. It browses the folders holding music
 * that Android's media scanner found (internal storage and USB drives) and returns the chosen folder's
 * library path, e.g. "USB 0C2C-13FC/Music/".
 */
@Composable
fun LibraryFolderPicker(onPick: (path: String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val browser = remember { libraryBrowser(ctx) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var path by remember { mutableStateOf("") }
    var folders by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(path, granted) {
        folders = null
        error = null
        if (!granted) { permLauncher.launch(audioPermission); error = "Permission to read music is needed."; return@LaunchedEffect }
        try {
            folders = browser.list(path).filter { it.isDir }.map { it.path }
        } catch (e: Exception) {
            error = e.describe()
        }
        runCatching { focus.requestFocus() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(720.dp),
        confirmButton = { TextButton(onDismiss) { Text("Cancel") } },
        title = { Text(path.trimEnd('/').ifEmpty { "Choose a music folder" }, maxLines = 1, overflow = TextOverflow.StartEllipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (path.isNotEmpty()) {
                        TvButton("Use this folder", { onPick(path) }, Modifier.focusRequester(focus), icon = Icons.Filled.Check)
                        TvButton("Up", { path = path.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } }, icon = Icons.Filled.ArrowUpward)
                    }
                }
                val list = folders
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    list == null -> Text("Loading…")
                    list.isEmpty() && path.isEmpty() -> Text("No music found on this TV or its USB drives.")
                    else -> LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(list, key = { it }) { dir ->
                            TvCard(
                                onClick = { path = dir },
                                modifier = Modifier.fillMaxWidth().let { if (path.isEmpty() && dir == list.first()) it.focusRequester(focus) else it },
                            ) {
                                Icon(Icons.Filled.Folder, null)
                                Text(dir.trimEnd('/').substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
    )
}
