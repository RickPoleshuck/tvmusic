package app.tvmusic.data

import org.json.JSONObject
import java.util.UUID

enum class SourceType { SFTP, SMB, FOLDER, LIBRARY }

enum class SftpAuth { PASSWORD, KEY }

/** A place music can be browsed from. One flat type keeps persistence simple. */
data class Source(
    val id: String = UUID.randomUUID().toString(),
    val type: SourceType,
    val name: String = "",
    val host: String = "",
    val port: Int = 0,
    val username: String = "",
    val password: String = "",
    // SFTP
    val auth: SftpAuth = SftpAuth.PASSWORD,
    val privateKey: String = "",
    val publicKey: String = "",
    val passphrase: String = "",
    val hostKeyFingerprint: String = "",
    // SMB
    val share: String = "",
    val domain: String = "",
    // Start folder (SFTP absolute/relative path, SMB path inside share)
    val path: String = "",
    // Local folder picked with the storage access framework. If blank, a FOLDER source is instead the
    // media library folder in [path], chosen in-app on TVs without a system folder picker.
    val treeUri: String = "",
) {
    /** Reads from Android's media library (needs the audio permission) rather than a granted folder. */
    val usesMediaLibrary: Boolean
        get() = type == SourceType.LIBRARY || (type == SourceType.FOLDER && treeUri.isBlank())

    val displayName: String
        get() = name.ifBlank {
            when (type) {
                SourceType.SFTP -> "$username@$host"
                SourceType.SMB -> "\\\\$host\\$share"
                SourceType.FOLDER -> "Local folder"
                SourceType.LIBRARY -> "Music on this device"
            }
        }

    val subtitle: String
        get() = when (type) {
            SourceType.SFTP -> "SFTP · $username@$host:${if (port > 0) port else 22}" +
                (if (auth == SftpAuth.KEY) " · key" else "")
            SourceType.SMB -> "SMB · \\\\$host\\$share"
            SourceType.FOLDER -> "Local folder"
            SourceType.LIBRARY -> "Device & USB storage"
        }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("type", type.name); put("name", name)
        put("host", host); put("port", port); put("username", username); put("password", password)
        put("auth", auth.name); put("privateKey", privateKey); put("publicKey", publicKey)
        put("passphrase", passphrase); put("hostKeyFingerprint", hostKeyFingerprint)
        put("share", share); put("domain", domain); put("path", path); put("treeUri", treeUri)
    }

    companion object {
        val LIBRARY = Source(id = "library", type = SourceType.LIBRARY)

        fun fromJson(o: JSONObject) = Source(
            id = o.getString("id"),
            type = SourceType.valueOf(o.getString("type")),
            name = o.optString("name"),
            host = o.optString("host"),
            port = o.optInt("port"),
            username = o.optString("username"),
            password = o.optString("password"),
            auth = runCatching { SftpAuth.valueOf(o.optString("auth")) }.getOrDefault(SftpAuth.PASSWORD),
            privateKey = o.optString("privateKey"),
            publicKey = o.optString("publicKey"),
            passphrase = o.optString("passphrase"),
            hostKeyFingerprint = o.optString("hostKeyFingerprint"),
            share = o.optString("share"),
            domain = o.optString("domain"),
            path = o.optString("path"),
            treeUri = o.optString("treeUri"),
        )
    }
}
