package keiyoushi.utils.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import keiyoushi.utils.RenderProcessGoneException
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.webview.internal.WebViewGlueBridge
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

private const val MIN_HEIGHT_DP = 48
private const val MAX_HEIGHT_FRACTION = 0.7f

abstract class BaseWebViewDialogHelper : ActivityTrackingHelper() {

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    protected suspend fun runDialog(
        html: String,
        baseUrl: String = "about:blank",
        userAgent: String? = null,
        initiallyVisible: Boolean = true,
        timeout: Duration,
    ): String? = withContext(Dispatchers.Main) {
        val activity = topActivity().takeIf { it.usable() }
            ?: throw IllegalStateException("Activity unavailable for dialog")

        val mainHandler = Handler(Looper.getMainLooper())
        val deferred = CompletableDeferred<String?>()
        val webView = WebView(activity)

        var dialog: AlertDialog? = null
        var holder: FrameLayout? = null
        var dismissedByUs = false

        val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val theme = if (night) "dark" else "light"
        val bg = resolveColor(activity, "colorSurfaceContainerHigh")
            ?: resolveColor(activity, "colorSurfaceContainer")
            ?: resolveColor(activity, "colorSurface")
            ?: resolveFrameworkColor(activity, android.R.attr.colorBackground)
            ?: (if (night) 0xFF212121.toInt() else 0xFFFFFFFF.toInt())
        val density = activity.resources.displayMetrics.density
        val defaultRadius = 28f * density
        val appRadius = resolveDialogRadius(activity)
        val cornerRadius = appRadius ?: defaultRadius
        val radiusDp = (cornerRadius / density).toInt()
        val themeCss = themeCss(activity, night, bg, radiusDp)

        fun heightPx(h: Int): Int {
            val metrics = activity.resources.displayMetrics
            val maxPx = (metrics.heightPixels * MAX_HEIGHT_FRACTION).toInt()
            val minPx = (MIN_HEIGHT_DP * metrics.density).toInt()
            return (h * metrics.density).toInt().coerceIn(minPx, maxPx.coerceAtLeast(minPx))
        }

        fun showDialog(contentHeightDp: Int) {
            if (dialog != null || deferred.isCompleted || !activity.usable()) return

            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.setBackgroundColor(Color.TRANSPARENT)

            val frame = FrameLayout(activity).apply {
                addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, heightPx(contentHeightDp)))
            }
            holder = frame

            val created = AlertDialog.Builder(activity)
                .setView(frame)
                .setOnDismissListener {
                    holder?.removeView(webView)
                    holder = null
                    dialog = null
                    if (!dismissedByUs) {
                        deferred.complete(null)
                    }
                }
                .create()

            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(bg)
                setCornerRadius(cornerRadius)
            }
            val insetX = (24 * density).toInt()
            val insetY = (16 * density).toInt()
            created.window?.let { window ->
                window.setBackgroundDrawable(InsetDrawable(shape, insetX, insetY, insetX, insetY))
                window.decorView.findViewById<ViewGroup>(android.R.id.content)?.setBackgroundColor(Color.TRANSPARENT)
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
            dialog = created
            created.show()

            created.window?.clearFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            )
            webView.requestFocus()
        }

        fun hideDialog() {
            dismissedByUs = true
            dialog?.dismiss()
            dialog = null
            holder?.removeView(webView)
            holder = null
        }

        val bridge = object {
            @JavascriptInterface
            fun submit(value: String?) {
                mainHandler.post {
                    if (deferred.isCompleted) return@post
                    hideDialog()
                    deferred.complete(value)
                }
            }

            @JavascriptInterface
            fun cancel() {
                mainHandler.post {
                    if (deferred.isCompleted) return@post
                    hideDialog()
                    deferred.complete(null)
                }
            }

            @JavascriptInterface
            fun error(message: String?) {
                mainHandler.post {
                    if (deferred.isCompleted) return@post
                    hideDialog()
                    deferred.completeExceptionally(Exception(message ?: "unknown"))
                }
            }

            @JavascriptInterface
            fun show() {
                mainHandler.post {
                    showDialog(MIN_HEIGHT_DP)
                }
            }

            @JavascriptInterface
            fun hide() {
                mainHandler.post {
                    hideDialog()
                }
            }

            @JavascriptInterface
            fun resize(height: String?) {
                val px = height?.toIntOrNull() ?: return
                mainHandler.post {
                    if (deferred.isCompleted) return@post
                    if (dialog != null) {
                        webView.layoutParams = webView.layoutParams?.apply {
                            this.height = heightPx(px)
                        }
                    } else if (initiallyVisible) {
                        showDialog(px)
                    }
                }
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = false
            loadWithOverviewMode = false
            userAgent?.let {
                userAgentString = it
                WebViewGlueBridge.setClientHintsFromUserAgent(this, it)
            }
        }
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.addJavascriptInterface(bridge, "_dialogBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true

            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                deferred.completeExceptionally(RenderProcessGoneException(detail?.didCrash() == true))
                return true
            }
        }

        val metrics = activity.resources.displayMetrics
        webView.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        webView.loadDataWithBaseURL(baseUrl, wrapHtml(html, theme, themeCss), "text/html", "UTF-8", null)

        try {
            withTimeout(timeout) {
                deferred.await()
            }
        } catch (_: TimeoutCancellationException) {
            throw WebViewTimeoutException(timeout)
        } finally {
            mainHandler.removeCallbacksAndMessages(null)
            dismissedByUs = true
            dialog?.dismiss()
            dialog = null
            holder?.removeView(webView)
            holder = null
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }
    }
}

