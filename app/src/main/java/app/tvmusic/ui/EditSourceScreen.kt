package app.tvmusic.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tvmusic.data.SftpAuth
import app.tvmusic.data.Source
import app.tvmusic.data.SourceStore
import app.tvmusic.data.SourceType
import app.tvmusic.net.Connections
import app.tvmusic.net.KeyGen
import app.tvmusic.net.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun EditSourceScreen(initial: Source, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var d by remember { mutableStateOf(initial) }
    var status by remember { mutableStateOf<String?>(null) }
    var showPublicKey by remember { mutableStateOf(false) }
    val isNew = SourceStore.get(initial.id) == null
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    val keyFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        }.getOrElse { status = "Could not read file: ${it.describe()}"; return@rememberLauncherForActivityResult }
        when {
            "PRIVATE KEY" in text || text.startsWith("PuTTY-User-Key-File") -> {
                d = d.copy(privateKey = text.trim(), auth = SftpAuth.KEY); status = "Private key imported"
            }
            text.trimStart().startsWith("ssh-") || text.trimStart().startsWith("ecdsa-") -> {
                d = d.copy(publicKey = text.trim()); status = "Public key imported"
            }
            else -> status = "That file doesn't look like an SSH key"
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        d = d.copy(treeUri = uri.toString(), name = d.name.ifBlank { uri.lastPathSegment?.substringAfterLast(':') ?: "" })
    }
    fun launchSafely(block: () -> Unit) = try { block() } catch (_: ActivityNotFoundException) {
        status = "This TV has no file picker. Paste the key, generate one, or use “Music on this device”."
    }

    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            (if (isNew) "Add " else "Edit ") + when (d.type) {
                SourceType.SFTP -> "SFTP server"; SourceType.SMB -> "SMB share"; else -> "local folder"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Field("Display name (optional)", d.name, { d = d.copy(name = it) }, Modifier.focusRequester(first))

        when (d.type) {
            SourceType.SFTP -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Host", d.host, { d = d.copy(host = it) }, Modifier.weight(3f), type = KeyboardType.Uri)
                    Field("Port", d.port.toString(), { d = d.copy(port = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) }, Modifier.weight(1f), type = KeyboardType.Number)
                }
                Field("Username", d.username, { d = d.copy(username = it) })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Login with:")
                    TvCard({ d = d.copy(auth = SftpAuth.PASSWORD) }, selected = d.auth == SftpAuth.PASSWORD) {
                        Text((if (d.auth == SftpAuth.PASSWORD) "● " else "○ ") + "Password")
                    }
                    TvCard({ d = d.copy(auth = SftpAuth.KEY) }, selected = d.auth == SftpAuth.KEY) {
                        Text((if (d.auth == SftpAuth.KEY) "● " else "○ ") + "Public/private key")
                    }
                }
                if (d.auth == SftpAuth.PASSWORD) {
                    Field("Password", d.password, { d = d.copy(password = it) }, secret = true)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton("Import key file", { launchSafely { keyFilePicker.launch(arrayOf("*/*")) } }, icon = Icons.Filled.FileOpen)
                        TvButton("Generate new key on TV", {
                            val kp = KeyGen.ed25519()
                            d = d.copy(privateKey = kp.privatePem, publicKey = kp.publicLine, passphrase = "")
                            showPublicKey = true
                        }, icon = Icons.Filled.Key)
                        if (d.publicKey.isNotBlank()) {
                            TvButton("Show public key", { showPublicKey = true }, icon = Icons.Filled.QrCode)
                        }
                    }
                    Field(
                        "Private key (OpenSSH / PEM / PuTTY)", d.privateKey,
                        { d = d.copy(privateKey = it) }, multiLine = true,
                    )
                    Field("Public key (optional)", d.publicKey, { d = d.copy(publicKey = it) })
                    Field("Key passphrase (if encrypted)", d.passphrase, { d = d.copy(passphrase = it) }, secret = true)
                }
                Field("Start folder (blank = home directory)", d.path, { d = d.copy(path = it) })
                if (d.hostKeyFingerprint.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Trusted host key: ${d.hostKeyFingerprint}", Modifier.weight(1f), fontFamily = FontFamily.Monospace)
                        TvButton("Forget host key", { d = d.copy(hostKeyFingerprint = "") })
                    }
                }
            }
            SourceType.SMB -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Server (name or IP)", d.host, { d = d.copy(host = it) }, Modifier.weight(3f), type = KeyboardType.Uri)
                    Field("Port", d.port.toString(), { d = d.copy(port = it.filter(Char::isDigit).take(5).toIntOrNull() ?: 0) }, Modifier.weight(1f), type = KeyboardType.Number)
                }
                Field("Share name (e.g. Music)", d.share, { d = d.copy(share = it) })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Username (blank = guest)", d.username, { d = d.copy(username = it) }, Modifier.weight(1f))
                    Field("Domain / workgroup", d.domain, { d = d.copy(domain = it) }, Modifier.weight(1f))
                }
                Field("Password", d.password, { d = d.copy(password = it) }, secret = true)
                Field("Start folder inside share (optional)", d.path, { d = d.copy(path = it) })
            }
            SourceType.FOLDER -> {
                Text(if (d.treeUri.isBlank()) "No folder chosen yet." else "Folder: ${Uri.parse(d.treeUri).lastPathSegment}")
                TvButton("Choose folder…", { launchSafely { folderPicker.launch(null) } }, icon = Icons.Filled.FileOpen)
            }
            SourceType.LIBRARY -> {}
        }

        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("Save", {
                val err = validate(d)
                if (err != null) { status = err; return@TvButton }
                SourceStore.upsert(d)
                Connections.invalidate(d.id)
                onDone()
            }, icon = Icons.Filled.Save)
            if (d.type == SourceType.SFTP || d.type == SourceType.SMB) {
                TvButton("Test connection", {
                    validate(d)?.let { status = it; return@TvButton }
                    status = "Connecting…"
                    scope.launch {
                        status = withContext(Dispatchers.IO) {
                            runCatching { Connections.test(d) }.fold(
                                { r ->
                                    if (r.fingerprint.isNotEmpty()) d = d.copy(hostKeyFingerprint = r.fingerprint)
                                    "Connected ✓  ${r.entries} items in start folder"
                                },
                                { "Failed: ${it.describe()}" },
                            )
                        }
                    }
                }, icon = Icons.Filled.NetworkCheck)
            }
            TvButton("Cancel", onDone)
            if (!isNew) TvButton("Delete", {
                SourceStore.remove(d.id); Connections.invalidate(d.id); onDone()
            }, icon = Icons.Filled.Delete)
        }
    }

    if (showPublicKey && d.publicKey.isNotBlank()) {
        val qr = remember(d.publicKey) { qrBitmap(d.publicKey.trim()).asImageBitmap() }
        AlertDialog(
            onDismissRequest = { showPublicKey = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.width(820.dp),
            confirmButton = { TextButton({ showPublicKey = false }) { Text("Done") } },
            title = { Text("Add this public key to the server") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(qr, "Public key QR code", Modifier.size(260.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Scan with your phone and append it to ~/.ssh/authorized_keys for “${d.username.ifBlank { "your user" }}” on the server, then Save and Test.")
                        Text(d.publicKey.trim(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
                    }
                }
            },
        )
    }
}

private fun validate(d: Source): String? = when (d.type) {
    SourceType.SFTP -> when {
        d.host.isBlank() -> "Host is required"
        d.username.isBlank() -> "Username is required"
        d.auth == SftpAuth.KEY && d.privateKey.isBlank() -> "Import, paste or generate a private key"
        else -> null
    }
    SourceType.SMB -> when {
        d.host.isBlank() -> "Server is required"
        d.share.isBlank() -> "Share name is required"
        else -> null
    }
    SourceType.FOLDER -> if (d.treeUri.isBlank()) "Choose a folder first" else null
    SourceType.LIBRARY -> null
}

/**
 * TV-friendly text setting: a focusable row showing the value. Pressing OK opens a dialog with the
 * keyboard, so D-padding through the form never pops the keyboard up uninvited.
 */
@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    multiLine: Boolean = false,
    type: KeyboardType = KeyboardType.Text,
) {
    var editing by remember { mutableStateOf(false) }
    TvCard({ editing = true }, modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            val shown = when {
                value.isEmpty() -> "—"
                secret -> "•".repeat(minOf(value.length, 12))
                multiLine -> value.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() +
                    if (value.lines().size > 1) "  (${value.lines().size} lines)" else ""
                else -> value
            }
            Text(shown, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
    if (editing) {
        var text by remember { mutableStateOf(TextFieldValue(value, TextRange(0, value.length))) }
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
        val commit = { onChange(text.text); editing = false }
        AlertDialog(
            onDismissRequest = { editing = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.width(720.dp),
            title = { Text(label) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(fr),
                    singleLine = !multiLine,
                    minLines = if (multiLine) 6 else 1,
                    maxLines = if (multiLine) 10 else 1,
                    textStyle = if (multiLine) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    else MaterialTheme.typography.bodyLarge,
                    visualTransformation = if (secret && !multiLine) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (secret && !multiLine) KeyboardType.Password else type,
                        autoCorrectEnabled = false,
                        imeAction = if (multiLine) ImeAction.Default else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                )
            },
            confirmButton = { TextButton(commit) { Text("OK") } },
            dismissButton = { TextButton({ editing = false }) { Text("Cancel") } },
        )
    }
}
