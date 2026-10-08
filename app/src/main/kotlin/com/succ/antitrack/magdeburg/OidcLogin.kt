package com.succ.antitrack.magdeburg

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream

private const val AUTH_HOST = "prod.tafmobile.de"
private val AUTH_URL = "https://$AUTH_HOST/magdeburg-auth/realms/magdeburg/protocol/openid-connect/auth" +
    "?client_id=mvb&response_type=code&ui_locales=de&redirect_uri=" +
    Uri.encode("https://$AUTH_HOST/restapi/keycloak?client_id=mpmagdeburg&returnVersion=APPLINK")

private fun tokensFrom(uri: Uri): Tokens? = when (uri.scheme) {
    "prod.mvbapp" -> fromUri(uri)
    "intent" -> fromUri(Uri.parse("prod.mvbapp:" + uri.toString().removePrefix("intent:").substringBefore("#Intent")))
    else -> null
}

private const val FIT_CSS = "(function(){if(document.getElementById('at-fit'))return;" +
    "var s=document.createElement('style');s.id='at-fit';" +
    "s.textContent='.container{height:auto!important;min-height:100vh;align-items:flex-start!important}';" +
    "(document.head||document.documentElement).appendChild(s)})()"

fun wipeWebSession() = wipe(null)

private fun wipe(wv: WebView?) {
    CookieManager.getInstance().apply {
        removeAllCookies(null)
        flush()
        setAcceptCookie(false)
    }
    WebStorage.getInstance().deleteAllData()
    wv?.clearCache(true)
    wv?.clearHistory()
    wv?.clearFormData()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ColumnScope.OidcLoginScreen(store: Store, onLoggedIn: () -> Unit, onBack: () -> Unit) {
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    TopBar("MVB-Anmeldung", onBack = onBack)
    if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
    AndroidView(

        modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(),
        factory = { ctx ->
            WebView(ctx).apply {
                CookieManager.getInstance().setAcceptCookie(true)
                val webView = this
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setGeolocationEnabled(false)
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    safeBrowsingEnabled = false
                }
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val uri = request.url
                        tokensFrom(uri)?.let { t ->
                            store.saveTokens(t.access, t.refresh)
                            wipe(view)
                            view.stopLoading()
                            onLoggedIn()
                            return true
                        }
                        if (uri.scheme == "prod.mvbapp" || uri.scheme == "intent") {
                            error = "Die Anmeldung kam ohne Token zurück."
                            return true
                        }

                        return !(uri.scheme == "https" && uri.host == AUTH_HOST)
                    }

                    override fun onPageCommitVisible(view: WebView, url: String?) {
                        view.evaluateJavascript(FIT_CSS, null)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(FIT_CSS, null)
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        if (request.url.scheme == "https" && request.url.host == AUTH_HOST) null
                        else WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }

                CookieManager.getInstance().removeAllCookies { loadUrl(AUTH_URL) }
            }
        },
        onRelease = { wv ->
            wipe(wv)
            wv.destroy()
        },
    )
}