private fun resolveColor(activity: Activity, attrName: String): Int? {
    val id = activity.resources.getIdentifier(attrName, "attr", activity.packageName)
        .takeIf { it != 0 }
        ?: activity.resources.getIdentifier(attrName, "attr", "android")
    if (id == 0) return null
    val a = activity.obtainStyledAttributes(intArrayOf(id))
    return try {
        if (a.hasValue(0)) a.getColor(0, 0) else null
    } finally {
        a.recycle()
    }
}

private fun resolveFrameworkColor(activity: Activity, attrId: Int): Int? {
    val a = activity.obtainStyledAttributes(intArrayOf(attrId))
    return try {
        if (a.hasValue(0)) a.getColor(0, 0) else null
    } finally {
        a.recycle()
    }
}

private fun resolveDialogRadius(activity: Activity): Float? {
    val themeId = resolveAttrResId(activity, "materialAlertDialogTheme")
        ?: resolveAttrResId(activity, "alertDialogTheme")
    val attrId = activity.resources.getIdentifier("dialogCornerRadius", "attr", activity.packageName)
        .takeIf { it != 0 }
        ?: activity.resources.getIdentifier("dialogCornerRadius", "attr", "android")
    if (attrId == 0) return null

    val a = if (themeId != null) {
        activity.obtainStyledAttributes(themeId, intArrayOf(attrId))
    } else {
        activity.obtainStyledAttributes(intArrayOf(attrId))
    }
    return try {
        if (a.hasValue(0)) a.getDimension(0, 0f).takeIf { it > 0f } else null
    } finally {
        a.recycle()
    }
}

private fun resolveAttrResId(activity: Activity, attrName: String): Int? {
    val id = activity.resources.getIdentifier(attrName, "attr", activity.packageName)
        .takeIf { it != 0 }
        ?: activity.resources.getIdentifier(attrName, "attr", "android")
    if (id == 0) return null
    val a = activity.obtainStyledAttributes(intArrayOf(id))
    return try {
        if (a.hasValue(0)) a.getResourceId(0, 0).takeIf { it != 0 } else null
    } finally {
        a.recycle()
    }
}

