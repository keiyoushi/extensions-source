package keiyoushi.lib.webviewpatch

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import java.io.Closeable

/**
 * A mapped WebView `.so` the locator reads through, with its sha. [source] is the off-heap byte view and
 * [sha256] hashes the mapped span; [close] releases the backing file. The real implementation wraps
 * [NativeMemory.MappedSo]; a test supplies its own.
 */
internal interface MappedImage : Closeable {
    val source: ByteSource
    fun sha256(): String
}

/**
 * How the app's previous MAIN process ended, read from the OS exit-reason history since a marker time. The
 * survival state machine treats a native [CRASH] as the patch's fault, a [CLEAN] end (user swipe, signal,
 * low memory, ANR, exit-self, dependency-died, ...) as not ours, and [UNKNOWN] (no attributable main-process
 * record since the marker, or API < 30 where exit reasons are unavailable) as the conservative fallback.
 */
internal enum class ExitKind { CRASH, CLEAN, UNKNOWN }

/**
 * Every side-effecting native/OS dependency the patch orchestration touches, behind one seam so the engine
 * runs off-device against fakes. The real implementation ([AndroidNativeOps]) wraps the
 * [NativeMemory] / [XrwLocator] / [LoopbackProbe] / [ActivityManager] / system-clock calls directly.
 */
internal interface NativeOps {
    /** The mapped WebView library, or null (cold process / not mapped). Wraps [NativeMemory.findWebViewLib]. */
    fun findLib(): NativeMemory.MappedLib?

    /** Cheap path+size+mtime identity for [lib], no file read. Wraps [NativeMemory.cheapKey]. */
    fun cheapKey(lib: NativeMemory.MappedLib): String

    /** Maps the `.so` bytes the locator needs, or null on failure. Wraps [NativeMemory.mapSo]. */
    fun openLib(lib: NativeMemory.MappedLib): MappedImage?

    /** Structural locate of the per-request adder over [src]. Wraps [XrwLocator.locate]. */
    fun locate(src: ByteSource): LocateResult

    /** Reads [len] bytes of our own process memory at [va], or null. Wraps [NativeMemory.readMem]. */
    fun readMem(va: Long, len: Int): ByteArray?

    /** Copy-on-write self-patch at [va], true only on a verified read-back. Wraps [NativeMemory.writeMem]. */
    fun writeMem(va: Long, bytes: ByteArray): Boolean

    /**
     * Classifies this app's MAIN-process exit since [markerMs] as a native [ExitKind.CRASH], a
     * [ExitKind.CLEAN] end, or [ExitKind.UNKNOWN]. Wraps the ENTIRE body: the SDK>=R gate, the
     * [ActivityManager.getHistoricalProcessExitReasons] scan, and the main-process filter.
     */
    fun exitSince(markerMs: Long): ExitKind

    /** Whether the OS exposes exit reasons (API 30+); gates the OK-apply crash backstop. Wraps the `SDK >= R` check. */
    fun exitReasonsAvailable(): Boolean

    /** The loopback self-test verdict. Wraps [LoopbackProbe.probe]. */
    suspend fun selfTest(): LoopbackProbe.Result

    /** Wall-clock milliseconds. Wraps [System.currentTimeMillis]. */
    fun now(): Long
}

/**
 * The process-wide shared coordination the extension copies use to agree on one patch: the applied-record,
 * the in-flight flag, the pending-survival record and the settled-cheap-key set, plus the lock guarding
 * them (which also guards the file store). The real implementation ([ProcessSharedState]) keeps today's
 * cross-classloader coordination through String-valued [System.getProperties] entries; a test gets a fresh instance.
 */
internal interface SharedState {
    /** The process-wide coordination lock; also passed to the file store. */
    val lock: Any

    /** The applied-record's live-address hex (or null), for the fast-path read-back. */
    fun fastPathAddr(): String?

    /** The applied-record's patch hex (or null), for the fast-path read-back. */
    fun fastPathPatch(): String?

    /** Drops the applied-record (address/patch/sha), when its live bytes no longer match. */
    fun clearApplied()

    /** Records the applied patch: live address, patch bytes, sha. */
    fun markApplied(live: Long, patchHex: String, sha: String)

    /** Records the sha + ok-value awaiting a real run's survival. */
    fun setPending(sha: String, okValue: String)

    /** Takes and clears the pending-survival record, or null if none. */
    fun takePending(): Pair<String, String>?

    /** Whether a pending-survival record is set (cheap completion-hook check). */
    fun hasPending(): Boolean

    /** Whether a copy is mid-attempt. */
    fun inflight(): Boolean

    /** Sets/clears the in-flight flag. */
    fun setInflight(value: Boolean)

    /** Whether this process already settled the lib with [key]. */
    fun isSettled(key: String): Boolean

    /** Marks the lib with [key] settled for this process. */
    fun markSettled(key: String)
}

/** One of the app's recent MAIN-process exits, reduced to the two fields [classifyExitSince] needs. */
internal data class MainExit(val nativeCrash: Boolean, val timestampMs: Long)

/**
 * Classifies the app's exit since [markerMs] from its recent MAIN-process exits ([mainExits], any order).
 * CRASH if ANY main-process record at/after the marker is a native crash — the scan does NOT stop at the
 * newest record, so a later clean session can't hide an earlier native crash. CLEAN if a main-process exit
 * exists since the marker but none is a native crash. UNKNOWN if no main-process record falls since the marker.
 * Pure, so it is JVM-unit-testable without Android.
 */
internal fun classifyExitSince(mainExits: List<MainExit>, markerMs: Long): ExitKind {
    val since = mainExits.filter { it.timestampMs >= markerMs }
    return when {
        since.isEmpty() -> ExitKind.UNKNOWN
        since.any { it.nativeCrash } -> ExitKind.CRASH
        else -> ExitKind.CLEAN
    }
}

