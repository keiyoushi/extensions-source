package keiyoushi.runtime.webviewpatch

import keiyoushi.runtime.webviewpatch.LoopbackProbe.Result.INCONCLUSIVE
import keiyoushi.runtime.webviewpatch.LoopbackProbe.Result.PRESENT
import keiyoushi.runtime.webviewpatch.LoopbackProbe.Result.STRIPPED
import keiyoushi.runtime.webviewpatch.XrwStripper.Outcome.NO_LIB
import keiyoushi.runtime.webviewpatch.XrwStripper.Outcome.SETTLED
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Characterization tests for the [XrwStripEngine] state machine, driven off-device through fakes for its three
 * seams ([NativeOps], [KvStore], [SharedState]). Each test pins the persisted store state, [XrwStripper.Outcome],
 * memory writes and shared-coordination effects of one path.
 *
 * Store keys and state values (`sha.`, `attempting@1`, the `ok:` record, ...) are spelled out rather than
 * referenced from constants: they are persisted on real devices, so these literals guard reading an existing store.
 */
class XrwStripEngineTest {

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    /** In-memory [KvStore] that is its own [KvStore.Txn]; logs transactional sets to pin write-ahead ordering. */
    private class MapStore(val map: MutableMap<String, String>, val log: MutableList<String>) : KvStore, KvStore.Txn {
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }
        override fun set(key: String, value: String) {
            map[key] = value
            log += "set $key=$value"
        }
        override fun remove(key: String) {
            map.remove(key)
        }
        override fun transact(block: KvStore.Txn.() -> Unit) = block()
    }

    private class FakeNativeOps(private val log: MutableList<String>) : NativeOps {
        val mem = HashMap<Long, Byte>()
        val writes = mutableListOf<Pair<Long, String>>()
        var writeAllowed = true
        var lib: NativeMemory.MappedLib? = NativeMemory.MappedLib(0x400000L, "/lib.so", 0L, "arm64")
        var locateResult: LocateResult = LocateResult.emit(0x1000L, VERIFY, PATCH, 0x1000L)
        var selfTestResult = STRIPPED
        var exitKind = ExitKind.UNKNOWN
        var exitReasons = true
        var findLibCalls = 0
        var openLibCalls = 0
        var selfTestCalls = 0

        fun seed(addr: Long, h: String) = h.chunked(2).forEachIndexed { i, b -> mem[addr + i] = b.toInt(16).toByte() }

        override fun findLib(): NativeMemory.MappedLib? = lib.also { findLibCalls++ }
        override fun cheapKey(lib: NativeMemory.MappedLib): String = "ck"
        override fun openLib(lib: NativeMemory.MappedLib): MappedImage {
            openLibCalls++
            return object : MappedImage {
                override val source: ByteSource = ArrayByteSource(ByteArray(0))
                override fun sha256(): String = "S1"
                override fun close() = Unit
            }
        }
        override fun locate(src: ByteSource): LocateResult = locateResult.also { log += "locate" }
        override fun readMem(va: Long, len: Int): ByteArray? = ByteArray(len) { mem[va + it] ?: return null }
        override fun writeMem(va: Long, bytes: ByteArray): Boolean {
            val h = bytes.joinToString("") { "%02x".format(it) }
            writes += va to h
            log += "write@${va.toString(16)}:$h"
            if (writeAllowed) seed(va, h)
            return writeAllowed
        }
        override fun exitSince(markerMs: Long): ExitKind = exitKind
        override fun exitReasonsAvailable(): Boolean = exitReasons
        override suspend fun selfTest(): LoopbackProbe.Result = selfTestResult.also { selfTestCalls++ }
        override fun now(): Long = 1000L
    }

    private class FakeSharedState(private val log: MutableList<String>) : SharedState {
        override val lock = Any()
        private var addr: String? = null
        private var patch: String? = null
        private var pending: Pair<String, String>? = null
        private val settledKeys = HashMap<String, Int>()
        private var status: String? = null

        override fun setLastStatus(status: String) {
            this.status = status
        }
        override fun lastStatus(): String? = status
        override fun fastPathAddr(): String? = addr
        override fun fastPathPatch(): String? = patch
        override fun clearApplied() {
            primeApplied(null, null)
            log += "clearApplied"
        }
        override fun markApplied(live: Long, patchHex: String) = primeApplied(live.toString(16), patchHex)
        override fun setPending(sha: String, okValue: String) {
            pending = sha to okValue
        }
        override fun takePending(): Pair<String, String>? = pending.also { pending = null }
        override fun hasPending(): Boolean = pending != null
        override fun isSettled(key: String, version: Int): Boolean = (settledKeys[key] ?: 0) >= version
        override fun markSettled(key: String, version: Int) {
            settledKeys[key] = version
        }
        fun primeApplied(addrHex: String?, patchHex: String?) {
            addr = addrHex
            patch = patchHex
        }
    }

    /** One launch: fresh fakes over a store pre-filled with [state]. */
    private class Fixture(vararg state: Pair<String, String>) {
        val log = mutableListOf<String>()
        val ops = FakeNativeOps(log)
        val map = mutableMapOf(*state)
        val store = MapStore(map, log)
        val shared = FakeSharedState(log)
        fun engine(version: Int = LOCATOR_VERSION) = XrwStripEngine(ops, store, shared, version)
        fun patch(version: Int = LOCATOR_VERSION): XrwStripper.Outcome = runBlocking { engine(version).doPatch() }
    }

    // ---- cross-release decision version (one copy; a newer runtime release re-decides old failures) ----

    @Test
    fun `a negative state stamped by an older version is re-attempted`() {
        val f = Fixture("sha.S1" to "nomatch@0") // a locator fix must reach builds an older version gave up on
        f.ops.seed(LIVE, VERIFY)
        f.patch()
        assertEquals("unconfirmed@1", f.map["sha.S1"])
    }

    @Test
    fun `a cached terminal, newer or unreadable state does no work`() {
        // Current-version negatives are honoured. Anything this runtime can't read (a malformed or new-prefix OK,
        // a newer release's intermediate marker, an unknown value) is left untouched: an older runtime stays fail-safe.
        val states = listOf(
            "blocklisted@1",
            "nomatch@1",
            "inconclusive@1",
            "ok@1:1000:2000",
            "ok2@2:1000:1000:$VERIFY:$PATCH",
            "attempting@2",
            "unconfirmed@2",
            "something-a-newer-lib-wrote",
        )
        for (state in states) {
            val f = Fixture("sha.S1" to state)
            f.ops.seed(LIVE, VERIFY)
            assertEquals(state, SETTLED, f.patch())
            assertEquals(state, f.map["sha.S1"])
            assertTrue(state, f.ops.writes.isEmpty())
            assertFalse(state, f.log.contains("locate"))
        }
    }

    // ---- fresh attempt ----

    @Test
    fun `fresh success writes the patch, stores unconfirmed and sets pending`() {
        val f = Fixture()
        f.ops.seed(LIVE, VERIFY) // verify window == patch site (RET era)

        assertEquals(SETTLED, f.patch())
        assertEquals("unconfirmed@1", f.map["sha.S1"])
        assertEquals("write-ahead marker time, kept through UNCONFIRMED", "1000", f.map["mtime.S1"])
        assertEquals(PATCH, hex(f.ops.readMem(LIVE, 4)!!))
        assertTrue("pending must be set for a later run to promote", f.shared.hasPending())
        val iMarker = f.log.indexOf("set sha.S1=attempting@1")
        assertTrue("attempt marker before mem write", iMarker in 0 until f.log.indexOf("write@401000:$PATCH"))
    }

    @Test
    fun `a failed self-test or refused write reverts the patch site`() {
        // PRESENT and a refused write are deterministic for this build: blocklist at once, with no crash-count
        // detour, and feed the per-device give-up. INCONCLUSIVE is environmental: back off without a strike.
        // The revert writes the saved 4-byte patch window, unlike cachedApply's 16-byte verify window.
        data class Case(val selfTest: LoopbackProbe.Result, val writeAllowed: Boolean, val state: String, val giveUp: String?)
        val cases = listOf(
            Case(PRESENT, true, "blocklisted@1", "S1"),
            Case(STRIPPED, false, "blocklisted@1", "S1"),
            Case(INCONCLUSIVE, true, "inconclusive@1", null),
        )
        for (c in cases) {
            val f = Fixture()
            f.ops.seed(LIVE, VERIFY)
            f.ops.selfTestResult = c.selfTest
            f.ops.writeAllowed = c.writeAllowed

            f.patch()

            val msg = c.toString()
            assertEquals(msg, c.state, f.map["sha.S1"])
            assertNull(msg, f.map["mtime.S1"])
            assertNull(msg, f.map["crash.S1"])
            assertEquals(msg, c.giveUp, f.map["giveup.blocklistedShas"])
            assertEquals(msg, listOf(LIVE to PATCH, LIVE to OTHER4), f.ops.writes)
            assertEquals(msg, VERIFY, hex(f.ops.readMem(LIVE, 16)!!))
        }
    }

    @Test
    fun `no adder or a verify-window mismatch stores a version-stamped nomatch and never writes`() {
        val setups = listOf<FakeNativeOps.() -> Unit>(
            { locateResult = LocateResult.noMatch("no strategy matched") },
            {
                locateResult = LocateResult.emit(0x1000L, VERIFY, PATCH, 0x2000L)
                seed(LIVE, OTHER4)
                seed(VERIFY_SITE, BAD16)
            },
        )
        for (setup in setups) {
            val f = Fixture()
            f.ops.setup()
            f.patch()
            assertEquals("nomatch@1", f.map["sha.S1"])
            assertTrue(f.ops.writes.isEmpty())
        }
    }

    @Test
    fun `a cold process with no mapped library returns NO_LIB`() {
        val f = Fixture()
        f.ops.lib = null
        assertEquals(NO_LIB, f.patch())
        assertTrue(f.map.isEmpty())
    }

    @Test
    fun `an unsupported ABI settles and marks the cheap key settled`() {
        val f = Fixture()
        f.ops.lib = NativeMemory.MappedLib(0x400000L, "/lib.so", 0L, "x86_64")
        assertEquals(SETTLED, f.patch())
        assertTrue("later runs must not rescan", f.shared.isSettled("ck"))
        assertTrue(f.map.isEmpty())
    }

    @Test
    fun `a warm lib-sha cache skips opening and hashing the library`() {
        val cold = Fixture("sha.S1" to "blocklisted@1") // terminal, so only hashing needs the .so
        cold.patch()
        assertEquals("S1", cold.map["lib.ck"])
        assertEquals(1, cold.ops.openLibCalls)

        val warm = Fixture(*cold.map.toList().toTypedArray())
        warm.patch()
        assertEquals("the warm cache must not re-open the ~150 MB library", 0, warm.ops.openLibCalls)
        assertEquals("blocklisted@1", warm.map["sha.S1"])
        assertTrue(warm.ops.writes.isEmpty())
    }

    // ---- recover: a non-terminal survival marker from a prior launch ----

    @Test
    fun `recover adopts a still-live patch as a cancelled run, not a crash`() {
        val f = Fixture("sha.S1" to "unconfirmed@1")
        f.ops.seed(LIVE, PATCH)
        f.ops.exitKind = ExitKind.CRASH // the process never died, so a recorded crash isn't ours

        f.patch()

        assertEquals("unconfirmed@1", f.map["sha.S1"])
        assertTrue(f.shared.hasPending())
        assertNull("adoption must not count a crash", f.map["crash.S1"])
    }

    @Test
    fun `recover blocklists a native crash at once and an unattributable exit at the count threshold`() {
        // exitSince scans every main-process record since the marker, so a crash behind a newer clean exit still
        // reads CRASH (that scan is pinned in PatchGuardsTest). Without a marker the exit is UNKNOWN and the
        // crash-count decides: 1 + this exit == CRASH_THRESHOLD.
        val cases = listOf(
            mapOf("sha.S1" to "attempting@1", "mtime.S1" to "500") to ExitKind.CRASH,
            mapOf("sha.S1" to "unconfirmed@1", "mtime.S1" to "500") to ExitKind.CRASH,
            mapOf("sha.S1" to "attempting@1", "crash.S1" to "1") to ExitKind.UNKNOWN,
        )
        for ((state, exit) in cases) {
            val f = Fixture(*state.toList().toTypedArray())
            f.ops.seed(LIVE, OTHER4) // not patched: the process died
            f.ops.exitKind = exit

            f.patch()

            assertEquals(state.toString(), "blocklisted@1", f.map["sha.S1"])
            assertEquals(state.toString(), "S1", f.map["giveup.blocklistedShas"])
            assertNull(state.toString(), f.map["crash.S1"])
            assertNull(state.toString(), f.map["mtime.S1"])
            assertTrue(state.toString(), f.ops.writes.isEmpty())
        }
    }

    @Test
    fun `a clean kill retries without a strike and clears the count, so unattributable exits cannot add up`() {
        // A run swiped away during the ~5.5 s locate+self-test window leaves a marker, but the OS records a CLEAN
        // exit. Retrying it must not bump the crash-count, even at the threshold, and must clear a leftover count;
        // otherwise a build that passed every self-test it took is blocklisted by a third unattributable exit.
        val first = Fixture("sha.S1" to "attempting@1") // no marker time: UNKNOWN
        first.ops.seed(LIVE, VERIFY)
        first.patch()
        assertEquals("an unknown retry counts", "1", first.map["crash.S1"])
        assertEquals("unconfirmed@1", first.map["sha.S1"])
        assertTrue(first.shared.hasPending())

        val second = nextLaunch(first)
        second.ops.exitKind = ExitKind.CLEAN // 1 + this exit would reach CRASH_THRESHOLD
        second.patch()
        assertNull("the clean end clears the strike", second.map["crash.S1"])
        assertEquals("unconfirmed@1", second.map["sha.S1"])
        assertTrue(second.shared.hasPending())
        assertNull(second.map["giveup.blocklistedShas"])

        val third = nextLaunch(second)
        third.patch()
        assertEquals("not blocklisted: the count restarted", "unconfirmed@1", third.map["sha.S1"])
        assertEquals("1", third.map["crash.S1"])
        assertNull(third.map["giveup.blocklistedShas"])
    }

    /** The launch after [prior]: a fresh process over the persisted store [prior] left behind. */
    private fun nextLaunch(prior: Fixture): Fixture = Fixture(*prior.map.toList().toTypedArray()).also {
        it.ops.seed(LIVE, VERIFY)
    }

    // ---- cachedApply: the OK fast path and its post-OK crash backstop ----

    private fun okFixture(): Fixture = Fixture("sha.S1" to OK_SPLIT).also {
        it.ops.seed(LIVE, OTHER4) // adder site, not yet patched
        it.ops.seed(VERIFY_SITE, VERIFY)
    }

    @Test
    fun `cachedApply re-applies an OK build and keeps the post-OK crash count by exit kind`() {
        // Only a native crash since the last apply counts. A clean launch resets the count, an unattributable exit
        // leaves it, and without exit reasons (API < 30) the backstop is skipped and a leftover count survives.
        data class Case(val exitReasons: Boolean, val okApply: String?, val exit: ExitKind, val before: String?, val after: String?)
        val cases = listOf(
            Case(true, null, ExitKind.CRASH, null, null), // no prior apply time: nothing to attribute
            Case(true, "100", ExitKind.CRASH, null, "1"), // counted, below the threshold: still re-applied
            Case(true, "100", ExitKind.UNKNOWN, "1", "1"),
            Case(true, "100", ExitKind.CLEAN, "3", null),
            Case(false, "100", ExitKind.CRASH, "5", "5"),
        )
        for (c in cases) {
            val f = okFixture()
            c.okApply?.let { f.map["okapply.S1"] = it }
            c.before?.let { f.map["okcrash.S1"] = it }
            f.ops.exitReasons = c.exitReasons
            f.ops.exitKind = c.exit

            val msg = c.toString()
            assertEquals(msg, SETTLED, f.patch())
            assertEquals(msg, c.after, f.map["okcrash.S1"])
            assertEquals(msg, listOf(LIVE to PATCH), f.ops.writes)
            assertEquals(msg, "1000", f.map["okapply.S1"])
            assertEquals(msg, OK_SPLIT, f.map["sha.S1"])
        }
    }

    @Test
    fun `the threshold-th consecutive post-OK crash demotes the build to blocklisted`() {
        val f = okFixture()
        f.map["okapply.S1"] = "100"
        f.map["okcrash.S1"] = "1" // becomes 2 == OK_CRASH_THRESHOLD
        f.ops.exitKind = ExitKind.CRASH

        f.patch()

        assertEquals("blocklisted@1", f.map["sha.S1"])
        assertNull("blocklist clears the ok-crash count", f.map["okcrash.S1"])
        assertEquals("S1", f.map["giveup.blocklistedShas"])
        assertTrue("demote returns before any re-apply write", f.ops.writes.isEmpty())
    }

    @Test
    fun `cachedApply with an already-live patch adopts it and re-stamps, no write`() {
        val f = okFixture()
        f.ops.seed(LIVE, PATCH)
        f.patch()
        assertTrue(f.ops.writes.isEmpty())
        assertEquals("1000", f.map["okapply.S1"])
    }

    @Test
    fun `cachedApply on a verify mismatch or an unreadable patch site neither writes nor stamps`() {
        val setups = listOf<FakeNativeOps.() -> Unit>(
            { seed(VERIFY_SITE, BAD16) },
            {
                mem.clear() // no revert image for the patch site, so no write is attempted
                seed(VERIFY_SITE, VERIFY)
            },
        )
        for (setup in setups) {
            val f = okFixture()
            f.ops.setup()
            f.patch()
            assertTrue(f.ops.writes.isEmpty())
            assertNull(f.map["okapply.S1"])
        }
    }

    @Test
    fun `cachedApply restores the saved patch-site bytes when the re-apply write is refused`() {
        val f = okFixture()
        f.ops.writeAllowed = false

        f.patch()

        // The verify window sits at a different address on the inlined and mode eras and must never land here.
        assertEquals(listOf(LIVE to PATCH, LIVE to OTHER4), f.ops.writes)
        assertNull(f.map["okapply.S1"])
    }

    // ---- per-device give-up and fast-path adoption ----

    @Test
    fun `a disabled give-up settles before any native work`() {
        val f = Fixture("patchDisabledOnDevice" to "true@1")
        assertEquals(SETTLED, f.patch())
        assertEquals(0, f.ops.findLibCalls)
    }

    @Test
    fun `the fast path adopts a peer's live patch and settles without probing`() {
        val f = Fixture()
        f.shared.primeApplied(LIVE.toString(16), PATCH)
        f.ops.seed(LIVE, PATCH)
        assertEquals(SETTLED, f.patch())
        assertEquals(0, f.ops.findLibCalls)
    }

    @Test
    fun `a stale applied record is cleared and the attempt proceeds`() {
        val f = Fixture()
        f.shared.primeApplied(LIVE.toString(16), PATCH)
        f.ops.seed(LIVE, VERIFY) // live bytes no longer match the record
        f.patch()
        assertTrue(f.log.contains("clearApplied"))
        assertEquals("unconfirmed@1", f.map["sha.S1"])
    }

    // ---- promotion ----

    @Test
    fun `markRunSurvived promotes only a pending sha, stamping the ok-apply time and clearing crash state`() {
        val f = Fixture(
            "sha.S1" to "unconfirmed@1",
            "crash.S1" to "1",
            "mtime.S1" to "500",
            "okapply.S1" to "100",
            "okcrash.S1" to "1",
        )
        f.engine().markRunSurvived()
        assertEquals("nothing pending: untouched", "unconfirmed@1", f.map["sha.S1"])

        f.shared.setPending("S1", OK1)
        f.engine().markRunSurvived()

        assertEquals(OK1, f.map["sha.S1"])
        assertNull(f.map["crash.S1"])
        assertNull(f.map["mtime.S1"])
        // Stamped rather than removed, so a same-session crash right after promotion has a backstop baseline.
        assertEquals("1000", f.map["okapply.S1"])
        assertNull(f.map["okcrash.S1"])
        assertFalse(f.shared.hasPending())
    }

    @Test
    fun `no run active at completion promotes over the grace window, an active run blocks it`() = runTest {
        // When the run that hosted the attempt has already ended (nothing active at completion), no completion
        // hook is left to host the promotion, so the grace path promotes the build — otherwise it stays
        // UNCONFIRMED and is re-located and re-self-tested on every launch. While a run is still active, the grace
        // path is not taken; that run's own hook will promote when it ends. Driven on the test's virtual clock, so
        // the grace window is exercised deterministically with no real-time sleep deciding the outcome.
        for (runActive in listOf(false, true)) {
            val f = Fixture()
            f.ops.seed(LIVE, VERIFY)
            val runJob = Job().apply { if (!runActive) complete() }
            val waiters = ConcurrentHashMap.newKeySet<Job>().apply { add(runJob) }
            val attempt = CompletableDeferred(f.patch())
            val grace = 40.milliseconds

            promoteOnAttemptCompletion(attempt, waiters, this, grace, { true }) {
                f.engine().markRunSurvived()
            }

            if (runActive) {
                // The grace path is never taken while a run is active, so advancing past the window changes nothing.
                advanceTimeBy(grace * 3)
                runCurrent()
                assertEquals("not promoted while a run is active", "unconfirmed@1", f.map["sha.S1"])
                assertTrue(f.shared.hasPending())
            } else {
                // Not promoted before the grace window elapses...
                advanceTimeBy(grace / 2)
                runCurrent()
                assertEquals("not promoted before the grace window elapses", "unconfirmed@1", f.map["sha.S1"])
                assertTrue(f.shared.hasPending())
                // ...then the grace path promotes once the window passes and no run can host it.
                advanceUntilIdle()
                assertEquals("the grace path promotes once no run can host it", OK1, f.map["sha.S1"])
                assertEquals("1000", f.map["okapply.S1"])
                assertFalse(f.shared.hasPending())
            }
        }
    }

    // ---- promote on survival: the full fresh -> pending -> promote -> restart story ----

    @Test
    fun `promote on survival then a restart over the same store takes the cached-OK path`() {
        // 1. Fresh build: locate, write the patch, self-test it, leave it UNCONFIRMED with pending set.
        val first = Fixture()
        first.ops.seed(LIVE, VERIFY) // RET era: verify window == patch site
        assertEquals(SETTLED, first.patch())
        assertEquals("unconfirmed@1", first.map["sha.S1"])
        assertTrue("a fresh attempt must set pending for a later run to promote", first.shared.hasPending())
        assertEquals("the fresh attempt self-tests exactly once", 1, first.ops.selfTestCalls)
        assertTrue("the fresh attempt locates", first.log.contains("locate"))

        // 2. The run that hosted the attempt ends with the process still alive: promote to an ok: record,
        //    clearing the crash-count and the write-ahead marker.
        first.engine().markRunSurvived()
        assertEquals(OK1, first.map["sha.S1"])
        assertNull("crash-count cleared on promotion", first.map["crash.S1"])
        assertNull("attempt marker cleared on promotion", first.map["mtime.S1"])

        // 3. A process restart: a NEW engine with FRESH in-process shared state over the persisted store. The
        //    ok: record drives the cached path — re-applied with no re-locate and no self-test.
        val restart = nextLaunch(first)
        assertEquals(SETTLED, restart.patch())
        assertFalse("no re-locate on the cached-OK path", restart.log.contains("locate"))
        assertEquals("no self-test on the cached-OK path", 0, restart.ops.selfTestCalls)
        assertEquals("the warm sha cache skips opening the ~150 MB library", 0, restart.ops.openLibCalls)
        assertEquals("the cached patch bytes are written", listOf(LIVE to PATCH), restart.ops.writes)
        assertEquals(PATCH, hex(restart.ops.readMem(LIVE, 4)!!))
        assertEquals("the build stays ok:, never blocklisted", OK1, restart.map["sha.S1"])
    }

    @Test
    fun `repeated restarts after promotion never re-locate, self-test, or blocklist (B3)`() {
        // B3: when promotion never ran, the build stayed UNCONFIRMED and every launch re-located, re-self-tested,
        // and below API 30 the third launch blocklisted a build that had passed twice. Once promotion runs, the
        // build is ok: and every restart takes the cached path, which — with exit reasons unavailable (API < 30) —
        // skips the crash backstop entirely, so no number of clean restarts can blocklist it.
        val first = Fixture()
        first.ops.seed(LIVE, VERIFY)
        first.patch()
        first.engine().markRunSurvived()
        assertEquals(OK1, first.map["sha.S1"])

        var prior = first
        repeat(3) { i ->
            val restart = nextLaunch(prior)
            restart.ops.exitReasons = false // API < 30: the exact window B3 named
            assertEquals(SETTLED, restart.patch())
            assertFalse("restart ${i + 1} must not re-locate", restart.log.contains("locate"))
            assertEquals("restart ${i + 1} must not self-test", 0, restart.ops.selfTestCalls)
            assertEquals("restart ${i + 1} re-applies the cached patch", listOf(LIVE to PATCH), restart.ops.writes)
            assertEquals("restart ${i + 1} stays ok:", OK1, restart.map["sha.S1"])
            assertNull("restart ${i + 1} never adds to the give-up set", restart.map["giveup.blocklistedShas"])
            assertNull("restart ${i + 1} never disables the device", restart.map["patchDisabledOnDevice"])
            prior = restart
        }
    }

    @Test
    fun `a run active at the attempt's completion promotes when it later ends (B3)`() {
        // B3's root: the only caller of promote was the run's completion hook, and the run ended before pending
        // was set, so nothing promoted. In the real flow Entry.apply awaits the attempt before returning, so the
        // run is still active when the attempt completes with pending set; promoteOnAttemptCompletion registers
        // that run's completion hook, which promotes when the run ends. This models that ordering with a real Job
        // (Entry itself needs Android: WebView warm-up + XrwStripper).
        val f = Fixture()
        f.ops.seed(LIVE, VERIFY)
        val runJob = Job()
        val waiters = ConcurrentHashMap.newKeySet<Job>().apply { add(runJob) }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // apply() awaits the attempt, so pending is set while the run is still active.
        val attempt = CompletableDeferred(f.patch())
        assertEquals("unconfirmed@1", f.map["sha.S1"])
        assertTrue(f.shared.hasPending())

        // The attempt completes with the run active: its hook owns promotion, nothing fires yet.
        promoteOnAttemptCompletion(attempt, waiters, scope, 40.milliseconds, { true }) { f.engine().markRunSurvived() }
        assertEquals("not promoted while the run is active", "unconfirmed@1", f.map["sha.S1"])
        assertTrue(f.shared.hasPending())

        // The run completes AFTER pending was set -> its hook fires -> promotion happens, exactly once.
        runJob.complete()
        assertEquals(OK1, f.map["sha.S1"])
        assertFalse("takePending is atomic, so no second promote", f.shared.hasPending())

        scope.cancel()
    }

    companion object {
        private const val VERIFY = "00112233445566778899aabbccddeeff" // 16-byte verify window
        private const val BAD16 = "ffffffffffffffffffffffffffffffff"
        private const val PATCH = "c0035fd6" // arm64 RET
        private const val OTHER4 = "00112233" // the verify window's first 4 bytes: an unpatched adder site
        private const val LIVE = 0x401000L // loadBase 0x400000 + adder 0x1000
        private const val VERIFY_SITE = 0x402000L // loadBase + a verify window distinct from the adder
        private const val OK1 = "ok:1000:1000:$VERIFY:$PATCH"
        private const val OK_SPLIT = "ok:1000:2000:$VERIFY:$PATCH" // adder 0x1000, verify 0x2000
    }
}
