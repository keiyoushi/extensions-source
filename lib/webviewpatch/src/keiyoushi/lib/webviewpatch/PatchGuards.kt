package keiyoushi.lib.webviewpatch

import java.io.File
import java.util.Properties

/**
 * Minimal persistent key-value seam, satisfied by the stripper's `filesDir` store and by an in-memory
 * fake in tests. It lets the give-up guard below stay pure logic (no Android, no I/O of its own), so it
 * is fully JVM-unit-testable.
 */
internal interface KvStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)

    /**
     * Atomic read-modify-write across keys, under one lock and one durable write: [block] reads current values with
     * [Txn.get] and stages changes with [Txn.set], all committed together. Keeps a read-then-write (e.g. the give-up
     * distinct-sha set) from interleaving with another writer.
     */
    fun transact(block: Txn.() -> Unit)

    interface Txn {
        fun get(key: String): String?
        fun set(key: String, value: String)
        fun remove(key: String)
    }
}

/**
 * Crash-safe [KvStore] backed by a `.properties` file. Pure `java.io`, so it is JVM-unit-testable
 * without Android.
 *
 * Durability: every state change is written to a sibling temp file (flushed + fsync'd) and then renamed
 * over the target. `rename(2)` is atomic within a filesystem, so a crash mid-write leaves either the old
 * file or the new one whole — never the half-written/empty file an in-place truncate+rewrite could leave,
 * which would drop the blocklist, crash-counts and give-up flag. A truncated or corrupt file from an older
 * build is tolerated on load (start empty rather than throw). All reads and writes take [lock] (the
 * process lock), so a write from [XrwStripper.markRunSurvived]'s own thread can't interleave with another.
 */
internal class FileKvStore(private val file: File, private val lock: Any) : KvStore {

    private fun load(): Properties = Properties().apply {
        try {
            if (file.exists()) file.inputStream().use { load(it) }
        } catch (_: Throwable) {
            clear() // a truncated/corrupt file is tolerated: start empty rather than crash the run
        }
    }

    private fun save(p: Properties) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { out ->
            p.store(out, null)
            out.flush()
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            // Rename failed (rare): fail safe. Never write in place — a torn in-place truncate+rewrite is the
            // exact corruption the temp+rename avoids. Drop the temp and leave the existing file whole; a
            // dropped update is fine (the state machine re-derives), a half-written file is not.
            tmp.delete()
        }
    }

    /** Load, apply [block], and save once — one atomic file write per state change, under the lock. */
    fun edit(block: Properties.() -> Unit) = synchronized(lock) { save(load().apply(block)) }

    /** Like [edit] but returns a value computed from the mutated properties (e.g. a bumped count). */
    fun <R> editAndGet(block: Properties.() -> R): R = synchronized(lock) {
        val p = load()
        val r = p.block()
        save(p)
        r
    }

    override fun get(key: String): String? = synchronized(lock) { load().getProperty(key) }

    override fun put(key: String, value: String) = edit { setProperty(key, value) }

    override fun remove(key: String) = edit { remove(key) }

    override fun transact(block: KvStore.Txn.() -> Unit) = edit {
        val props = this
        val txn = object : KvStore.Txn {
            override fun get(key: String): String? = props.getProperty(key)
            override fun set(key: String, value: String) {
                props.setProperty(key, value)
            }
            override fun remove(key: String) {
                props.remove(key)
            }
        }
        txn.block()
    }
}

/**
 * Locator/lib version, stamped onto the negative cache states (`nomatch`/`inconclusive`/`blocklisted`)
 * and the per-device give-up. A copy with a newer version retries an entry an older version wrote, so a
 * locator fix reaches users who already recorded a no-match without waiting for their next WebView update.
 * Bump this whenever the locator changes in a way that could claim a build it used to miss.
 */
internal const val LOCATOR_VERSION = 1

/** Stamps a cache state with the version that wrote it: `"<state>@<ver>"`. */
internal fun stampState(state: String, version: Int = LOCATOR_VERSION): String = "$state@$version"

/** The version stamped on a stored value (0 for an unstamped/legacy or `ok:` value, which carries no `@`). */
internal fun stateStamp(raw: String): Int = raw.substringAfterLast('@', "").toIntOrNull() ?: 0

/** The bare state name without any version stamp. */
internal fun stateName(raw: String): String = if ('@' in raw) raw.substringBeforeLast('@') else raw

/** A stored negative state ([stateName] one of nomatch/inconclusive/blocklisted) is stale when a newer version may fix it. */
internal fun negativeStateStale(raw: String, current: Int = LOCATOR_VERSION): Boolean = stateStamp(raw) < current

/** What a launch does for an `ok:` build it already confirmed, given how the previous apply's session ended. */
internal enum class OkBackstop { RESET, COUNT, DEMOTE, LEAVE }

/**
 * Pure crash-backstop decision for a confirmed-OK build (fix: a build that only starts crashing after it was
 * OK'd had no stop). Only a native [ExitKind.CRASH] counts toward the demote; [threshold] consecutive such
 * launches demote the build to blocklisted. A [ExitKind.CLEAN] launch resets the counter; an
 * [ExitKind.UNKNOWN] launch leaves it untouched (nothing attributable). Unit-testable without Android.
 */
internal fun okBackstop(exit: ExitKind, newCount: Int, threshold: Int): OkBackstop = when (exit) {
    ExitKind.CLEAN -> OkBackstop.RESET
    ExitKind.UNKNOWN -> OkBackstop.LEAVE
    ExitKind.CRASH -> if (newCount >= threshold) OkBackstop.DEMOTE else OkBackstop.COUNT
}

