package com.pluckcode.tvmusic.playback

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pluckcode.tvmusic.data.PlayHistory
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** Owns the player so the remote's media keys work. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(RoutingDataSourceFactory(this)))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(object : Player.Listener {
            private var failuresInARow = 0

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.takeIf { it.isNotEmpty() }?.let(PlayHistory::recordPlayed)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) failuresInARow = 0
            }

            // Skip a track that can't be played. Give up after a run of failures (e.g. the server is offline,
            // or every track fails) so the error stays on screen instead of churning through the whole queue.
            override fun onPlayerError(error: PlaybackException) {
                failuresInARow++
                if (failuresInARow >= minOf(MAX_FAILURES_IN_A_ROW, player.mediaItemCount) || !player.hasNextMediaItem()) return
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        })

        session = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // Controllers don't send a MediaItem's URI across processes; restore it from requestMetadata.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
                    mediaItems.map { it.buildUpon().setUri(it.requestMetadata.mediaUri).build() }.toMutableList()
                )
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private companion object {
        const val MAX_FAILURES_IN_A_ROW = 10
    }
}
