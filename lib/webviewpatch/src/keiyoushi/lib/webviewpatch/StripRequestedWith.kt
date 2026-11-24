package keiyoushi.lib.webviewpatch

import android.content.Context
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.applicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import java.io.IOException

private const val REQUESTED_WITH = "requested-with"

/**
 * Stops WebView from sending the `X-Requested-With` header, which it adds to every request it doesn't hand
 * `interceptRequest` (all POSTs among them) and which some Cloudflare sites refuse. WebView exposes no switch
 * for it, so the Keiyoushi Runtime adjusts the per-request adder in the app's own copy-on-write copy of the
 * WebView library; the request still leaves on WebView's own stack, so the fingerprint stays native Chrome.
 * The effect is process-wide and lasts the app's run.
 *
 * Call it inside a `runWebView` `configure` block **before the first** `loadUrl`/`loadData`. It returns only
 * once the patch has applied and passed its self-test, so the run's very first request already goes out
 * without the header. Any outcome other than success throws before a page loads — the header is never let out
 * as a fallback.
 *
 * Add `implementation(project(":lib:webviewpatch"))`.
 * Requires the Keiyoushi Runtime extension to be installed; otherwise it throws the install prompt.
 */
suspend fun WebViewScope<*>.stripRequestedWith() {
    applyRequestedWith(applicationContext, currentCoroutineContext()[Job])
}

/**
 * Applies the requested-with patch outside a `runWebView` block — an OkHttp interceptor, a cookie fetch, any
 * path that opens its own WebView. Await it before issuing a single WebView request on [context]. Throws the
 * same way as [stripRequestedWith].
 */
suspend fun ensureRequestedWithStripped(context: Context) {
    applyRequestedWith(context, currentCoroutineContext()[Job])
}

private suspend fun applyRequestedWith(context: Context, runJob: Job?) {
    val status = RuntimeModules.webviewpatch.bind<WebViewPatches>().apply(REQUESTED_WITH, context, runJob)
    when {
        status == "applied" -> return
        status == "unknown" -> throw RuntimeModules.webviewpatch.needsUpdate(REQUESTED_WITH)
        status.startsWith("unsupported:") || status.startsWith("failed:") ->
            throw IOException("Unsupported device (${status.substringAfter(':')}).")
        else -> throw IOException("Unsupported device.")
    }
}
