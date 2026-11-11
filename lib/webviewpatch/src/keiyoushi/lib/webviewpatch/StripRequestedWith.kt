package keiyoushi.lib.webviewpatch

import keiyoushi.utils.WebViewScope
import keiyoushi.utils.applicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Stops WebView from sending the `X-Requested-With` header, which it adds to every request it doesn't hand
 * `interceptRequest` (all POSTs among them) and which some Cloudflare sites refuse. WebView exposes no way to
 * drop it, so this patches the per-request adder in the shared WebView library in the app's own copy-on-write
 * memory; the request still goes out on WebView's own stack, so the fingerprint stays native Chrome. The effect
 * is **process-wide** and lasts the app's lifetime (no WebView sends the header). Fail-safe: a build the patch
 * can't handle just keeps sending the header.
 *
 * Call it inside a `runWebView` `configure` block **before the first** `loadUrl`/`loadData`. The patch attempt
 * runs detached (see [patchScope]); this waits only briefly ([PATCH_WAIT_MS]) for it to settle, so a run isn't
 * blocked by the full locate + loopback self-test. Opt-in and idempotent; a no-op on a host the lib can't
 * support (a JVM extension runner, a `/proc`-less or non-arm platform).
 *
 * Needs `runWebView` as the host — add `implementation(project(":lib:webviewpatch"))` and call this from a source's
 * own WebView run.
 */
suspend fun WebViewScope<*>.stripRequestedWith() {
    try {
        // Unsupported host: settle silently — no completion hook, no retry hook, no store file, header stays.
        if (!XrwStripper.hostSupported()) return

        // Promote a freshly-written patch to OK only after a real run survives (not just the self-test). Register on
        // the run coroutine's own Job: its completion handler fires after runWebView's `finally` (WebView teardown)
        // returns, so reaching it means the process lived through the run. It also fires on cancellation/timeout, so a
        // run that was interrupted still promotes a patch the self-test had already confirmed. Null Job: skip (no host
        // coroutine to hang the bookkeeping off, so leave promotion to the next run).
        currentCoroutineContext()[Job]?.invokeOnCompletion {
            runCatching {
                if (XrwStripper.hasPendingSurvival()) {
                    patchScope.launch { XrwStripper.markRunSurvived(applicationContext) }
                }
            }
        }

        // Run the attempt detached from this run's Job (so a run timeout/cancel can't interrupt a write
        // mid-flight or strand the store mid-self-test), and wait only briefly for it. If it hasn't settled in
        // time the run proceeds with the header and the attempt finishes in the background.
        val attempt = patchScope.async { XrwStripper.patchProcess(applicationContext) }
        val outcome = withTimeoutOrNull(PATCH_WAIT_MS) { attempt.await() }
        if (outcome == XrwStripper.Outcome.NO_LIB) {
            // Cold process: the WebView library wasn't mapped yet. It maps during the first load, so retry once
            // on the next page start to cover the run's later requests (the first request is the existing floor).
            val retried = AtomicBoolean(false)
            onPageStarted {
                runCatching {
                    if (retried.compareAndSet(false, true)) {
                        patchScope.launch { XrwStripper.patchProcess(applicationContext) }
                    }
                }
            }
        }
    } catch (c: CancellationException) {
        throw c // cooperative cancellation (run timeout/teardown): propagate; the detached attempt lives on
    } catch (t: Throwable) {
        // Belt-and-braces: a stub/missing android class on an unsupported host must never break the source's run.
    }
}

// How long stripRequestedWith blocks the run's own coroutine waiting for the patch to settle. The cheap paths
// (cached-OK re-apply, a terminal/blocklisted state, fast-path adoption, NO_LIB) resolve well under a second;
// a fresh locate + ~5.5 s loopback self-test deliberately exceeds it, so the first run proceeds with the header
// and the patch — confirmed in the background — covers later runs.
private const val PATCH_WAIT_MS = 3_000L

// Hosts the patch attempt, detached from any single run's Job so a run timeout or cancellation can't interrupt a
// /proc/self/mem write mid-flight or strand the store mid-self-test. One per classloader (per extension copy).
private val patchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
