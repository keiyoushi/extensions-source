package eu.kanade.tachiyomi.extension.pt.sakuramangas

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.WebView
import keiyoushi.utils.ui.topActivity
import keiyoushi.utils.ui.usable

/**
 * Shows a WebView full screen over the foreground activity while the Cloudflare check or the site's captcha needs
 * the user, who solves it there. Call from the main thread, e.g. inside `withNativeWebView`.
 */
internal class ChallengeOverlay(private val onClosed: () -> Unit) {
    private var dialog: Dialog? = null
    private var webView: WebView? = null
    private var callbacks: Application.ActivityLifecycleCallbacks? = null

    val isShowing get() = dialog != null

    fun show(view: WebView) {
        if (dialog != null) return
        val activity = runCatching { topActivity() }.getOrNull()?.takeIf { it.usable() } ?: return onClosed()
        try {
            (view.parent as? ViewGroup)?.removeView(view)
            dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar).apply {
                window?.apply {
                    setLayout(MATCH_PARENT, MATCH_PARENT)
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    setDimAmount(0.6f)
                }
                setContentView(view)
                setOnCancelListener { onClosed() }
                show()
            }
            webView = view
            callbacks = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityDestroyed(a: Activity) {
                    if (a === activity) {
                        dismiss()
                        onClosed()
                    }
                }
                override fun onActivityCreated(a: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(a: Activity) = Unit
                override fun onActivityResumed(a: Activity) = Unit
                override fun onActivityPaused(a: Activity) = Unit
                override fun onActivityStopped(a: Activity) = Unit
                override fun onActivitySaveInstanceState(a: Activity, outState: Bundle) = Unit
            }.also { activity.application.registerActivityLifecycleCallbacks(it) }
        } catch (_: Throwable) {
            onClosed()
        }
    }

    fun dismiss() {
        val shown = dialog ?: return
        dialog = null
        callbacks?.let { runCatching { (shown.context.applicationContext as Application).unregisterActivityLifecycleCallbacks(it) } }
        callbacks = null
        shown.setOnCancelListener(null)
        runCatching { if (shown.isShowing) shown.dismiss() }
        webView?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        webView = null
    }
}
