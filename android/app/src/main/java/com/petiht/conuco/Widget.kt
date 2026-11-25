package com.petiht.conuco

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/** Home-screen widget: today's field decision at a glance, plus one-tap Diagnose and Ask. Data comes from Bg.updateWidget. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sp = context.getSharedPreferences(Bg.WIDGET, Context.MODE_PRIVATE)
        Strings.lang = sp.getString("lang", if (java.util.Locale.getDefault().language == "es") "es" else "en")!!
        val head = sp.getString("head", null)
        val sub = sp.getString("sub", "") ?: ""
        val place = sp.getString("place", "") ?: ""
        val temp = sp.getString("temp", "") ?: ""
        val ok = sp.getBoolean("ok", true)
        provideContent { Content(context, head, sub, place, temp, ok) }
    }

    @androidx.compose.runtime.Composable
    private fun Content(ctx: Context, head: String?, sub: String, place: String, temp: String, ok: Boolean) {
        val bg = ColorProvider(day = Color(0xFFF4F7F3), night = Color(0xFF141B17))
        val ink = ColorProvider(day = Color(0xFF10231A), night = Color(0xFFE8EFE9))
        val muted = ColorProvider(day = Color(0xFF5B6D63), night = Color(0xFF9DB0A5))
        val card = ColorProvider(day = Color.White, night = Color(0xFF171F1B))
        val decision = if (ok) ColorProvider(day = Color(0xFF1F6F3F), night = Color(0xFF1F6F3F)) else ColorProvider(day = Color(0xFFB45309), night = Color(0xFF8A4A10))
        val white = ColorProvider(day = Color.White, night = Color.White)
        val green = ColorProvider(day = Color(0xFF1F6F3F), night = Color(0xFF4FB477))

        Column(GlanceModifier.fillMaxSize().background(bg).cornerRadius(22.dp).padding(12.dp).clickable(actionStartActivity(open(ctx, "today")))) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Conuco", style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.defaultWeight())
                Text(listOf(temp, place).filter { it.isNotBlank() }.joinToString(" "), style = TextStyle(color = muted, fontSize = 13.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            Column(GlanceModifier.fillMaxWidth().background(decision).cornerRadius(14.dp).padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(head ?: tr("wg_open"), style = TextStyle(color = white, fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                if (sub.isNotBlank()) Text(sub, style = TextStyle(color = white, fontSize = 12.sp), maxLines = 1)
            }
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                Text("📷  " + tr("wg_diag"), style = TextStyle(color = green, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight().background(card).cornerRadius(12.dp).padding(vertical = 8.dp, horizontal = 10.dp).clickable(actionStartActivity(open(ctx, "camera"))))
                Spacer(GlanceModifier.width(8.dp))
                Text("🎤  " + tr("wg_ask"), style = TextStyle(color = green, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = GlanceModifier.defaultWeight().background(card).cornerRadius(12.dp).padding(vertical = 8.dp, horizontal = 10.dp).clickable(actionStartActivity(open(ctx, "ask"))))
            }
        }
    }

    private fun open(ctx: Context, tab: String) =
        Intent(ctx, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse("conuco://tab/$tab")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
