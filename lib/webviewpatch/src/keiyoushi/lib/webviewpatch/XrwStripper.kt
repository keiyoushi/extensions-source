package keiyoushi.lib.webviewpatch

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Drops WebView's `X-Requested-With` header by an **in-process patch** of the shared WebView library: the
 * per-request adder is located by [XrwLocator] and its entry overwritten with a return in this process's own
 * copy-on-write page of the library. The write only ever touches this process's own COW pages, and because the
 * library is shared by every WebView, the patch is PROCESS-WIDE — it outlives the call and no WebView of the app
 * sends the header for the rest of the app's lifetime. A build the locator can't claim is never touched: the
 * header stays (fail-safe).
 *
 * This is the thin Android entry: it exposes the public API, decides host support, and builds the real
 * seams — [AndroidNativeOps] over [NativeMemory]/[XrwLocator]/[LoopbackProbe]/the system clock, a [FileKvStore]
 * over the app's `filesDir`, and the process-wide [ProcessSharedState] singleton — then delegates the whole
 * state machine to [XrwStripEngine].
 */
internal object XrwStripper {

    /** Settled = nothing more to do this run (patched, blocked, unsupported, or adopted). NO_LIB = library not mapped yet. */
    enum class Outcome { SETTLED, NO_LIB }

    private const val TAG = "XrwStripper"
    private const val STORE_FILE = "keiyoushi-webview-xrw.properties"

    /**
     * Builds the engine over the real seams for [app]. The persistent store shares the process lock with the
     * coordination map (so [markRunSurvived]'s own thread can't interleave with another writer); the store
     * lives in the app's own `filesDir`, not per-extension prefs.
     */
    private fun engine(app: Context): XrwStripEngine = XrwStripEngine(
        AndroidNativeOps(app),
        FileKvStore(File(app.filesDir, STORE_FILE), ProcessSharedState.lock),
        ProcessSharedState,
    )

    /**
     * Locates and patches the per-request adder for the mapped WebView library, off the main thread. Returns
     * [Outcome.SETTLED] once there is nothing more to do this run, or [Outcome.NO_LIB] when the library isn't
     * mapped yet (cold process) so the caller can retry once the first load maps it. Never throws.
     */
    suspend fun patchProcess(context: Context): Outcome = withContext(Dispatchers.IO) {
        // Unsupported host (non-ART, /proc-less, non-arm): settle at once, before any android.* or store work.
        if (!hostSupported()) return@withContext Outcome.SETTLED
        val app = context.applicationContext
        try {
            engine(app).doPatch()
        } catch (c: CancellationException) {
            throw c // let a cancelled run propagate instead of recording a false SETTLED
        } catch (t: Throwable) {
            Log.e(TAG, "patch", t)
            Outcome.SETTLED
        }
    }

    /**
     * Promotes the pending sha to OK and clears its crash-count. The sha is pending only after the self-test's
     * in-process load stripped cleanly, and reaching here means a [keiyoushi.utils.runWebView] run has since
     * ended without the process dying — so OK records that the process survived (a cancelled or timed-out run
     * still promotes). A no-op unless this process applied a patch this run. Called off the main thread from the
     * run's finally path — never from the stripper.
     */
    fun markRunSurvived(context: Context) {
        try {
            engine(context.applicationContext).markRunSurvived()
        } catch (t: Throwable) {
            Log.e(TAG, "markRunSurvived", t)
        }
    }

    /** Cheap check for the run's completion hook to skip spawning a confirmation thread when no patch is awaiting survival. Never throws. */
    fun hasPendingSurvival(): Boolean = try {
        ProcessSharedState.hasPending()
    } catch (t: Throwable) {
        false
    }

    // Computed once per classloader: whether this host supports the in-process patch at all. On an unsupported
    // host (a JVM extension runner, a non-arm or /proc-less platform) the lib settles silently and touches
    // nothing. Read before any android.* class, so a stub/missing android API can't break the host's run.
    private val supportedHost: Boolean by lazy {
        hostSupported(System.getProperty("java.vm.name"), System.getProperty("os.arch"), File("/proc/self/maps").canRead())
    }

    /** True only on a host the patch supports (ART/Dalvik, readable `/proc/self/maps`, arm ABI). Never throws. */
    fun hostSupported(): Boolean = try {
        supportedHost
    } catch (t: Throwable) {
        false
    }
}
