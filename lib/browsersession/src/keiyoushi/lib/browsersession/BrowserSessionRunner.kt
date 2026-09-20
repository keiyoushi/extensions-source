package keiyoushi.lib.browsersession

import android.os.Handler
import android.os.Looper
import keiyoushi.utils.runWebView
import keiyoushi.utils.runWebViewBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Result of a challenge resolution attempt.
 */
sealed class SolveResult {
    object Success : SolveResult() {
        override fun toString(): String = "SolveResult.Success"
    }

    object AlreadySolved : SolveResult() {
        override fun toString(): String = "SolveResult.AlreadySolved"
    }

    class Failed(val error: Throwable) : SolveResult() {
        override fun toString(): String = "SolveResult.Failed(error=$error)"
        override fun equals(other: Any?): Boolean = other is Failed && other.error == error
        override fun hashCode(): Int = error.hashCode()
    }

    val isSuccess: Boolean get() = this is Success || this is AlreadySolved
}

/**
 * Headless WebView runner for resolving modern browser challenges (Cloudflare Turnstile, DDoS-Guard, etc.).
 *
 * Key features:
 * - Single-flight synchronization lock (`ReentrantLock`) with a cooldown window to prevent thundering-herd issues.
 * - Active polling (default 250ms) directly against [android.webkit.CookieManager] rather than relying solely
 *   on `onPageFinished`.
 * - Settle window delay (default 750ms) to allow challenge beacon POST requests to commit before teardown.
 * - Fail-fast detection on interactive challenge prompts (checkboxes, puzzles) to abort and prompt manual
 *   WebView opening rather than stalling for 30 seconds.
 */
