package cl.ccu.tiempos

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pendingWidgetArgs: WidgetArgs? = null

    data class WidgetArgs(
        val idRegistro: String,
        val patente: String,
        val eett: String,
        val startEpochMs: Long,
        val metaMin: Int
    )

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val idReg = intent?.getStringExtra("idRegistro") ?: return
            val escaped = idReg.replace("'", "\\'")
            runOnUiThread {
                webView.evaluateJavascript("if(window.pararDesdeWidgetNativo) window.pararDesdeWidgetNativo('$escaped');", null)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val ws = webView.settings
        ws.javaScriptEnabled = true
        ws.domStorageEnabled = true
        ws.databaseEnabled = true
        ws.cacheMode = WebSettings.LOAD_DEFAULT
        ws.allowFileAccess = true
        ws.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                Toast.makeText(this@MainActivity, message ?: "", Toast.LENGTH_LONG).show()
                result?.confirm()
                return true
            }
        }

        webView.addJavascriptInterface(AndroidWidgetBridge(), "AndroidWidget")
        webView.loadUrl("https://icatalanr-sys.github.io/Medicion-Tiempo/")

        val filter = IntentFilter(FloatingTimerService.ACTION_STOP_TIMER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopReceiver, filter)
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(stopReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 5001) {
            val args = pendingWidgetArgs
            pendingWidgetArgs = null
            if (args != null && Settings.canDrawOverlays(this)) {
                launchFloatingService(args)
            } else if (args != null) {
                Toast.makeText(this, "Activa 'Mostrar sobre otras aplicaciones' para usar el widget flotante.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun launchFloatingService(args: WidgetArgs) {
        val intent = Intent(this, FloatingTimerService::class.java).apply {
            putExtra("idRegistro", args.idRegistro)
            putExtra("patente", args.patente)
            putExtra("eett", args.eett)
            putExtra("startEpochMs", args.startEpochMs)
            putExtra("metaMin", args.metaMin)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    inner class AndroidWidgetBridge {
        @JavascriptInterface
        fun isAvailable(): Boolean = true

        @JavascriptInterface
        fun startFloating(idRegistro: String, patente: String, eett: String, startEpochMs: Long, metaMin: Int) {
            val args = WidgetArgs(idRegistro, patente, eett, startEpochMs, metaMin)
            runOnUiThread {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    pendingWidgetArgs = args
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivityForResult(intent, 5001)
                } else {
                    launchFloatingService(args)
                }
            }
        }

        @JavascriptInterface
        fun stopFloating() {
            runOnUiThread {
                stopService(Intent(this@MainActivity, FloatingTimerService::class.java))
            }
        }
    }
}
