package com.pluckcode.tvmusic.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

val TvColors = darkColorScheme(
    primary = Color(0xFF8C9EFF),
    primaryContainer = Color(0xFF3D5AFE),
    onPrimaryContainer = Color.White,
    background = Color(0xFF0E1116),
    surface = Color(0xFF171B22),
    surfaceVariant = Color(0xFF232833),
)

@Composable
fun TvTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = TvColors, content = content)

/** A D-pad focusable surface with an obvious focus highlight (TV remotes have no hover). */
@Composable
fun TvCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    selected: Boolean = false,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    interaction: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .scale(if (focused) 1.03f else 1f)
            .clip(shape)
            .background(
                when {
                    focused -> colors.primaryContainer
                    selected -> colors.surfaceVariant
                    else -> colors.surface
                }
            )
            .border(2.dp, if (focused) colors.primary else Color.Transparent, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) { content(focused) }
}

@Composable
fun TvButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    TvCard(onClick, modifier) {
        if (icon != null) Icon(icon, null)
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** An icon-only button that names its action in a word underneath while focused or hovered. */
@Composable
fun TvIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    active: Boolean = false,
    label: String = description,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        TvCard(onClick, modifier, shape = CircleShape, selected = active, padding = androidx.compose.foundation.layout.PaddingValues(10.dp), interaction = interaction) {
            Box(Modifier.size(if (big) 44.dp else 30.dp), contentAlignment = Alignment.Center) {
                Icon(
                    icon, description,
                    Modifier.size(if (big) 44.dp else 28.dp),
                    tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (focused || hovered) {
            Text(
                label,
                // Takes no space, so showing it doesn't shift the buttons; it just hangs below, centred.
                Modifier.layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = 0))
                    layout(0, 0) { p.place(-p.width / 2, 4.dp.roundToPx()) }
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms < 0) return "--:--"
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

fun qrBitmap(text: String, size: Int = 600): Bitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { if (m[it % size, it / size]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888)
}