class BrowserSessionRunner(
    private val timeout: Duration = DEFAULT_TIMEOUT,
    private val pollInterval: Duration = DEFAULT_POLL_INTERVAL,
    private val settleDelay: Duration = DEFAULT_SETTLE_DELAY,
    private val cooldownWindow: Duration = DEFAULT_COOLDOWN_WINDOW,
    private val clearanceCookieNames: Set<String> = DEFAULT_CLEARANCE_COOKIES,
) {

    private val lock = ReentrantLock()
    private val lastSolveTimes = ConcurrentHashMap<String, Long>()

    internal fun domainKey(url: HttpUrl): String = runCatching { url.topPrivateDomain() }.getOrNull()
        ?: CookieBridge.computeFallbackTopPrivateDomain(url.host)
        ?: url.host

    private fun executeSolveInternal(
        url: HttpUrl,
        userAgent: String?,
        runWebViewAction: ((keiyoushi.utils.WebViewScope<Unit>.() -> Unit) -> Unit),
    ): SolveResult {
        val domain = domainKey(url)
        val lastSolve = lastSolveTimes[domain] ?: 0L
        val elapsed = System.currentTimeMillis() - lastSolve
        val hasClearance = clearanceCookieNames.any { name ->
            CookieBridge.getCookieValue(url, name)?.isNotBlank() == true
        }
        if (elapsed in 0..cooldownWindow.inWholeMilliseconds && hasClearance) {
            return SolveResult.AlreadySolved
        }

        CookieBridge.clearCookies(url, clearanceCookieNames)

        return try {
            runWebViewAction {
                if (!userAgent.isNullOrBlank()) {
                    this.userAgent = userAgent
                }
                blockImages = true
                javaScriptEnabled = true
                domStorageEnabled = true

                val isSettling = AtomicBoolean(false)
                val mainHandler = Handler(Looper.getMainLooper())

                poll(pollInterval) {
                    if (isSettling.get()) return@poll

                    evaluateJs(CHECK_INTERACTIVE_JS) { result ->
                        if (isSettling.get()) return@evaluateJs
                        val json = result.trim('"', ' ', '\n', '\r')
                        if (json == "interactive") {
                            reject(InteractiveChallengeException())
                            return@evaluateJs
                        }
                    }

                    val cookies = CookieBridge.getCookies(url)
                    val solved = clearanceCookieNames.any { name ->
                        cookies.any { it.name == name && it.value.isNotBlank() }
                    }

                    if (solved && isSettling.compareAndSet(false, true)) {
                        mainHandler.postDelayed(
                            { resolve(Unit) },
                            settleDelay.inWholeMilliseconds,
                        )
                    }
                }

                loadUrl(url.toString())
            }

            val cookies = CookieBridge.getCookies(url)
            val hasClearanceAfter = clearanceCookieNames.any { name ->
                cookies.any { it.name == name && it.value.isNotBlank() }
            }

            if (hasClearanceAfter) {
                lastSolveTimes[domain] = System.currentTimeMillis()
                SolveResult.Success
            } else {
                SolveResult.Failed(IOException("Clearance cookies not found after challenge solve"))
            }
        } catch (e: InteractiveChallengeException) {
            SolveResult.Failed(e)
        } catch (t: Throwable) {
            SolveResult.Failed(t)
        }
    }

    /**
     * Resolves a browser challenge for [url] blocking the current background thread.
     *
     * @param url The challenge URL to load.
     * @param call The active OkHttp [Call], monitored for cancellation.
     * @param userAgent Optional User-Agent header string to spoof.
     * @return [SolveResult] indicating success, already-solved state, or failure.
     */
    fun solve(
        url: HttpUrl,
        call: Call,
        userAgent: String? = null,
    ): SolveResult = lock.withLock {
        executeSolveInternal(url, userAgent) { configure ->
            runWebViewBlocking<Unit>(call, timeout = timeout, configure = configure)
        }
    }

    /**
     * Resolves a browser challenge for [url] string blocking the current background thread.
     */
    fun solve(
        url: String,
        call: Call,
        userAgent: String? = null,
    ): SolveResult = solve(url.toHttpUrl(), call, userAgent)

    /**
     * Resolves a browser challenge for [url] in a suspending coroutine context.
     */
    suspend fun solveSuspending(
        url: HttpUrl,
        userAgent: String? = null,
    ): SolveResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        lock.withLock {
            executeSolveInternal(url, userAgent) { configure ->
                kotlinx.coroutines.runBlocking {
                    runWebView<Unit>(timeout = timeout, configure = configure)
                }
            }
        }
    }

    /**
     * Resolves a browser challenge for [url] string in a suspending coroutine context.
     */
    suspend fun solveSuspending(
        url: String,
        userAgent: String? = null,
    ): SolveResult = solveSuspending(url.toHttpUrl(), userAgent)

    companion object {
        /** Default maximum duration for challenge solving before timing out. */
        val DEFAULT_TIMEOUT: Duration = 30.seconds

        /** Default polling interval for checking clearance cookies in CookieManager. */
        val DEFAULT_POLL_INTERVAL: Duration = 250.milliseconds

        /** Default settle delay before destroying WebView to ensure beacon POST commits. */
        val DEFAULT_SETTLE_DELAY: Duration = 750.milliseconds

        /** Default cooldown window suppressing redundant solve attempts across subdomains. */
        val DEFAULT_COOLDOWN_WINDOW: Duration = 5.seconds

        /** Default clearance cookie names monitored during challenge resolution. */
        val DEFAULT_CLEARANCE_COOKIES: Set<String> = setOf("cf_clearance", "__cf_bm")

        /** Default shared [BrowserSessionRunner] instance. */
        val DEFAULT = BrowserSessionRunner()

        internal val CHECK_INTERACTIVE_JS = """
            (function() {
                function isVisible(el) {
                    if (!el) return false;
                    var style = window.getComputedStyle(el);
                    return style && style.display !== 'none' && style.visibility !== 'hidden' && style.opacity !== '0';
                }

                var iframes = document.querySelectorAll('iframe[src*="challenges.cloudflare.com"], iframe[src*="turnstile"]');
                for (var i = 0; i < iframes.length; i++) {
                    var src = iframes[i].getAttribute('src') || '';
                    if (src.indexOf('/interactive/') !== -1 || src.indexOf('mode=interactive') !== -1 || src.indexOf('/checkbox/') !== -1) {
                        return "interactive";
                    }
                }

                var cfCheckbox = document.querySelector('#challenge-stage input[type="checkbox"], #cf-stage input[type="checkbox"], .ctp-checkbox-container input[type="checkbox"]');
                if (isVisible(cfCheckbox)) return "interactive";

                var cfLabel = document.querySelector('#challenge-stage .ctp-checkbox-label, .ctp-checkbox-container .ctp-checkbox-label');
                if (isVisible(cfLabel)) return "interactive";

                var puzzle = document.querySelector('#cf-challenge-puzzle, canvas#sliderCanvas');
                if (isVisible(puzzle)) return "interactive";

                var captchas = document.querySelectorAll('.g-recaptcha, .h-captcha');
                for (var j = 0; j < captchas.length; j++) {
                    var c = captchas[j];
                    if (isVisible(c) && c.getAttribute('data-size') !== 'invisible') {
                        return "interactive";
                    }
                }

                var prompt = document.querySelector('#challenge-stage .challenge-prompt, #challenge-error-title');
                if (isVisible(prompt) && /verif|güvenlik|puzzle|checkbox|human/i.test(prompt.innerText || '')) {
                    return "interactive";
                }

                return "none";
            })()
        """.trimIndent()
    }
}
