package com.succ.antitrack.magdeburg

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.ParcelFileDescriptor
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File

@Composable
private fun TicketBrightness() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        val w = activity.window
        val before = w.attributes.screenBrightness
        w.attributes = w.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
        onDispose { w.attributes = w.attributes.apply { screenBrightness = before } }
    }
}

@Composable
private fun TiltFeed(webView: () -> WebView?) {
    val ctx = LocalContext.current
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rot = FloatArray(16)
        val remapped = FloatArray(16)
        val o = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Y, remapped)
                SensorManager.getOrientation(remapped, o)
                val a = Math.toDegrees(o[0].toDouble())
                val b = Math.toDegrees(o[1].toDouble())
                val g = Math.toDegrees(o[2].toDouble()).coerceIn(-89.99, 89.99)
                webView()?.evaluateJavascript(
                    "typeof handleOrientation==='function'&&handleOrientation({alpha:'$a',beta:'$b',gamma:'$g'})", null,
                )
            }
            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm?.unregisterListener(listener) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TicketHtml(html: String, modifier: Modifier = Modifier) {
    TicketBrightness()
    val holder = remember { arrayOfNulls<WebView>(1) }
    TiltFeed { holder[0] }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).also { holder[0] = it }.apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    domStorageEnabled = false
                    @Suppress("DEPRECATION")
                    databaseEnabled = false
                    setGeolocationEnabled(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    blockNetworkLoads = true
                    safeBrowsingEnabled = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                val webView = this
                CookieManager.getInstance().apply {
                    setAcceptCookie(false)
                    setAcceptThirdPartyCookies(webView, false)
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        !request.url.toString().startsWith("data:")

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        if (request.url.scheme == "data") null
                        else WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        update = { wv ->

            if (wv.tag != html.hashCode()) {
                if (wv.tag != null) wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                wv.tag = html.hashCode()
            }
        },
        onRelease = { holder[0] = null; it.destroy() },
    )
}

@Composable
fun TicketPdf(pdf: ByteArray, modifier: Modifier = Modifier) {
    TicketBrightness()
    val ctx = LocalContext.current
    val pages by produceState<List<Bitmap>?>(null, pdf) {
        value = withContext(Dispatchers.IO) { renderPdf(File(ctx.cacheDir, "ticket.pdf"), pdf) }
    }
    Column(modifier.verticalScroll(rememberScrollState())) {
        when (val p = pages) {
            null -> CircularProgressIndicator(Modifier.padding(8.dp))
            else -> if (p.isEmpty()) Text("PDF konnte nicht gelesen werden.", color = MaterialTheme.colorScheme.error)
            else p.forEach {
                Image(
                    it.asImageBitmap(), contentDescription = "Ticketseite",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentScale = ContentScale.FillWidth,
                )
            }
        }
    }
}

private fun renderPdf(file: File, pdf: ByteArray): List<Bitmap> = try {
    file.writeBytes(pdf)
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { r ->
            (0 until r.pageCount).map { i ->
                r.openPage(i).use { page ->
                    val width = 1400
                    val height = width * page.height / page.width
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }
} catch (e: Exception) {
    emptyList()
} finally {
    file.delete()
}
