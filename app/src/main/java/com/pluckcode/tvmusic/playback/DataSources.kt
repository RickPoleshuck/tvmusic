@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pluckcode.tvmusic.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.pluckcode.tvmusic.data.SourceStore
import com.pluckcode.tvmusic.net.Connections
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.fileinformation.FileStandardInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import net.schmizz.sshj.sftp.RemoteFile
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet

/**
 * Hands sftp:// and smb:// URIs to our own readers, through [AudioCache], and everything else
 * (content://, file://) to ExoPlayer's default.
 */
class RoutingDataSourceFactory(context: Context) : DataSource.Factory {
    private val default = DefaultDataSource.Factory(context)
    private val remote = AudioCache.dataSourceFactory(context)
    override fun createDataSource(): DataSource = RoutingDataSource(remote.createDataSource(), default.createDataSource())
}

/** Our own SFTP / SMB readers, without caching. */
class RemoteDataSourceFactory : DataSource.Factory {
    override fun createDataSource(): DataSource = object : DataSource {
        private var current: DataSource? = null
        private val listeners = mutableListOf<TransferListener>()

        override fun addTransferListener(transferListener: TransferListener) { listeners += transferListener }

        override fun open(dataSpec: DataSpec): Long {
            val ds = when (dataSpec.uri.scheme) {
                "sftp" -> SftpDataSource()
                "smb" -> SmbDataSource()
                else -> throw IOException("Not a remote uri: ${dataSpec.uri}")
            }
            listeners.forEach(ds::addTransferListener)
            current = ds
            return ds.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int) = current!!.read(buffer, offset, length)
        override fun getUri(): Uri? = current?.uri
        override fun close() {
            try { current?.close() } finally { current = null }
        }
    }
}

private class RoutingDataSource(private val remote: DataSource, private val fallback: DataSource) : DataSource {
    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        remote.addTransferListener(transferListener)
        fallback.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val ds = if (isRemote(dataSpec.uri)) remote else fallback
        current = ds
        return ds.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int) = current!!.read(buffer, offset, length)
    override fun getUri(): Uri? = current?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()
    override fun close() {
        try { current?.close() } finally { current = null }
    }
}

private fun sourceOf(uri: Uri) =
    SourceStore.get(uri.authority ?: "") ?: throw IOException("Music source no longer exists")

private fun remaining(spec: DataSpec, fileLength: Long): Long {
    if (spec.position > fileLength) {
        throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
    }
    return if (spec.length != C.LENGTH_UNSET.toLong()) spec.length else fileLength - spec.position
}

/** Streams a file over SFTP with pipelined read-ahead; supports seeking. */
private class SftpDataSource : BaseDataSource(true) {
    private var uri: Uri? = null
    private var file: RemoteFile? = null
    private var input: InputStream? = null
    private var bytesRemaining = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val source = sourceOf(dataSpec.uri)
        val path = dataSpec.uri.path ?: throw IOException("Bad SFTP uri")
        val f = Connections.withRetry(source) { Connections.sftp(source).open(path) }
        file = f
        bytesRemaining = remaining(dataSpec, f.length())
        input = f.ReadAheadRemoteFileInputStream(16, dataSpec.position)
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val n = input!!.read(buffer, offset, minOf(length.toLong(), bytesRemaining).toInt())
        if (n < 0) return C.RESULT_END_OF_INPUT
        bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        runCatching { input?.close() }
        runCatching { file?.close() }
        input = null; file = null
        if (opened) { opened = false; transferEnded() }
    }
}

/** Streams a file from an SMB share using large buffered reads; supports seeking. */
private class SmbDataSource : BaseDataSource(true) {
    private var uri: Uri? = null
    private var file: com.hierynomus.smbj.share.File? = null
    private var position = 0L
    private var bytesRemaining = 0L
    private var opened = false
    private val buf = ByteArray(256 * 1024)
    private var bufStart = 0L
    private var bufLen = 0

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        transferInitializing(dataSpec)
        val source = sourceOf(dataSpec.uri)
        val path = Connections.smbPath(dataSpec.uri.path ?: "")
        val f = Connections.withRetry(source) {
            Connections.smb(source).openFile(
                path, EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN, null,
            )
        }
        file = f
        val length = f.getFileInformation(FileStandardInformation::class.java).endOfFile
        bytesRemaining = remaining(dataSpec, length)
        position = dataSpec.position
        bufLen = 0
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        if (position < bufStart || position >= bufStart + bufLen) {
            val want = minOf(buf.size.toLong(), bytesRemaining).toInt()
            val n = file!!.read(buf, position, 0, want)
            if (n <= 0) return C.RESULT_END_OF_INPUT
            bufStart = position
            bufLen = n
        }
        val inBuf = (position - bufStart).toInt()
        val n = minOf(length, bufLen - inBuf, minOf(bytesRemaining, Int.MAX_VALUE.toLong()).toInt())
        System.arraycopy(buf, inBuf, buffer, offset, n)
        position += n
        bytesRemaining -= n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        runCatching { file?.close() }
        file = null
        bufLen = 0
        if (opened) { opened = false; transferEnded() }
    }
}