/**
 * Whether this host is one the in-process patch supports, from signals that touch no `android.*` class: the
 * runtime must be ART/Dalvik (not a desktop JVM running extensions, e.g. Suwayomi/KCEF), `/proc/self/maps`
 * must be readable, and the ABI must be arm. A false here settles the lib silently (header stays), so the
 * opt-in patch never breaks a source on a host it doesn't support.
 */
internal fun hostSupported(vmName: String?, osArch: String?, mapsReadable: Boolean): Boolean {
    if (vmName?.contains("Dalvik", ignoreCase = true) != true) return false
    if (!mapsReadable) return false
    val arch = osArch?.lowercase().orEmpty()
    return arch.contains("aarch64") || arch.contains("arm")
}

/**
 * What to do when a launch finds a non-terminal survival marker (`attempting`/`selftest`/`unconfirmed`):
 * a prior launch wrote the patch but no real run confirmed it survived.
 */
internal enum class RecoverAction {
    /** The patch is still live in THIS process — a prior run was cancelled, not a crash. Adopt it, don't count. */
    ADOPT,

    /** The OS recorded a native crash of the main process after the marker: a real crasher, blocklist on the first strike. */
    NATIVE_BLOCKLIST,

    /** No live patch, no attributable exit, but the crash-count reached the threshold: blocklist. */
    COUNT_BLOCKLIST,

    /** An attributable CLEAN exit (user swipe, signal, ...): re-attempt WITHOUT counting a strike. */
    RETRY_CLEAN,

    /** Unattributable (API < 30 / no record): re-attempt and count a strike (today's conservative fallback). */
    RETRY,
}

/**
 * Pure decision for [RecoverAction], so the ordering (a cancellation is not a crash; a clean kill is never
 * counted) is unit-testable without Android. [patchLive] wins first: if our patch bytes are still at the site
 * this process never died, so the marker is a cancelled run. A native [ExitKind.CRASH] blocklists on the first
 * strike; a [ExitKind.CLEAN] exit re-attempts without counting (so a swipe-away can't push a good build toward
 * the blocklist); an [ExitKind.UNKNOWN] exit falls to the counted path (blocklist at the threshold, else retry).
 */
internal fun recoverAction(patchLive: Boolean, exit: ExitKind, newCrashCount: Int, threshold: Int): RecoverAction = when {
    patchLive -> RecoverAction.ADOPT
    exit == ExitKind.CRASH -> RecoverAction.NATIVE_BLOCKLIST
    exit == ExitKind.CLEAN -> RecoverAction.RETRY_CLEAN
    newCrashCount >= threshold -> RecoverAction.COUNT_BLOCKLIST
    else -> RecoverAction.RETRY
}

/**
 * Resolves the lib's sha from its cheap identity key without re-reading the ~150 MB `.so` when it's already
 * known. On a cache hit returns the stored sha and never calls [readAndHash]; on a miss calls [readAndHash]
 * (which reads + hashes the file) exactly once and records the mapping. Returns null if the read failed.
 */
internal inline fun resolveSha(store: KvStore, libKey: String, readAndHash: () -> String?): String? {
    store.get(libKey)?.let { return it }
    val sha = readAndHash() ?: return null
    store.put(libKey, sha)
    return sha
}

/**
 * Local, automatic, offline per-device give-up.
 *
 * Every time a lib-sha is blocklisted on this device (a native crasher caught by the write-ahead/exit-reason
 * path, or a self-test that reverted), its sha is added to a persisted DISTINCT set. One bad build is just a
 * per-sha blocklist; three DIFFERENT builds each dying here is the environment — device anti-tamper (Samsung
 * KNOX, a China-OEM security layer) or an aggressive battery-killer — not a bad build. At that point the
 * `/proc/self/mem` patch is switched off on this device for good and the header stays (fail-safe).
 */
internal object GiveUp {
    /** Distinct blocklisted shas needed to give up. Three different builds dying = hostile environment. */
    const val THRESHOLD = 3
    const val BLOCKLISTED_SHAS = "giveup.blocklistedShas"
    const val DISABLED = "patchDisabledOnDevice"
    private const val SEP = ","

    /** Disabled only when the flag was written by this version or newer; a newer lib ignores an older give-up (retries). */
    fun isDisabled(store: KvStore, version: Int = LOCATOR_VERSION): Boolean {
        val raw = store.get(DISABLED) ?: return false
        return stateName(raw) == "true" && stateStamp(raw) >= version
    }

    /**
     * Records [sha] in the distinct-blocklisted set; once [THRESHOLD] DISTINCT shas have been blocklisted,
     * trips the persistent device give-up, stamped with [version]. Re-blocklisting a sha already in the set
     * never grows the set, so a single bad build can't trip the guard by itself; but a blocklist while the set
     * is already at threshold re-stamps the give-up to [version], so a newer lib that retried old blocklists
     * re-confirms the give-up under its own version instead of leaving it stale.
     */
    fun recordBlocklisted(store: KvStore, sha: String, version: Int = LOCATOR_VERSION) = store.transact {
        val current = parse(get(BLOCKLISTED_SHAS))
        val updated = if (sha in current) current else current + sha
        if (updated.size != current.size) set(BLOCKLISTED_SHAS, updated.joinToString(SEP))
        if (updated.size >= THRESHOLD) set(DISABLED, stampState("true", version))
    }

    private fun parse(raw: String?): Set<String> = raw?.split(SEP)?.filterTo(LinkedHashSet()) { it.isNotEmpty() } ?: emptySet()
}
