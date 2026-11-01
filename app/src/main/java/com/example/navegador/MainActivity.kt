package com.example.navegador

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.*
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var callback: ValueCallback<Array<Uri>>? = null

    private val picker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        val data = r.data
        val res: Array<Uri>? = if (r.resultCode == Activity.RESULT_OK && data != null) {
            val clip = data.clipData
            when {
                clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                data.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        callback?.onReceiveValue(res)
        callback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.setBackgroundColor(0xFF0B0D12.toInt())
        setContentView(web)

        ViewCompat.setOnApplyWindowInsetsListener(web) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        val prefs = getSharedPreferences("ext", MODE_PRIVATE)
        var cur = ""

        fun runExt(v: WebView, url: String) {
            try {
                val arr = JSONArray(prefs.getString("list", "[]"))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (!o.optBoolean("on", true)) continue
                    val m = o.optString("match", "*").trim()
                    if (m != "*" && m.isNotEmpty() && !url.contains(m)) continue
                    val code = o.optString("code")
                    val js = if (o.optString("type") == "css") {
                        "(function(){var s=document.createElement('style');s.textContent=" +
                            JSONObject.quote(code) +
                            ";document.documentElement.appendChild(s)})()"
                    } else {
                        "(function(){try{\n" + code + "\n}catch(e){}})()"
                    }
                    v.evaluateJavascript(js, null)
                }
            } catch (e: Exception) {
            }
        }

        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun getExt(): String {
                return if (cur.startsWith("file:///android_asset"))
                    prefs.getString("list", "[]") ?: "[]"
                else "[]"
            }

            @JavascriptInterface
            fun setExt(s: String) {
                if (cur.startsWith("file:///android_asset"))
                    prefs.edit().putString("list", s).apply()
            }
        }, "Android")
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(
                view: WebView, url: String, favicon: android.graphics.Bitmap?
            ) {
                cur = url
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (url.endsWith("android_asset/index.html")) {
                    view.evaluateJavascript(
                        "(function(){var b=document.createElement('button');" +
                            "b.textContent='Extensiones';" +
                            "b.onclick=function(){location.href='ext.html'};" +
                            "document.getElementById('dock').appendChild(b)})()",
                        null
                    )
                } else if (url.startsWith("http")) {
                    runExt(view, url)
                }
            }
        }

        web.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val req = DownloadManager.Request(Uri.parse(url))
                req.setMimeType(mimeType)
                req.addRequestHeader("User-Agent", userAgent)
                val cookie = CookieManager.getInstance().getCookie(url)
                if (cookie != null) req.addRequestHeader("Cookie", cookie)
                req.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(req)
                Toast.makeText(this, "Descargando: " + name, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "No se pudo descargar", Toast.LENGTH_SHORT).show()
            }
        }
        web.webChromeClient = object : WebChromeClient() {

            override fun onShowFileChooser(
                view: WebView,
                cb: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                callback?.onReceiveValue(null)
                callback = cb
                return try {
                    picker.launch(params.createIntent())
                    true
                } catch (e: Exception) {
                    callback = null
                    false
                }
            }

            override fun onJsAlert(
                view: WebView, url: String, message: String, result: JsResult
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(
                view: WebView, url: String, message: String, result: JsResult
            ): Boolean {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setPositiveButton("Sí") { _, _ -> result.confirm() }
                    .setNegativeButton("No") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
            override fun onJsPrompt(
                view: WebView, url: String, message: String,
                defaultValue: String?, result: JsPromptResult
            ): Boolean {
                val input = EditText(this@MainActivity)
                input.setText(defaultValue ?: "")
                AlertDialog.Builder(this@MainActivity)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("OK") { _, _ -> result.confirm(input.text.toString()) }
                    .setNegativeButton("Cancelar") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        web.loadUrl("file:///android_asset/index.html")
    }
}