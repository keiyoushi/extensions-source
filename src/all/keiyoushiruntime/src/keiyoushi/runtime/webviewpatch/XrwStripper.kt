package keiyoushi.runtime.webviewpatch

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
 * attempt reports failed or unsupported.
 *
 * This is the thin Android entry: it exposes the public API, decides host support, and builds the real
 * seams — [AndroidNativeOps] over [NativeMemory]/[XrwLocator]/[LoopbackProbe]/the system clock, a [FileKvStore]
 * over the app's `filesDir`, and the process-wide [RuntimeSharedState] singleton — then delegates the whole
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
        FileKvStore(File(app.filesDir, STORE_FILE), RuntimeSharedState.lock),
        RuntimeSharedState,
    )

    /**
     * Locates and patches the per-request adder for the mapped WebView library, off the main thread. Returns
     * [Outcome.SETTLED] once there is nothing more to do this run, or [Outcome.NO_LIB] when the library isn't
     * mapped yet (cold process), which the caller reports as a failure. Never throws.
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
     * in-process load stripped cleanly, and reaching here means the process survived from the patch application
     * to now with the [keiyoushi.utils.runWebView] run that hosted the attempt already ended — OK records that
     * survival, not that a later load exercised the patch (a cancelled or timed-out run still promotes). A no-op
     * unless this process applied a patch this run. Called off the main thread, either from that run's completion
     * hook or from the attempt itself once no run is left to host the hook.
     */
    fun markRunSurvived(context: Context) {
        try {
            engine(context.applicationContext).markRunSurvived()
        } catch (t: Throwable) {
            Log.e(TAG, "markRunSurvived", t)
        }
    }

    /** Whether the patch is live in this process. Never throws. */
    fun stripped(): Boolean = try {
        hostSupported() && RuntimeSharedState.appliedLive(NativeMemory::readMem)
    } catch (t: Throwable) {
        false
    }

    /** Cheap check for the run's completion hook to skip spawning a confirmation thread when no patch is awaiting survival. Never throws. */
    fun hasPendingSurvival(): Boolean = try {
        RuntimeSharedState.hasPending()
    } catch (t: Throwable) {
        false
    }

    /** The most recent terminal status of a patch run, for the lib to map to the caller's error. Never throws. */
    fun lastStatus(): String? = try {
        RuntimeSharedState.lastStatus()
    } catch (t: Throwable) {
        null
    }

    // Computed once per classloader: whether this host supports the in-process patch at all. On an unsupported
    // host (a JVM extension runner, a non-arm or /proc-less platform) the lib settles silently and touches
    // nothing. Read before any android.* class, so a stub/missing android API can't break the host's run.
    private val supportedHostReason: String? by lazy {
        hostUnsupportedReason(System.getProperty("java.vm.name"), System.getProperty("os.arch"), File("/proc/self/maps").canRead())
    }

    /** True only on a host the patch supports (ART/Dalvik, readable `/proc/self/maps`, arm ABI). Never throws. */
    fun hostSupported(): Boolean = hostUnsupportedReason() == null

    /**
     * The user-readable reason this host can't run the patch (the lib shows it as the error detail), or null when
     * supported. Reads only process-wide system properties, so it's computed once per classloader. Never throws;
     * a failure to read the signals reports a generic reason rather than claiming support.
     */
    fun hostUnsupportedReason(): String? = try {
        supportedHostReason
    } catch (t: Throwable) {
        "this device can't run the patch"
    }
}
