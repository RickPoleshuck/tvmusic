package com.pluckcode.tvmusic.browse

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.pluckcode.tvmusic.data.Source
import com.pluckcode.tvmusic.data.SourceType
import com.pluckcode.tvmusic.data.TrackCache
import com.pluckcode.tvmusic.net.Connections
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.protocol.commons.EnumWithValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import net.schmizz.sshj.sftp.FileMode
import java.io.File

/** A folder or a playable file. [path] is the browser-specific id used to list a folder. */
data class Entry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val playUri: Uri? = null,
)

interface Browser {
    /** List a folder; "" means the source's start folder. */
    suspend fun list(path: String): List<Entry>
}

private val AUDIO_EXT = setOf(
    "mp3", "flac", "ogg", "oga", "opus", "m4a", "m4b", "mp4", "aac", "wav", "mka", "webm", "aif", "aiff", "amr", "3gp",
)

fun isAudio(name: String) = name.substringAfterLast('.', "").lowercase() in AUDIO_EXT

private val entryOrder = compareBy<Entry>({ !it.isDir }, { it.name.lowercase() })

/** Guards against symlink loops on SFTP, where directory symlinks are followed. */
private const val MAX_DEPTH = 32

/**
 * Every playable file under [path], including all subfolders. [onFound] gets the running total.
 * A failure listing [path] itself is thrown; unreadable subfolders are skipped.
 */
