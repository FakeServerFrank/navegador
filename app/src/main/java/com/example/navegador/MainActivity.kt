package com.example.navegador

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var barra: EditText
    private lateinit var progreso: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val atras = Button(this).apply { text = "◀" }
        val adelante = Button(this).apply { text = "▶" }
        val recargar = Button(this).apply { text = "⟳" }
        barra = EditText(this).apply {
            hint = "Buscar o escribir URL"
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
        }
        progreso = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        web = WebView(this)

        fila.addView(atras)
        fila.addView(adelante)
        fila.addView(barra, LinearLayout.LayoutParams(0, -2, 1f))
        fila.addView(recargar)
        raiz.addView(fila)
        raiz.addView(progreso, LinearLayout.LayoutParams(-1, 8))
        raiz.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(raiz)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                barra.setText(url)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) {
                progreso.progress = p
                progreso.visibility = if (p < 100) View.VISIBLE else View.GONE
            }
        }

        atras.setOnClickListener { if (web.canGoBack()) web.goBack() }
        adelante.setOnClickListener { if (web.canGoForward()) web.goForward() }
        recargar.setOnClickListener { web.reload() }
        barra.setOnEditorActionListener { _, _, _ ->
            cargar(barra.text.toString()); true
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        web.loadUrl("https://www.google.com")
    }

    private fun cargar(texto: String) {
        val t = texto.trim()
        val destino = when {
            t.startsWith("http") -> t
            t.contains(".") && !t.contains(" ") -> "https://$t"
            else -> "https://www.google.com/search?q=" +
                java.net.URLEncoder.encode(t, "UTF-8")
        }
        web.loadUrl(destino)
    }
}