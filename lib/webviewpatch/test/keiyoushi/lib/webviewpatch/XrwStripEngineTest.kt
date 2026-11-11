package keiyoushi.lib.webviewpatch

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests for the [XrwStripEngine] orchestration, driven entirely off-device through fakes for
 * the three seams ([NativeOps], [KvStore], [SharedState]) that [XrwStripper] wires to the real Android
 * implementations. Each test pins today's exact persisted store state, [XrwStripper.Outcome], memory writes and
 * shared-coordination effects for one path, so a later deep refactor of the state machine can't regress it.
 *
 * The strings asserted (store keys/prefixes, state values, the `ok:` and `@version` formats) are deliberately
 * spelled out rather than referenced from constants: they are persisted to disk on real devices, so changing a
 * value silently breaks reading an existing store, and these literals are the guard against that.
 */
class XrwStripEngineTest {

    // ---- fakes ----

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    private fun bytes(h: String): ByteArray = h.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /** In-memory [KvStore] with [KvStore.Txn.remove]; appends every mutation to [log] for order-pinning. */
    private class MapStore(
        val map: MutableMap<String, String> = mutableMapOf(),
        val log: MutableList<String>? = null,
    ) : KvStore {
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
            log?.add("put $key=$value")
        }
        override fun remove(key: String) {
            map.remove(key)
            log?.add("remove $key")
        }
        override fun transact(block: KvStore.Txn.() -> Unit) {
            val txn = object : KvStore.Txn {
                override fun get(key: String): String? = map[key]
                override fun set(key: String, value: String) {
                    map[key] = value
                    log?.add("set $key=$value")
                }
                override fun remove(key: String) {
                    map.remove(key)
                    log?.add("rm $key")
                }
            }
            txn.block()
        }
    }

    private class FakeMapped(private val sha: String) : MappedImage {
        var closed = false
        override val source: ByteSource = ArrayByteSource(ByteArray(0))
        override fun sha256(): String = sha
        override fun close() {
            closed = true
        }
    }

    private class FakeNativeOps(
        private val log: MutableList<String>? = null,
    ) : NativeOps {
        val mem = HashMap<Long, Byte>()
        var writeAllowed = true
        val writes = mutableListOf<Pair<Long, String>>()

        var lib: NativeMemory.MappedLib? = NativeMemory.MappedLib(0x400000L, "/lib.so", 0L, "arm64")
        var cheapKey = "ck"
        var sha = "S1"
        var locateResult: LocateResult = LocateResult.emit("arm64", 0x1000L, "high", VERIFY, PATCH, 0x1000L)
        var selfTestResult = LoopbackProbe.Result.STRIPPED
        var exitKind = ExitKind.UNKNOWN
        var exitReasons = true
        var clock = 1000L
        var findLibCalls = 0

        fun seed(addr: Long, h: String) {
            val b = h.chunked(2).map { it.toInt(16).toByte() }
            b.forEachIndexed { i, v -> mem[addr + i] = v }
        }

        override fun findLib(): NativeMemory.MappedLib? {
            findLibCalls++
            return lib
        }
        override fun cheapKey(lib: NativeMemory.MappedLib): String = cheapKey
        override fun openLib(lib: NativeMemory.MappedLib): MappedImage? = FakeMapped(sha)
        override fun locate(src: ByteSource): LocateResult = locateResult
        override fun readMem(va: Long, len: Int): ByteArray? {
            val out = ByteArray(len)
            for (i in 0 until len) out[i] = mem[va + i] ?: return null
            return out
        }
        override fun writeMem(va: Long, bytes: ByteArray): Boolean {
            val h = bytes.joinToString("") { "%02x".format(it) }
            writes += va to h
            log?.add("write@${va.toString(16)}:$h")
            return if (writeAllowed) {
                bytes.forEachIndexed { i, v -> mem[va + i] = v }
                true
            } else {
                false
            }
        }
        override fun exitSince(markerMs: Long): ExitKind = exitKind
        override fun exitReasonsAvailable(): Boolean = exitReasons
        override suspend fun selfTest(): LoopbackProbe.Result {
            log?.add("selftest")
            return selfTestResult
        }
        override fun now(): Long = clock
    }

    private class FakeSharedState(private val log: MutableList<String>? = null) : SharedState {
        override val lock = Any()
        private var addr: String? = null
        private var patch: String? = null
        private var sha: String? = null
        private var pending: String? = null
        private var inflightFlag = false
        private val settledKeys = HashSet<String>()
        var onInflight: (() -> Boolean)? = null

        override fun fastPathAddr(): String? = addr
        override fun fastPathPatch(): String? = patch
        override fun clearApplied() {
            addr = null
            patch = null
            sha = null
            log?.add("clearApplied")
        }
        override fun markApplied(live: Long, patchHex: String, sha: String) {
            addr = live.toString(16)
            patch = patchHex
            this.sha = sha
            log?.add("markApplied@${live.toString(16)}")
        }
        override fun setPending(sha: String, okValue: String) {
            pending = "$sha\u0000$okValue"
            log?.add("setPending")
        }
        override fun takePending(): Pair<String, String>? {
            val v = pending ?: return null
            pending = null
            val s = v.indexOf('\u0000')
            return v.substring(0, s) to v.substring(s + 1)
        }
        override fun hasPending(): Boolean = pending != null
        override fun inflight(): Boolean = onInflight?.invoke() ?: inflightFlag
        override fun setInflight(value: Boolean) {
            inflightFlag = value
        }
        override fun isSettled(key: String): Boolean = key in settledKeys
        override fun markSettled(key: String) {
            settledKeys.add(key)
        }
        fun primeApplied(addrHex: String, patchHex: String) {
            addr = addrHex
            patch = patchHex
        }
    }

    private class Fixture {
        val log = mutableListOf<String>()
        val ops = FakeNativeOps(log)
        val map = mutableMapOf<String, String>()
        val store = MapStore(map, log)
        val shared = FakeSharedState(log)
        val engine get() = XrwStripEngine(ops, store, shared)
        fun patch(): XrwStripper.Outcome = runBlocking { engine.doPatch() }
    }

    // ---- fresh attempt paths ----

    @Test
    fun `fresh success writes the patch, stores unconfirmed and sets pending`() {
        val f = Fixture()
        f.ops.seed(LIVE, VERIFY) // verify window == patch site (RET era): adder == verifyVa

        val outcome = f.patch()

        assertEquals(XrwStripper.Outcome.SETTLED, outcome)
        assertEquals("unconfirmed", f.map["sha.S1"])
        assertEquals("1000", f.map["mtime.S1"]) // write-ahead marker time == fake clock, kept through UNCONFIRMED
        assertEquals("c0035fd6", hex(f.ops.readMem(LIVE, 4)!!)) // patch is live
        assertTrue("pending must be set for a later run to promote", f.shared.hasPending())
        // Marker-write precedes the memory write (write-ahead ordering).
        val iMarker = f.log.indexOf("set sha.S1=attempting")
        val iWrite = f.log.indexOf("write@401000:c0035fd6")
        assertTrue("attempt marker before mem write", iMarker in 0 until iWrite)
    }

    @Test
    fun `self-test PRESENT reverts the write and blocklists on the first strike`() {
        val f = Fixture()
        f.ops.seed(LIVE, VERIFY)
        f.ops.selfTestResult = LoopbackProbe.Result.PRESENT

        f.patch()

        assertEquals("blocklisted@1", f.map["sha.S1"])
        assertNull("marker dropped by blocklist", f.map["mtime.S1"])
        assertEquals("S1", f.map["giveup.blocklistedShas"]) // feeds the per-device give-up
        // patchAndSelfTest saves and restores the 4-byte patch window (unlike cachedApply's 16-byte verify window).
        assertEquals("write then revert", listOf(LIVE to "c0035fd6", LIVE to OTHER4), f.ops.writes)
        assertEquals("memory restored", VERIFY, hex(f.ops.readMem(LIVE, 16)!!))
    }

    @Test
    fun `self-test INCONCLUSIVE reverts and caches the back-off without counting a give-up`() {
        val f = Fixture()
        f.ops.seed(LIVE, VERIFY)
        f.ops.selfTestResult = LoopbackProbe.Result.INCONCLUSIVE

        f.patch()

        assertEquals("inconclusive@1", f.map["sha.S1"])
        assertNull("inconclusive drops the marker", f.map["mtime.S1"])
        assertNull("inconclusive is environmental, not a give-up strike", f.map["giveup.blocklistedShas"])
        assertEquals("memory restored", VERIFY, hex(f.ops.readMem(LIVE, 16)!!))
    }

    @Test
    fun `a refused write blocklists at once with no crash-count detour`() {
        val f = Fixture()
        f.ops.seed(LIVE, VERIFY)
        f.ops.writeAllowed = false

        f.patch()

        assertEquals("blocklisted@1", f.map["sha.S1"])
        assertNull(f.map["crash.S1"]) // no ambiguous-crash counting: deterministic refusal
        assertEquals("S1", f.map["giveup.blocklistedShas"])
        // Patch write attempted, then a restore of the saved 4-byte original.
        assertEquals(listOf(LIVE to "c0035fd6", LIVE to OTHER4), f.ops.writes)
    }

    @Test
    fun `locate with no adder stores a version-stamped nomatch`() {
        val f = Fixture()
        f.ops.locateResult = LocateResult.noMatch("arm64", "no strategy matched")

        f.patch()

        assertEquals("nomatch@1", f.map["sha.S1"])
        assertTrue("no write on a no-match", f.ops.writes.isEmpty())
    }

    @Test
    fun `a verify-window mismatch stores nomatch and never writes`() {
        val f = Fixture()
        // Distinct verify site that reads back wrong: the live bytes aren't what the locator expects.
        f.ops.locateResult = LocateResult.emit("arm64", 0x1000L, "high", VERIFY, PATCH, 0x2000L)
        f.ops.seed(LIVE, OTHER4) // adder site (not patched)
        f.ops.seed(0x402000L, "ffffffffffffffffffffffffffffffff") // verify site: wrong

        f.patch()

        assertEquals("nomatch@1", f.map["sha.S1"])
        assertTrue(f.ops.writes.isEmpty())
    }

    @Test
    fun `a cold process with no mapped library returns NO_LIB`() {
        val f = Fixture()
        f.ops.lib = null

        assertEquals(XrwStripper.Outcome.NO_LIB, f.patch())
        assertTrue(f.map.isEmpty())
    }

    @Test
    fun `an unsupported ABI settles and marks the cheap key settled`() {
        val f = Fixture()
        f.ops.lib = NativeMemory.MappedLib(0x400000L, "/lib.so", 0L, "x86_64")

        assertEquals(XrwStripper.Outcome.SETTLED, f.patch())
        assertTrue("later runs must not rescan", f.shared.isSettled("ck"))
        assertTrue("no store work for an unsupported ABI", f.map.isEmpty())
    }

    // ---- recover: a non-terminal survival marker from a prior launch ----

    @Test
    fun `recover adopts a still-live patch as a cancelled run, not a crash`() {
        val f = Fixture()
        f.map["sha.S1"] = "unconfirmed"
        f.ops.seed(LIVE, PATCH) // patch still live -> a cancelled run, never counted
        f.ops.exitKind = ExitKind.CRASH // even with a crash recorded, a live patch wins

        f.patch()

        assertEquals("unconfirmed", f.map["sha.S1"])
        assertTrue(f.shared.hasPending())
        assertNull("adoption must not count a crash", f.map["crash.S1"])
    }

    @Test
    fun `recover blocklists a native crasher on the first strike`() {
        val f = Fixture()
        f.map["sha.S1"] = "attempting"
        f.map["mtime.S1"] = "500"
        f.ops.seed(LIVE, OTHER4) // not patched: the process died
        f.ops.exitKind = ExitKind.CRASH

        f.patch()

        assertEquals("blocklisted@1", f.map["sha.S1"])
        assertEquals("S1", f.map["giveup.blocklistedShas"])
    }

    @Test
    fun `recover retries a clean kill WITHOUT counting a strike`() {
        // The bug: a short run swiped away during the ~5.5 s locate+self-test window leaves a marker but the OS
        // records a CLEAN main-process exit. That must re-attempt without bumping the crash-count, so a working
        // build the user kept swiping away is never pushed toward the blocklist.
        val f = Fixture()
        f.map["sha.S1"] = "attempting"
        f.map["mtime.S1"] = "500"
        f.ops.seed(LIVE, VERIFY) // not patched; verify window matches so the re-attempt succeeds
        f.ops.exitKind = ExitKind.CLEAN

        f.patch()

        assertNull("a clean kill must not count a crash strike", f.map["crash.S1"])
        assertEquals("unconfirmed", f.map["sha.S1"]) // re-attempt succeeds and holds UNCONFIRMED
        assertTrue(f.shared.hasPending())
    }

    @Test
    fun `two consecutive clean kills still do not blocklist`() {
        // Even with a leftover count already at the threshold, a CLEAN exit re-attempts (never blocklists): clean
        // kills can't accumulate toward COUNT_BLOCKLIST, so a swipe-away streak never kills a good build.
        val f = Fixture()
        f.map["sha.S1"] = "selftest"
        f.map["mtime.S1"] = "500"
        f.map["crash.S1"] = "1" // prospective count would be 2 == CRASH_THRESHOLD, but CLEAN ignores it
        f.ops.seed(LIVE, VERIFY)
        f.ops.exitKind = ExitKind.CLEAN

        f.patch()

        assertEquals("not blocklisted: a clean exit is retried", "unconfirmed", f.map["sha.S1"])
        assertNull("no per-device give-up strike from a clean kill", f.map["giveup.blocklistedShas"])
        assertEquals("the leftover count is left as-is, not bumped", "1", f.map["crash.S1"])
    }

    @Test
    fun `recover blocklists on a native crash hidden behind a newer clean exit`() {
        // #4: the OS logs a CLEAN session after the crash, yet exitSince scans ALL main-process records since the
        // marker and still returns CRASH, so recover blocklists on the first strike (modelled by the fake here;
        // the scan itself is pinned in PatchGuardsTest.classifyExitSince).
        val f = Fixture()
        f.map["sha.S1"] = "unconfirmed"
        f.map["mtime.S1"] = "500"
        f.ops.seed(LIVE, OTHER4) // not patched: the process died
        f.ops.exitKind = ExitKind.CRASH

        f.patch()

        assertEquals("blocklisted@1", f.map["sha.S1"])
        assertEquals("S1", f.map["giveup.blocklistedShas"])
    }

    @Test
    fun `recover blocklists an UNKNOWN exit once the crash-count reaches the threshold`() {
        val f = Fixture()
        f.map["sha.S1"] = "selftest"
        f.map["crash.S1"] = "1" // prospective count becomes 2 == CRASH_THRESHOLD
        f.ops.seed(LIVE, OTHER4)
        // No mtime marker -> the engine passes ExitKind.UNKNOWN: unattributable, so the count decides.

        f.patch()

        assertEquals("blocklisted@1", f.map["sha.S1"])
        assertEquals("S1", f.map["giveup.blocklistedShas"])
    }

    @Test
    fun `recover with no live patch, an UNKNOWN exit and a low count retries and counts`() {
        val f = Fixture()
        f.map["sha.S1"] = "attempting"
        f.ops.seed(LIVE, VERIFY) // not patched (first 4 != patch); verify window matches for the re-attempt
        // No mtime marker -> ExitKind.UNKNOWN (the conservative counted-retry fallback).

        f.patch()

        assertEquals("1", f.map["crash.S1"]) // an unknown retry bumps the crash count
        assertEquals("unconfirmed", f.map["sha.S1"]) // then the re-attempt succeeds and holds UNCONFIRMED
        assertTrue(f.shared.hasPending())
    }

    // ---- cachedApply: the OK fast path ----

    private fun okFixture(): Fixture {
        val f = Fixture()
        f.map["sha.S1"] = "ok:1000:2000:$VERIFY:$PATCH" // adder 0x1000, verify 0x2000 (distinct)
        f.ops.seed(LIVE, OTHER4) // adder site, not yet patched
        f.ops.seed(0x402000L, VERIFY) // verify site matches
        return f
    }

    @Test
    fun `cachedApply re-verifies, re-applies and stamps the ok-apply marker`() {
        val f = okFixture()

        assertEquals(XrwStripper.Outcome.SETTLED, f.patch())
        assertEquals("c0035fd6", hex(f.ops.readMem(LIVE, 4)!!))
        assertEquals("1000", f.map["okapply.S1"]) // crash-backstop marker == fake clock
        assertEquals("sha unchanged (still OK)", "ok:1000:2000:$VERIFY:$PATCH", f.map["sha.S1"])
    }

    @Test
    fun `cachedApply with an already-live patch adopts it and re-stamps, no write`() {
        val f = okFixture()
        f.ops.seed(LIVE, PATCH) // already patched

        f.patch()

        assertTrue("no write when the patch is already live", f.ops.writes.isEmpty())
        assertEquals("1000", f.map["okapply.S1"])
    }

    @Test
    fun `cachedApply on a verify mismatch neither writes nor stamps`() {
        val f = okFixture()
        f.ops.seed(0x402000L, "ffffffffffffffffffffffffffffffff") // verify site wrong

        f.patch()

        assertTrue(f.ops.writes.isEmpty())
        assertNull(f.map["okapply.S1"])
    }

    @Test
    fun `cachedApply restores the saved window when the re-apply write is refused`() {
        val f = okFixture()
        f.ops.writeAllowed = false

        f.patch()

        // Patch attempt refused, then the read-back window is written at the live site to undo a partial write.
        assertEquals(listOf(LIVE to "c0035fd6", LIVE to hex(bytes(VERIFY))), f.ops.writes)
        assertNull("no ok-apply marker on a failed re-apply", f.map["okapply.S1"])
    }

    @Test
    fun `cachedApply with a malformed ok value does nothing`() {
        val f = Fixture()
        f.map["sha.S1"] = "ok:1000:2000" // only 2 fields, not 4

        assertEquals(XrwStripper.Outcome.SETTLED, f.patch())
        assertTrue(f.ops.writes.isEmpty())
    }

    // ---- OK-apply crash backstop (API 30+) ----

    @Test
    fun `one post-OK native crash only counts, and the build is still re-applied`() {
        val f = okFixture()
        f.map["okapply.S1"] = "100"
        f.ops.exitKind = ExitKind.CRASH

        f.patch()

        assertEquals("1", f.map["okcrash.S1"]) // counted, not demoted
        assertEquals("1000", f.map["okapply.S1"]) // re-applied, marker refreshed
        assertTrue("sha stays OK", f.map["sha.S1"]!!.startsWith("ok:"))
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
    fun `a clean launch resets the post-OK crash count before re-applying`() {
        val f = okFixture()
        f.map["okapply.S1"] = "100"
        f.map["okcrash.S1"] = "3"
        f.ops.exitKind = ExitKind.CLEAN // a clean end since the last apply

        f.patch()

        assertNull("clean launch clears the count", f.map["okcrash.S1"])
        assertEquals("1000", f.map["okapply.S1"]) // and re-applies
    }

    @Test
    fun `an UNKNOWN post-OK exit leaves the count untouched and does not demote`() {
        // Only a native CRASH demotes a confirmed-OK build. An unattributable exit (API 30+ but no record since
        // the apply) must neither bump nor reset the count, and must re-apply as usual.
        val f = okFixture()
        f.map["okapply.S1"] = "100"
        f.map["okcrash.S1"] = "1"
        f.ops.exitKind = ExitKind.UNKNOWN

        f.patch()

        assertEquals("untouched: nothing attributable", "1", f.map["okcrash.S1"])
        assertEquals("1000", f.map["okapply.S1"]) // still re-applied
        assertTrue("sha stays OK", f.map["sha.S1"]!!.startsWith("ok:"))
    }

    @Test
    fun `without exit reasons the backstop is skipped and the count is left untouched`() {
        val f = okFixture()
        f.map["okcrash.S1"] = "5" // a leftover; API < 30 must not reset it
        f.ops.exitReasons = false

        f.patch()

        assertEquals("5", f.map["okcrash.S1"]) // untouched: the whole backstop block is gated out
        assertEquals("1000", f.map["okapply.S1"]) // plain re-apply still happens
    }

    // ---- dispatch of cached terminal/negative states ----

    @Test
    fun `a current-version blocklist is honoured with no work`() {
        val f = Fixture()
        f.map["sha.S1"] = "blocklisted@1"

        assertEquals(XrwStripper.Outcome.SETTLED, f.patch())
        assertEquals("blocklisted@1", f.map["sha.S1"])
        assertTrue(f.ops.writes.isEmpty())
    }

    @Test
    fun `a current-version nomatch is cached with no work`() {
        val f = Fixture()
        f.map["sha.S1"] = "nomatch@1"

        f.patch()

        assertEquals("nomatch@1", f.map["sha.S1"])
        assertTrue(f.ops.writes.isEmpty())
    }

    @Test
    fun `a current-version inconclusive is cached with no work`() {
        val f = Fixture()
        f.map["sha.S1"] = "inconclusive@1"

        f.patch()

        assertEquals("inconclusive@1", f.map["sha.S1"])
        assertTrue(f.ops.writes.isEmpty())
    }

    @Test
    fun `a negative state stamped by an older version is re-attempted`() {
        val f = Fixture()
        f.map["sha.S1"] = "nomatch@0" // older than LOCATOR_VERSION: a locator fix should reach it
        f.ops.seed(LIVE, VERIFY)

        f.patch()

        assertEquals("stale negative re-attempted and succeeded", "unconfirmed", f.map["sha.S1"])
    }

    @Test
    fun `an unknown state value is treated as corrupt and does nothing`() {
        val f = Fixture()
        f.map["sha.S1"] = "something-a-newer-lib-wrote"

        assertEquals(XrwStripper.Outcome.SETTLED, f.patch())
        assertEquals("fail-safe: left untouched", "something-a-newer-lib-wrote", f.map["sha.S1"])
        assertTrue(f.ops.writes.isEmpty())
    }

    // ---- per-device give-up ----

    @Test
    fun `a disabled give-up settles before any native work`() {
        val f = Fixture()
        f.map["patchDisabledOnDevice"] = "true@1"

        assertEquals(XrwStripper.Outcome.SETTLED, f.patch())
        assertEquals("the library is never even probed", 0, f.ops.findLibCalls)
    }

    // ---- in-flight coordination ----

    @Test
    fun `the fast path adopts a peer's live patch and settles without probing`() {
        val f = Fixture()
        f.shared.primeApplied(LIVE.toString(16), PATCH)
        f.ops.seed(LIVE, PATCH) // live bytes still match the applied record

        assertEquals(XrwStripper.Outcome.SETTLED, f.patch())
        assertEquals("fast path does no locate/map work", 0, f.ops.findLibCalls)
    }

    @Test
    fun `a stale applied record is cleared and the attempt proceeds`() {
        val f = Fixture()
        f.shared.primeApplied(LIVE.toString(16), PATCH)
        f.ops.seed(LIVE, VERIFY) // live bytes no longer match the applied record -> stale

        f.patch()

        assertTrue("stale fast path falls through to a real attempt", f.ops.findLibCalls > 0)
        assertTrue("the stale record was cleared", f.log.contains("clearApplied"))
        assertEquals("unconfirmed", f.map["sha.S1"])
    }

    @Test
    fun `an in-flight peer makes this copy wait, then settle`() {
        val f = Fixture()
        var calls = 0
        // inflight: true at the claim check, false on the first awaitInFlight poll.
        f.shared.onInflight = { calls++; calls == 1 }

        assertEquals(XrwStripper.Outcome.SETTLED, f.patch())
        assertEquals("waiting copy does no native work", 0, f.ops.findLibCalls)
    }

    @Test
    fun `the proceeding copy clears the in-flight flag when done`() {
        val f = Fixture()
        f.ops.seed(LIVE, VERIFY)

        f.patch()

        assertFalse("in-flight must be released in the finally", f.shared.inflight())
    }

    // ---- markRunSurvived: promotion after a real run ----

    @Test
    fun `markRunSurvived promotes the pending sha to OK, sets the ok-apply time and clears crash state`() {
        val f = Fixture()
        f.shared.setPending("S1", "ok:1000:1000:$VERIFY:$PATCH")
        f.map["crash.S1"] = "1"
        f.map["mtime.S1"] = "500"
        f.map["okapply.S1"] = "100"
        f.map["okcrash.S1"] = "1"

        f.engine.markRunSurvived()

        assertEquals("ok:1000:1000:$VERIFY:$PATCH", f.map["sha.S1"])
        assertNull(f.map["crash.S1"])
        assertNull(f.map["mtime.S1"])
        // #5: promote stamps the ok-apply time (== ops.now()) rather than removing it, so a same-session crash
        // right after promotion has a baseline for the next launch's backstop.
        assertEquals("1000", f.map["okapply.S1"])
        assertNull(f.map["okcrash.S1"])
        assertFalse("pending consumed", f.shared.hasPending())
    }

    @Test
    fun `markRunSurvived is a no-op when nothing is pending`() {
        val f = Fixture()
        f.map["sha.S1"] = "unconfirmed"

        f.engine.markRunSurvived()

        assertEquals("unconfirmed", f.map["sha.S1"]) // untouched
    }

    companion object {
        private const val VERIFY = "00112233445566778899aabbccddeeff" // 16-byte verify window
        private const val PATCH = "c0035fd6" // arm64 RET
        private const val OTHER4 = "00112233" // the verify window's first 4 bytes: an unpatched adder site
        private const val LIVE = 0x401000L // loadBase 0x400000 + adder 0x1000
    }
}