suspend fun Browser.allTracks(path: String = "", onFound: (Int) -> Unit = {}): List<Entry> {
    val out = mutableListOf<Entry>()
    suspend fun walk(dir: String, depth: Int) {
        val entries = if (depth == 0) list(dir) else try {
            list(dir)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        entries.filterTo(out) { !it.isDir }
        onFound(out.size)
        if (depth < MAX_DEPTH) for (e in entries) if (e.isDir) walk(e.path, depth + 1)
    }
    walk(path, 0)
    return out
}

/**
 * Like [allTracks] for [path] of [source], but answers straight from [TrackCache] when it has that
 * folder, so repeat shuffles start at once. A cached answer is then rescanned in the background, so
 * newly added music shows up the next time.
 */
suspend fun cachedTracks(ctx: Context, source: Source, path: String = "", onFound: (Int) -> Unit = {}): List<Entry> {
    val app = ctx.applicationContext
    val cached = withContext(Dispatchers.IO) { TrackCache.get(source.id, path) }
    if (cached != null) {
        onFound(cached.size)
        TrackCache.refresh(source.id, path) { browserFor(app, source).allTracks(path) }
        return cached
    }
    val tracks = browserFor(app, source).allTracks(path, onFound)
    withContext(Dispatchers.IO) { TrackCache.put(source.id, path, tracks) }
    return tracks
}

fun browserFor(ctx: Context, source: Source): Browser = when (source.type) {
    SourceType.SFTP -> SftpBrowser(source)
    SourceType.SMB -> SmbBrowser(source)
    SourceType.FOLDER ->
        if (source.usesMediaLibrary) LibraryBrowser(ctx.applicationContext, source.path)
        else TreeBrowser(ctx.applicationContext, source)
    SourceType.LIBRARY -> LibraryBrowser(ctx.applicationContext)
}

/** Browses the media library's folders; used to choose a folder on TVs without a system folder picker. */
fun libraryBrowser(ctx: Context): Browser = LibraryBrowser(ctx.applicationContext)

private class SftpBrowser(private val source: Source) : Browser {
    override suspend fun list(path: String): List<Entry> = withContext(Dispatchers.IO) {
        Connections.withRetry(source) {
            val c = Connections.sftp(source)
            val dir = path.ifEmpty { c.canonicalize(source.path.ifBlank { "." }) }
            c.ls(dir).asSequence()
                .filterNot { it.name.startsWith(".") }
                .mapNotNull { r ->
                    val type = r.attributes.type
                    val isDir = type == FileMode.Type.DIRECTORY ||
                        (type == FileMode.Type.SYMLINK &&
                            runCatching { c.stat(r.path).type == FileMode.Type.DIRECTORY }.getOrDefault(false))
                    when {
                        isDir -> Entry(r.name, r.path, true)
                        isAudio(r.name) -> Entry(
                            r.name, r.path, false,
                            Uri.Builder().scheme("sftp").authority(source.id).path(r.path).build(),
                        )
                        else -> null
                    }
                }
                .sortedWith(entryOrder).toList()
        }
    }
}

private class SmbBrowser(private val source: Source) : Browser {
    override suspend fun list(path: String): List<Entry> = withContext(Dispatchers.IO) {
        Connections.withRetry(source) {
            val share = Connections.smb(source)
            val dir = path.ifEmpty { Connections.smbPath(source.path) }
            share.list(dir).asSequence()
                .filterNot { it.fileName == "." || it.fileName == ".." || it.fileName.startsWith(".") }
                .filterNot { EnumWithValue.EnumUtils.isSet(it.fileAttributes, FileAttributes.FILE_ATTRIBUTE_HIDDEN) }
                .mapNotNull { info ->
                    val full = if (dir.isEmpty()) info.fileName else "$dir\\${info.fileName}"
                    val isDir = EnumWithValue.EnumUtils.isSet(info.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
                    when {
                        isDir -> Entry(info.fileName, full, true)
                        isAudio(info.fileName) -> Entry(
                            info.fileName, full, false,
                            Uri.Builder().scheme("smb").authority(source.id)
                                .path("/" + full.replace('\\', '/')).build(),
                        )
                        else -> null
                    }
                }
                .sortedWith(entryOrder).toList()
        }
    }
}

/** A folder the user granted via the system folder picker (USB drive, SD card, Downloads…). */
private class TreeBrowser(private val ctx: Context, source: Source) : Browser {
    private val tree = Uri.parse(source.treeUri)

    override suspend fun list(path: String): List<Entry> = withContext(Dispatchers.IO) {
        val docId = path.ifEmpty { DocumentsContract.getTreeDocumentId(tree) }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val proj = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val out = mutableListOf<Entry>()
        ctx.contentResolver.query(children, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                if (name.startsWith(".")) continue
                when {
                    mime == DocumentsContract.Document.MIME_TYPE_DIR -> out += Entry(name, id, true)
                    mime.startsWith("audio/") || isAudio(name) -> out += Entry(
                        name, id, false, DocumentsContract.buildDocumentUriUsingTree(tree, id),
                    )
                }
            }
        } ?: throw IllegalStateException("Folder is no longer accessible. Edit the source and choose it again.")
        out.sortedWith(entryOrder)
    }
}

/**
 * Everything Android's media scanner found, on internal storage and attached USB drives, browsed by folder.
 * [root] (a folder path as listed here, e.g. "Internal storage/Music/") limits it to one folder.
 */
private class LibraryBrowser(private val ctx: Context, private val root: String = "") : Browser {
    private data class Track(val folder: String, val name: String, val uri: Uri)

    @Volatile private var cache: List<Track>? = null

    private fun scan(): List<Track> {
        val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val collection = if (q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = if (q) arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.VOLUME_NAME, MediaStore.Audio.Media.RELATIVE_PATH,
        ) else arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, "_data")
        val out = mutableListOf<Track>()
        ctx.contentResolver.query(collection, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val folder = if (q) {
                    val vol = c.getString(2) ?: "external"
                    val rel = c.getString(3) ?: ""
                    "${volumeLabel(vol)}/$rel".trimEnd('/') + "/"
                } else {
                    (File(c.getString(2) ?: continue).parent ?: "").trim('/') + "/"
                }
                val uri = if (q) ContentUris.withAppendedId(
                    MediaStore.Audio.Media.getContentUri(c.getString(2) ?: MediaStore.VOLUME_EXTERNAL), id)
                else ContentUris.withAppendedId(collection, id)
                out += Track(folder, name, uri)
            }
        }
        return out
    }

    private fun volumeLabel(vol: String) =
        if (vol == MediaStore.VOLUME_EXTERNAL_PRIMARY) "Internal storage" else "USB $vol"

    override suspend fun list(path: String): List<Entry> = withContext(Dispatchers.IO) {
        // Listing the start folder rescans, so Refresh picks up newly added music.
        val tracks = (if (path.isEmpty()) null else cache) ?: scan().also { cache = it }
        val dir = path.ifEmpty { root }
        val dirs = linkedSetOf<String>()
        val files = mutableListOf<Entry>()
        for (t in tracks) {
            if (!t.folder.startsWith(dir)) continue
            val rest = t.folder.removePrefix(dir)
            if (rest.isEmpty()) files += Entry(t.name, dir + t.name, false, t.uri)
            else dirs += rest.substringBefore('/')
        }
        (dirs.map { Entry(it, "$dir$it/", true) } + files).sortedWith(entryOrder)
    }
}
