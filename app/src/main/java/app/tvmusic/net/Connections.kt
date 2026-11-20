package app.tvmusic.net

import app.tvmusic.data.SftpAuth
import app.tvmusic.data.Source
import app.tvmusic.data.SourceStore
import app.tvmusic.data.SourceType
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.security.bc.BCSecurityProvider
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.io.Closeable
import java.security.PublicKey
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Pools one live SFTP / SMB connection per source and reconnects when it goes stale. */
object Connections {

    class SftpConn(val ssh: SSHClient, val sftp: SFTPClient) : Closeable {
        fun alive() = ssh.isConnected && ssh.isAuthenticated
        override fun close() {
            runCatching { sftp.close() }
            runCatching { ssh.disconnect() }
        }
    }

    class SmbConn(
        private val client: SMBClient,
        private val connection: Connection,
        private val session: Session,
        val share: DiskShare,
    ) : Closeable {
        fun alive() = connection.isConnected && share.isConnected
        override fun close() {
            runCatching { share.close() }
            runCatching { session.close() }
            runCatching { connection.close() }
            runCatching { client.close() }
        }
    }

    private val sftpPool = ConcurrentHashMap<String, SftpConn>()
    private val smbPool = ConcurrentHashMap<String, SmbConn>()
    private val locks = ConcurrentHashMap<String, Any>()
    private fun lock(id: String) = locks.getOrPut(id) { Any() }

    fun sftp(source: Source): SFTPClient = synchronized(lock(source.id)) {
        sftpPool[source.id]?.takeIf { it.alive() }?.let { return it.sftp }
        sftpPool.remove(source.id)?.close()
        val conn = openSftp(source) { fp ->
            // Trust on first use: remember the server's host key.
            SourceStore.get(source.id)?.let { SourceStore.upsert(it.copy(hostKeyFingerprint = fp)) }
        }
        sftpPool[source.id] = conn
        conn.sftp
    }

    fun smb(source: Source): DiskShare = synchronized(lock(source.id)) {
        smbPool[source.id]?.takeIf { it.alive() }?.let { return it.share }
        smbPool.remove(source.id)?.close()
        val conn = openSmb(source)
        smbPool[source.id] = conn
        conn.share
    }

    /** Runs [block], and if it fails once, drops the pooled connection and tries again. */
    fun <T> withRetry(source: Source, block: () -> T): T = try {
        block()
    } catch (e: Exception) {
        if (e is UserAuthFailure) throw e
        invalidate(source.id)
        block()
    }

    fun invalidate(id: String) {
        synchronized(lock(id)) {
            sftpPool.remove(id)?.close()
            smbPool.remove(id)?.close()
        }
    }

    data class TestResult(val fingerprint: String, val entries: Int)

    /** Connects with an unsaved config (from the editor), lists the start folder, disconnects. */
    fun test(source: Source): TestResult = when (source.type) {
        SourceType.SFTP -> {
            var learned = source.hostKeyFingerprint
            openSftp(source) { learned = it }.use { c ->
                val dir = c.sftp.canonicalize(source.path.ifBlank { "." })
                TestResult(learned, c.sftp.ls(dir).size)
            }
        }
        SourceType.SMB -> openSmb(source).use { c ->
            TestResult("", c.share.list(smbPath(source.path)).size)
        }
        else -> TestResult("", 0)
    }

    fun smbPath(p: String) = p.trim().replace('/', '\\').trim('\\')

    private fun openSftp(source: Source, onLearn: (String) -> Unit): SftpConn {
        val ssh = SSHClient(DefaultConfig())
        ssh.addHostKeyVerifier(TofuVerifier(source.hostKeyFingerprint, onLearn))
        ssh.connectTimeout = 10_000
        ssh.timeout = 30_000
        try {
            ssh.connect(source.host.trim(), if (source.port > 0) source.port else 22)
            when (source.auth) {
                SftpAuth.KEY -> {
                    if (source.privateKey.isBlank()) throw UserAuthFailure("No private key configured")
                    val finder = source.passphrase.takeIf { it.isNotEmpty() }
                        ?.let { PasswordUtils.createOneOff(it.toCharArray()) }
                    val keys = ssh.loadKeys(source.privateKey.trim() + "\n", source.publicKey.trim().ifEmpty { null }, finder)
                    ssh.authPublickey(source.username.trim(), keys)
                }
                SftpAuth.PASSWORD -> ssh.authPassword(source.username.trim(), source.password)
            }
            ssh.connection.keepAlive.keepAliveInterval = 30
            return SftpConn(ssh, ssh.newSFTPClient())
        } catch (e: Exception) {
            runCatching { ssh.disconnect() }
            throw e
        }
    }

    private fun openSmb(source: Source): SmbConn {
        val config = SmbConfig.builder()
            .withSecurityProvider(BCSecurityProvider())
            .withTimeout(30, TimeUnit.SECONDS)
            .withSoTimeout(60, TimeUnit.SECONDS)
            .build()
        val client = SMBClient(config)
        try {
            val connection = client.connect(source.host.trim(), if (source.port > 0) source.port else 445)
            val auth = if (source.username.isBlank()) AuthenticationContext.guest()
            else AuthenticationContext(source.username.trim(), source.password.toCharArray(), source.domain.trim().ifEmpty { null })
            val session = connection.authenticate(auth)
            val share = session.connectShare(source.share.trim().trim('\\', '/')) as? DiskShare
                ?: throw IllegalArgumentException("'${source.share}' is not a disk share")
            return SmbConn(client, connection, session, share)
        } catch (e: Exception) {
            runCatching { client.close() }
            throw e
        }
    }

    class UserAuthFailure(msg: String) : Exception(msg)

    private class TofuVerifier(
        private val expected: String,
        private val onLearn: (String) -> Unit,
    ) : HostKeyVerifier {
        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val fp = SecurityUtils.getFingerprint(key)
            if (expected.isEmpty()) {
                onLearn(fp); return true
            }
            return expected.equals(fp, ignoreCase = true)
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
    }
}

/** Best human-readable message in a cause chain. */
fun Throwable.describe(): String {
    val msgs = generateSequence(this) { it.cause }.mapNotNull { it.message?.takeIf(String::isNotBlank) }.toList()
    val m = msgs.firstOrNull() ?: javaClass.simpleName
    return when {
        "HOST_KEY_NOT_VERIFIABLE" in m -> "Server host key changed! If expected, use \"Forget host key\" in the source settings. ($m)"
        "Exhausted available authentication" in m -> "Authentication failed (wrong user, password, or key not in authorized_keys)"
        else -> m
    }
}
