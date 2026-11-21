package com.petiht.conuco.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petiht.conuco.Crops
import com.petiht.conuco.tr

@Composable
fun CCard(modifier: Modifier = Modifier, border: Color? = null, bg: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = cc
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg ?: c.card).border(1.dp, border ?: c.line, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

@Composable
fun Title(text: String, icon: ImageVector? = null) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    if (icon != null) Icon(icon, null, tint = cc.green, modifier = Modifier.size(20.dp))
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
fun Muted(text: String, size: Int = 14) = Text(text, color = cc.muted, fontSize = size.sp)

@Composable
fun Pill(text: String, fg: Color, bg: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(15.dp))
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SourceBadge(source: String, ms: Long = 0) {
    val c = cc
    val (fg, bg, ic) = when (source) {
        "on_device" -> Triple(c.onGreen, c.green, Icons.Outlined.Memory)
        "in_cloud" -> Triple(Color.White, c.cloud, Icons.Outlined.Cloud)
        "grounded" -> Triple(Color.White, c.cloud, Icons.Outlined.TravelExplore)
        else -> Triple(Color(0xFF1F1A00), c.sun, Icons.Outlined.Rule)
    }
    Pill(tr("src_$source") + if (ms > 0) " · ${"%.1f".format(ms / 1000.0)} s" else "", fg, bg, ic)
}

@Composable
fun TraceBox(lines: List<String>) {
    val c = cc
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Route, null, tint = c.green, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(tr("why"), color = c.green, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(if (open) "−" else "+", color = c.green, fontWeight = FontWeight.ExtraBold)
        }
        if (open) lines.forEachIndexed { i, t -> Text("${i + 1}. $t", fontSize = 13.sp, color = c.muted, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable
fun Dot(color: Color, size: Int = 10) = Box(Modifier.size(size.dp).clip(CircleShape).background(color))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CropChips(options: List<String>, selected: Set<String>, lang: String, onToggle: (String) -> Unit) {
    val c = cc
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { k ->
            val on = k in selected
            FilterChip(
                selected = on, onClick = { onToggle(k) },
                label = { Text(Crops.name(k, lang), fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Dot(Color(Crops.get(k).color)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.green, selectedLabelColor = c.onGreen),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TextChips(options: List<Pair<String, String>>, selected: Set<String>, onToggle: (String) -> Unit) {
    val c = cc
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (k, label) ->
            FilterChip(selected = k in selected, onClick = { onToggle(k) }, label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.green, selectedLabelColor = c.onGreen))
        }
    }
}

@Composable
fun Decision(icon: ImageVector, title: String, main: String, detail: String, tone: String, modifier: Modifier = Modifier) {
    val c = cc
    val bar = when (tone) { "good" -> c.green; "mid" -> c.sun; else -> c.red }
    Row(modifier.clip(RoundedCornerShape(16.dp)).background(c.card).border(1.dp, c.line, RoundedCornerShape(16.dp)).height(IntrinsicSize.Min)) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(bar))
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = c.muted, modifier = Modifier.size(16.dp))
                Text(title.uppercase(), color = c.muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
            Text(main, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            if (detail.isNotBlank()) Text(detail, color = c.muted, fontSize = 12.sp)
        }
    }
}

@Composable
fun Note(text: String, warn: Boolean = false, icon: ImageVector? = null) {
    val c = cc
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (warn) c.amberSoft else c.surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = if (warn) c.amber else c.green, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 14.sp, color = if (warn) c.amber else c.ink)
    }
}

@Composable
fun BigButton(text: String, icon: ImageVector? = null, enabled: Boolean = true, color: Color? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = cc
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color ?: c.green, contentColor = if (color == null) c.onGreen else Color.White),
    ) {
        if (icon != null) { Icon(icon, null); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
