@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pluckcode.tvmusic.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future

fun isRemote(uri: Uri) = uri.scheme == "sftp" || uri.scheme == "smb"

/** On-disk copy of recently played and upcoming SFTP / SMB tracks. One per process, as SimpleCache requires. */
object AudioCache {
    private const val MAX_BYTES = 1L shl 30

    @Volatile private var cache: SimpleCache? = null

    fun get(ctx: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: run {
            val dir = File(ctx.applicationContext.cacheDir, "audio")
            // Leave room on small TV storage: never take more than a quarter of what is free.
            val limit = minOf(MAX_BYTES, ctx.cacheDir.usableSpace / 4)
            SimpleCache(dir, LeastRecentlyUsedCacheEvictor(limit), StandaloneDatabaseProvider(ctx.applicationContext))
                .also { cache = it }
        }
    }

    /** Reads remote tracks through the cache, fetching only what isn't stored yet. */
    fun dataSourceFactory(ctx: Context): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(get(ctx))
        .setUpstreamDataSourceFactory(RemoteDataSourceFactory())
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}

/**
 * While music plays, downloads the next [AHEAD] SFTP / SMB tracks in the queue (in play order, so shuffle
 * is respected) into [AudioCache], so they start instantly and survive network hiccups. Downloads run one
 * at a time and restart whenever the current track or the queue changes.
 */
class Prefetcher(ctx: Context, private val player: Player) : Player.Listener {
    private val factory = AudioCache.dataSourceFactory(ctx)
    private val executor = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    @Volatile private var writer: CacheWriter? = null

    init {
        player.addListener(this)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED,
            )
        ) restart()
    }

    private fun restart() {
        task?.cancel(true)
        writer?.cancel()
        val uris = upcoming()
        if (uris.isEmpty()) return
        task = executor.submit {
            for (uri in uris) {
                if (Thread.currentThread().isInterrupted) return@submit
                val w = CacheWriter(factory.createDataSource(), DataSpec(uri), null, null)
                writer = w
                try {
                    w.cache()
                } catch (e: Exception) {
                    // Cancelled, or this track is unreachable; carry on with the rest unless cancelled.
                    if (Thread.currentThread().isInterrupted) return@submit
                    Log.w("Prefetcher", "Could not cache $uri", e)
                } finally {
                    writer = null
                }
            }
        }
    }

    /** URIs of the remote tracks that will play after the current one, up to [AHEAD]. */
    private fun upcoming(): List<Uri> {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val window = Timeline.Window()
        val out = mutableListOf<Uri>()
        var i = player.currentMediaItemIndex
        val start = i
        while (out.size < AHEAD) {
            // Repeat-one would return the same track forever; treat it like repeat-off for lookahead.
            val repeat = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
            i = timeline.getNextWindowIndex(i, repeat, player.shuffleModeEnabled)
            if (i == C.INDEX_UNSET || i == start) break
            val uri = timeline.getWindow(i, window).mediaItem.localConfiguration?.uri ?: continue
            if (isRemote(uri)) out += uri
        }
        return out
    }

    fun release() {
        player.removeListener(this)
        task?.cancel(true)
        writer?.cancel()
        executor.shutdownNow()
    }

    private companion object {
        const val AHEAD = 10
    }
}
