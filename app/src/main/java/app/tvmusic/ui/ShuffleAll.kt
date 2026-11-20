package app.tvmusic.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import app.tvmusic.net.describe
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * Gathers tracks with [collect] (which may walk whole folder trees, so it reports progress and can be
 * cancelled by pressing the button again), then plays them all in random order.
 */
@Composable
fun ShuffleAllButton(
    label: String,
    player: Player?,
    onPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    collect: suspend (onFound: (Int) -> Unit) -> List<MediaItem>,
) {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var found by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    val text = when {
        job != null -> "Finding music… $found (press to stop)"
        error != null -> "$label – ${error}"
        else -> label
    }
    TvButton(text, onClick = {
        val running = job
        if (running != null) {
            running.cancel()
            job = null
        } else {
            error = null
            found = 0
            job = scope.launch {
                try {
                    val items = collect { found = it }
                    val p = player
                    when {
                        items.isEmpty() -> error = "no music found"
                        p == null -> error = "player not ready"
                        else -> {
                            p.shuffleModeEnabled = true
                            p.setMediaItems(items, items.indices.random(), 0L)
                            p.prepare()
                            p.play()
                            onPlaying()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.describe()
                } finally {
                    job = null
                }
            }
        }
    }, modifier, icon = Icons.Filled.Shuffle)
}
