package com.pluckcode.tvmusic.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import com.pluckcode.tvmusic.browse.Entry
import java.util.concurrent.Executors

/**
 * Remembers every track found under a folder (keyed by source ID and folder path, "" being the source's
 * start folder), so shuffling it again doesn't have to walk the whole tree first.
 */
object TrackCache {
    private lateinit var db: SQLiteDatabase
    private val writer = Executors.newSingleThreadExecutor()

    fun init(ctx: Context) {
        db = Helper(ctx).writableDatabase
    }

    /** The tracks last stored for [root] of [sourceId], or null if none are. Blocking; call off the main thread. */
    fun get(sourceId: String, root: String): List<Entry>? {
        val out = mutableListOf<Entry>()
        db.rawQuery("SELECT name, path, uri FROM tracks WHERE source_id = ? AND root = ?", arrayOf(sourceId, root)).use { c ->
            while (c.moveToNext()) out += Entry(c.getString(0), c.getString(1), false, Uri.parse(c.getString(2)))
        }
        return out.ifEmpty { null }
    }

    /** Replaces the tracks stored for [root] of [sourceId]. Blocking; call off the main thread. */
    fun put(sourceId: String, root: String, tracks: List<Entry>) {
        db.beginTransaction()
        try {
            db.delete("tracks", "source_id = ? AND root = ?", arrayOf(sourceId, root))
            val stmt = db.compileStatement("INSERT INTO tracks (source_id, root, name, path, uri) VALUES (?, ?, ?, ?, ?)")
            for (t in tracks) {
                val uri = t.playUri ?: continue
                stmt.bindString(1, sourceId)
                stmt.bindString(2, root)
                stmt.bindString(3, t.name)
                stmt.bindString(4, t.path)
                stmt.bindString(5, uri.toString())
                stmt.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Forgets every stored track, so each source is walked again on its next shuffle. Runs off the calling thread. */
    fun clearAll() {
        writer.execute { db.delete("tracks", null, null) }
    }

    /** Forgets everything stored for [sourceId], e.g. after it is edited or deleted. Runs off the calling thread. */
    fun clear(sourceId: String) {
        writer.execute { db.delete("tracks", "source_id = ?", arrayOf(sourceId)) }
    }

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, "track_cache.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE tracks (source_id TEXT NOT NULL, root TEXT NOT NULL, name TEXT NOT NULL, path TEXT NOT NULL, uri TEXT NOT NULL)")
            db.execSQL("CREATE INDEX tracks_folder ON tracks (source_id, root)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }
}