/** [NativeOps] over [NativeMemory] / [XrwLocator] / [LoopbackProbe] / [ActivityManager] / the system clock. */
internal class AndroidNativeOps(private val context: Context) : NativeOps {

    override fun findLib(): NativeMemory.MappedLib? = NativeMemory.findWebViewLib()

    override fun cheapKey(lib: NativeMemory.MappedLib): String = NativeMemory.cheapKey(lib)

    override fun openLib(lib: NativeMemory.MappedLib): MappedImage? = NativeMemory.mapSo(lib)?.let { so ->
        object : MappedImage {
            override val source: ByteSource get() = so
            override fun sha256(): String = so.sha256Hex()
            override fun close() = so.close()
        }
    }

    override fun locate(src: ByteSource): LocateResult = XrwLocator.locate(src)

    override fun readMem(va: Long, len: Int): ByteArray? = NativeMemory.readMem(va, len)

    override fun writeMem(va: Long, bytes: ByteArray): Boolean = NativeMemory.writeMem(va, bytes)

    /**
     * Classifies the app's MAIN-process exit since [markerMs]. The MAIN-process filter is load-bearing
     * (device-proven, OP6 API 30): WebView's renderer runs in an ISOLATED child and on a native crash the OS
     * logs the child's OWN non-crash exit ~120 ms AFTER the main process's `REASON_CRASH_NATIVE`, so we scan
     * several records, keep only the main process's, and classify ALL of them since the marker (not just the
     * newest, so a later clean session can't hide an earlier crash). API < 30 / unreadable -> UNKNOWN.
     */
    override fun exitSince(markerMs: Long): ExitKind {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ExitKind.UNKNOWN
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return ExitKind.UNKNOWN
            val pkg = context.packageName
            val mainExits = am.getHistoricalProcessExitReasons(pkg, 0, EXIT_REASON_SCAN)
                .filter { it.processName == pkg }
                .map { MainExit(it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE, it.timestamp) }
            classifyExitSince(mainExits, markerMs)
        } catch (t: Throwable) {
            Log.e(TAG, "exitReasons", t)
            ExitKind.UNKNOWN
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
    override fun exitReasonsAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override suspend fun selfTest(): LoopbackProbe.Result = LoopbackProbe.probe(context)

    override fun now(): Long = System.currentTimeMillis()

    companion object {
        private const val TAG = "XrwStripper"

        // How many historical exit records to scan for the main-process exit. WebView's isolated renderer logs
        // its own (non-crash) exit ~120 ms after the main process's native crash, so the newest record is often
        // the child's; a small window reliably contains the main-process record beneath it.
        private const val EXIT_REASON_SCAN = 10
    }
}

/**
 * The real process-wide [SharedState]. Every piece of coordination state is held as a STRING property in
 * [System.getProperties] under the stable, unversioned [PREFIX], so each extension copy (each in its own
 * classloader) rendezvous through the one process-global system-properties table and every lib version
 * shares one lock and one store file. Only String values land under our keys, so a `Properties.store`/`list`
 * anywhere in the app can't hit a non-String value and throw. The monitor is a process-global interned
 * string (the JVM string pool is shared across classloaders), not a stashed object.
 */
internal object ProcessSharedState : SharedState {

    private const val PREFIX = "keiyoushi.webviewpatch."
    private const val APPLIED_ADDR = PREFIX + "applied.addr"
    private const val APPLIED_PATCH = PREFIX + "applied.patch"
    private const val APPLIED_SHA = PREFIX + "applied.sha"
    private const val INFLIGHT = PREFIX + "inflight"
    private const val PENDING = PREFIX + "pending"
    private const val SETTLED_PREFIX = PREFIX + "settled."

    // The cross-classloader monitor: the same pooled String instance in every classloader (not a per-object
    // value stashed in the properties table, which would be a non-String value and contend with getProperty).
    override val lock: Any = "keiyoushi.webviewpatch.lock".intern()

    override fun fastPathAddr(): String? = System.getProperty(APPLIED_ADDR)

    override fun fastPathPatch(): String? = System.getProperty(APPLIED_PATCH)

    override fun clearApplied() = synchronized(lock) {
        System.clearProperty(APPLIED_ADDR)
        System.clearProperty(APPLIED_PATCH)
        System.clearProperty(APPLIED_SHA)
        Unit
    }

    override fun markApplied(live: Long, patchHex: String, sha: String) = synchronized(lock) {
        System.setProperty(APPLIED_ADDR, live.toString(16))
        System.setProperty(APPLIED_PATCH, patchHex)
        System.setProperty(APPLIED_SHA, sha)
        Unit
    }

    override fun setPending(sha: String, okValue: String) = synchronized(lock) {
        System.setProperty(PENDING, "$sha\u0000$okValue")
        Unit
    }

    override fun takePending(): Pair<String, String>? = synchronized(lock) {
        val v = System.getProperty(PENDING) ?: return null
        System.clearProperty(PENDING)
        val sep = v.indexOf('\u0000')
        if (sep < 0) null else v.substring(0, sep) to v.substring(sep + 1)
    }

    override fun hasPending(): Boolean = System.getProperty(PENDING) != null

    override fun inflight(): Boolean = System.getProperty(INFLIGHT) != null

    override fun setInflight(value: Boolean) {
        if (value) System.setProperty(INFLIGHT, "true") else System.clearProperty(INFLIGHT)
    }

    override fun isSettled(key: String): Boolean = System.getProperty(SETTLED_PREFIX + key) != null

    override fun markSettled(key: String) {
        System.setProperty(SETTLED_PREFIX + key, "1")
    }
}
