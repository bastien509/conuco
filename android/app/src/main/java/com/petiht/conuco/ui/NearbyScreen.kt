package com.petiht.conuco.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petiht.conuco.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ---------- Nearby (outbreak radar, drawn offline) ----------
@Composable
fun NearbyScreen(s: AppState, a: Actions) {
    val c = cc
    val p = s.profile ?: return
    val radius = a.ai.outbreakRadiusKm
    val rows = s.outbreaks ?: emptyList()
    Page {
        Text(tr("mp_title"), style = MaterialTheme.typography.headlineMedium)
        Muted(tr("mp_sub"))
        val view = radius * 2.2
        Canvas(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)).background(c.card)) {
            val cx = size.width / 2; val cy = size.height / 2
            val scale = min(size.width, size.height) / 2 / view.toFloat()
            listOf(0.5, 1.0, 1.5, 2.0).forEach { k -> drawCircle(c.line, (radius * k * scale).toFloat(), Offset(cx, cy), style = Stroke(2f, pathEffect = if (k == 1.0) null else PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))) }
            drawCircle(c.green.copy(alpha = 0.07f), (radius * scale).toFloat(), Offset(cx, cy))
            rows.forEach { o ->
                val dy = (o.lat - p.lat) * 111.0
                val dx = (o.lng - p.lng) * 111.0 * cos(Math.toRadians(p.lat))
                if (kotlin.math.abs(dx) < view && kotlin.math.abs(dy) < view) {
                    val col = Color(Crops.CAT_COLOR[o.category] ?: 0xFF57534E)
                    drawCircle(col.copy(alpha = 0.35f), 16f, Offset(cx + (dx * scale).toFloat(), cy - (dy * scale).toFloat()))
                    drawCircle(col, 16f, Offset(cx + (dx * scale).toFloat(), cy - (dy * scale).toFloat()), style = Stroke(3f))
                }
            }
            drawCircle(Color.White, 14f, Offset(cx, cy)); drawCircle(c.green, 10f, Offset(cx, cy))
            drawLine(c.muted, Offset(cx, 12f), Offset(cx, 40f), 3f)
        }
        Muted("N ↑ · ${radius.toInt()} km", 12)
        val groups = Repo.alerts(rows, p.lat, p.lng, radius, 1, null)
        CCard {
            if (groups.isEmpty()) Muted(tr("mp_none"))
            groups.forEach { g -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dot(Color(Crops.CAT_COLOR[g.category] ?: 0xFF57534E))
                Text("${Crops.problemName(g.problemKey, s.lang)} · ${Crops.name(g.crop, s.lang)}", Modifier.weight(1f), fontWeight = if (g.crop in p.crops) FontWeight.ExtraBold else FontWeight.Normal)
                Muted("${g.n}× · ${g.nearestKm} km", 12)
            } }
        }
    }
}
