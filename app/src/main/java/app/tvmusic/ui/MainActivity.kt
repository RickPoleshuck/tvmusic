package app.tvmusic.ui

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import app.tvmusic.data.Source
import app.tvmusic.playback.PlaybackService
import com.google.common.util.concurrent.ListenableFuture

sealed interface Screen {
    data object Home : Screen
    data class Edit(val source: Source) : Screen
    data class Browse(val source: Source) : Screen
    data object NowPlaying : Screen
}

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controller = mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvTheme { AppRoot(controller.value) } }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener(
            { controller.value = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(this),
        )
    }

    override fun onStop() {
        controller.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }
}

@Composable
fun AppRoot(controller: MediaController?) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val push: (Screen) -> Unit = { stack.add(it) }
    val pop: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    val player = rememberPlayerSnapshot(controller)

    BackHandler(enabled = stack.size > 1) { pop() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Generous padding keeps everything inside the TV's overscan-safe area.
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 27.dp)) {
            Box(Modifier.weight(1f)) {
                when (val s = stack.last()) {
                    Screen.Home -> HomeScreen(
                        onOpen = { push(Screen.Browse(it)) },
                        onEdit = { push(Screen.Edit(it)) },
                    )
                    is Screen.Edit -> EditSourceScreen(s.source, onDone = pop)
                    is Screen.Browse -> BrowseScreen(s.source, controller, onPlaying = { push(Screen.NowPlaying) })
                    Screen.NowPlaying -> NowPlayingScreen(player, controller)
                }
            }
            if (player.hasMedia && stack.last() != Screen.NowPlaying) {
                Spacer(Modifier.height(12.dp))
                MiniPlayer(player, controller, onOpen = { push(Screen.NowPlaying) })
            }
        }
    }
}