private fun themeCss(activity: Activity, night: Boolean, bg: Int, radiusDp: Int): String {
    val accent = resolveColor(activity, "colorPrimary")
        ?: resolveColor(activity, "colorAccent")
        ?: resolveColor(activity, "colorSecondary")
        ?: resolveFrameworkColor(activity, android.R.attr.colorPrimary)
        ?: resolveFrameworkColor(activity, android.R.attr.colorAccent)
        ?: (if (night) 0xFF90CAF9.toInt() else 0xFF1976D2.toInt())

    val lum = Color.red(accent) * 0.299 + Color.green(accent) * 0.587 + Color.blue(accent) * 0.114
    val accentFg = resolveColor(activity, "colorOnPrimary")
        ?: (if (lum < 150) Color.WHITE else Color.BLACK)

    val fg = resolveColor(activity, "colorOnSurface")
        ?: resolveFrameworkColor(activity, android.R.attr.textColorPrimary)
        ?: (if (night) Color.WHITE else Color.BLACK)

    val muted = resolveColor(activity, "colorOnSurfaceVariant")
        ?: resolveColor(activity, "colorOutline")
        ?: resolveFrameworkColor(activity, android.R.attr.textColorSecondary)
        ?: (if (night) 0xFFB0B0B0.toInt() else 0xFF606060.toInt())

    fun hex(color: Int) = String.format("#%06X", 0xFFFFFF and color)

    return buildString {
        append("--bg: ").append(hex(bg)).append("; ")
        append("--surface: ").append(hex(bg)).append("; ")
        append("--fg: ").append(hex(fg)).append("; ")
        append("--muted: ").append(hex(muted)).append("; ")
        append("--accent: ").append(hex(accent)).append("; ")
        append("--accent-fg: ").append(hex(accentFg)).append("; ")
        append("--radius: ").append(radiusDp).append("px; ")
        append("color-scheme: ").append(if (night) "dark" else "light").append(";")
    }
}

private fun wrapHtml(body: String, theme: String, themeCss: String): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            :root {
                $themeCss
                accent-color: var(--accent);
            }
            html, body {
                margin: 0;
                background: transparent;
                color: var(--fg);
                font-family: sans-serif;
            }
            #root {
                padding: 16px;
                box-sizing: border-box;
            }
            input, select, textarea {
                font: inherit;
                color: var(--fg);
                background: rgba(128, 128, 128, 0.08);
                border: 1px solid var(--muted);
                border-radius: 8px;
                padding: 8px 12px;
                margin: 4px 0;
                box-sizing: border-box;
                max-width: 100%;
            }
            input[type="text"], input[type="password"], input:not([type]), textarea {
                width: 100%;
            }
            input:focus, select:focus, textarea:focus {
                outline: none;
                border-color: var(--accent);
                box-shadow: 0 0 0 1px var(--accent);
            }
            button {
                cursor: pointer;
                font: inherit;
                font-weight: 500;
                color: var(--fg);
                background: transparent;
                border: 1px solid var(--muted);
                border-radius: var(--radius);
                padding: 8px 16px;
                margin: 4px 0;
                box-sizing: border-box;
            }
            button:active:not(:disabled) {
                background: rgba(128, 128, 128, 0.2);
            }
            button.primary, button[type="submit"] {
                background: var(--accent);
                color: var(--accent-fg);
                border-color: var(--accent);
                border-radius: var(--radius);
            }
            button.primary:active:not(:disabled), button[type="submit"]:active:not(:disabled) {
                filter: brightness(0.9);
            }
            button:disabled {
                opacity: 0.5;
                cursor: not-allowed;
            }
            button.icon-btn, button.icon-btn:active {
                background: transparent !important;
                border: none !important;
                border-radius: 0 !important;
                padding: 0 !important;
                margin: 0 !important;
            }
            label:active {
                background: rgba(128, 128, 128, 0.15);
            }
            a {
                color: var(--accent);
            }
        </style>
        <script>
            window.Dialog = {
                submit: function (value) {
                    var serialized = value === undefined ? '' : (typeof value === 'string' ? value : JSON.stringify(value));
                    window._dialogBridge.submit(serialized);
                },
                cancel: function () {
                    window._dialogBridge.cancel();
                },
                error: function (message) {
                    window._dialogBridge.error(String(message || ''));
                },
                show: function () {
                    window._dialogBridge.show();
                },
                hide: function () {
                    window._dialogBridge.hide();
                },
                theme: '$theme'
            };
        </script>
    </head>
    <body>
        <div id="root">$body</div>
        <script>
            document.addEventListener('click', function (e) {
                var anchor = e.target.closest && e.target.closest('a');
                if (anchor) {
                    e.preventDefault();
                }
            }, true);

            var root = document.getElementById('root');
            var lastHeight = 0;
            function reportHeight() {
                var height = Math.ceil(root.getBoundingClientRect().height);
                if (height > 0 && height !== lastHeight) {
                    lastHeight = height;
                    window._dialogBridge.resize(String(height));
                }
            }
            new ResizeObserver(reportHeight).observe(root);
            reportHeight();
        </script>
    </body>
    </html>
""".trimIndent()
