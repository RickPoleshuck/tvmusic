package app.tvmusic.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.Executors

/**
 * Remembers when each track (keyed by media ID, i.e. its URI) last started playing, so shuffles can
 * play every track once before repeating any.
 */
object PlayHistory {
    private lateinit var db: SQLiteDatabase
    private val writer = Executors.newSingleThreadExecutor()

    fun init(ctx: Context) {
        db = Helper(ctx).writableDatabase
    }

    /** Records that [mediaId] started playing now. Runs off the calling thread. */
    fun recordPlayed(mediaId: String) {
        val values = ContentValues().apply {
            put("media_id", mediaId)
            put("played_at", System.currentTimeMillis())
        }
        writer.execute { db.insertWithOnConflict("played", null, values, SQLiteDatabase.CONFLICT_REPLACE) }
    }

    /**
     * Orders [ids] for a shuffle: never-played tracks first in random order, then the rest from least
     * to most recently played. If every track has already been played, their history is cleared and a
     * fresh round starts with all of them in random order. Blocking; call off the main thread.
     */
    fun shuffleOrder(ids: List<String>): List<String> {
        val lastPlayed = HashMap<String, Long>()
        db.rawQuery("SELECT media_id, played_at FROM played", null).use { c ->
            while (c.moveToNext()) lastPlayed[c.getString(0)] = c.getLong(1)
        }
        val (played, unplayed) = ids.distinct().partition { it in lastPlayed }
        if (unplayed.isEmpty()) {
            clear(played)
            return played.shuffled()
        }
        return unplayed.shuffled() + played.sortedBy { lastPlayed.getValue(it) }
    }

    private fun clear(ids: List<String>) {
        db.beginTransaction()
        try {
            val stmt = db.compileStatement("DELETE FROM played WHERE media_id = ?")
            for (id in ids) {
                stmt.bindString(1, id)
                stmt.executeUpdateDelete()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, "play_history.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE played (media_id TEXT PRIMARY KEY, played_at INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }
}
