package com.pluckcode.tvmusic.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

private const val CONTACT_EMAIL = "info@pluckcode.com"

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
    val qr = remember { qrBitmap("mailto:$CONTACT_EMAIL?subject=TV%20Music").asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.width(720.dp),
        confirmButton = { TextButton(onDismiss) { Text("Done") } },
        title = { Text("About TV Music") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Version $version", style = MaterialTheme.typography.titleMedium)
                    Text("PluckCode LLC", style = MaterialTheme.typography.titleMedium)
                    Text("© 2026 PluckCode LLC. Licensed under the Apache License 2.0.")
                    Text("Questions or feedback? Scan the code with your phone to email $CONTACT_EMAIL.")
                }
                Image(qr, "QR code to email $CONTACT_EMAIL", Modifier.size(220.dp))
            }
        },
    )
}
