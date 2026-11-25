package com.petiht.conuco.ui

import android.net.Uri
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.petiht.conuco.tr
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Leaf camera (CameraX). A frame shows where the leaf goes; an analyzer reads the live preview on the device
 * (brightness and sharpness from the Y plane, ~3 times a second) and tells the farmer before the shot if it is too
 * dark, too bright or blurry. The saved photo stays in the app cache and is redrawn without EXIF by Photos.load.
 */
@Composable
fun CameraScreen(onPhoto: (Uri) -> Unit, onGallery: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val c = cc
    var light by remember { mutableStateOf("ok") }   // dark | bright | ok
    var sharp by remember { mutableStateOf(true) }
    var torch by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    DisposableEffect(owner) {
        val exec = Executors.newSingleThreadExecutor()
        var last = 0L
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
            it.setAnalyzer(exec) { img ->
                val now = System.currentTimeMillis()
                if (now - last > 300) {
                    last = now
                    val (luma, edge) = Quality.measure(img)
                    light = when { luma < 55 -> "dark"; luma > 215 -> "bright"; else -> "ok" }
                    sharp = edge >= Quality.SHARP
                }
                img.close()
            }
        }
        val future = ProcessCameraProvider.getInstance(ctx)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider.unbindAll()
                camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
            }.onFailure { Log.w("Conuco", "camera bind failed", it) }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose { runCatching { future.get().unbindAll() }; exec.shutdown() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        // Leaf frame
        val frameColor = if (light == "ok" && sharp) Color(0xFF4FB477) else c.sun
        Box(Modifier.align(Alignment.Center).fillMaxWidth(0.78f).aspectRatio(0.8f).drawBehind {
            drawRoundRect(frameColor, cornerRadius = CornerRadius(28.dp.toPx()),
                style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 14f))))
        })

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, Modifier.background(Color(0x66000000), CircleShape)) { Icon(Icons.Outlined.Close, tr("cam_close"), tint = Color.White) }
                Text(tr("cam_frame"), Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.size(48.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Hint(if (sharp) tr("cam_sharp") else tr("cam_blur"), sharp)
                Hint(when (light) { "dark" -> tr("cam_dark"); "bright" -> tr("cam_bright"); else -> tr("cam_ok") }, light == "ok")
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onGallery, Modifier.size(52.dp).background(Color(0x66000000), CircleShape)) { Icon(Icons.Outlined.PhotoLibrary, tr("gallery"), tint = Color.White) }
                Box(Modifier.size(82.dp).border(5.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                    .background(if (busy) c.muted else Color(0xFF1F6F3F))
                    .clickable(enabled = !busy) {
                        busy = true
                        val f = File(File(ctx.cacheDir, "photos").apply { mkdirs() }, "leaf.jpg")
                        capture.takePicture(ImageCapture.OutputFileOptions.Builder(f).build(), ContextCompat.getMainExecutor(ctx),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(r: ImageCapture.OutputFileResults) { busy = false; onPhoto(Uri.fromFile(f)) }
                                override fun onError(e: ImageCaptureException) { busy = false; Log.w("Conuco", "capture failed", e) }
                            })
                    }.semantics { contentDescription = tr("cam_shoot") },
                    contentAlignment = Alignment.Center) { if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp) }
                IconButton(onClick = { torch = !torch; camera?.cameraControl?.enableTorch(torch) }, Modifier.size(52.dp).background(if (torch) c.sun else Color(0x66000000), CircleShape)) {
                    Icon(if (torch) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff, tr("cam_flash"), tint = if (torch) Color.Black else Color.White)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.Shield, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(tr("cam_private"), color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Hint(text: String, good: Boolean) {
    Row(Modifier.clip(RoundedCornerShape(99.dp)).background(if (good) Color(0xCC1F6F3F) else Color(0xCCB45309)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (good) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** Photo quality from the camera's luminance (Y) plane, center 60% only, sampled on a grid. Pure math, runs on the device. */
object Quality {
    /** Mean absolute Laplacian above this counts as sharp (tuned on a Galaxy S24 Ultra preview). */
    var SHARP = 5.0

    fun measure(img: ImageProxy): Pair<Double, Double> {
        val plane = img.planes[0]
        val buf = plane.buffer
        val rs = plane.rowStride; val ps = plane.pixelStride
        val w = img.width; val h = img.height
        val x0 = (w * 0.2).toInt(); val x1 = (w * 0.8).toInt(); val y0 = (h * 0.2).toInt(); val y1 = (h * 0.8).toInt()
        fun px(x: Int, y: Int) = buf.get(y * rs + x * ps).toInt() and 0xFF
        var sum = 0L; var edges = 0L; var n = 0
        var y = y0 + 2
        while (y < y1 - 2) {
            var x = x0 + 2
            while (x < x1 - 2) {
                val p = px(x, y)
                sum += p
                edges += abs(4 * p - px(x - 2, y) - px(x + 2, y) - px(x, y - 2) - px(x, y + 2))
                n++
                x += 6
            }
            y += 6
        }
        if (n == 0) return 128.0 to 99.0
        return sum.toDouble() / n to edges.toDouble() / n
    }
}
