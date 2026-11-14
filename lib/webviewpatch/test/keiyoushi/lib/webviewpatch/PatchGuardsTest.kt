package keiyoushi.lib.webviewpatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic unit tests for the per-device give-up guard. No Android, no network: the persistent store is an
 * in-memory [MapStore].
 */
class PatchGuardsTest {

    /** In-memory [KvStore]; a fresh instance over the same backing map proves persistence across "launches". */
    private class MapStore(val map: MutableMap<String, String> = mutableMapOf()) : KvStore {
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }
        override fun remove(key: String) {
            map.remove(key)
        }
        override fun transact(block: KvStore.Txn.() -> Unit) {
            val txn = object : KvStore.Txn {
                override fun get(key: String): String? = map[key]
                override fun set(key: String, value: String) {
                    map[key] = value
                }
                override fun remove(key: String) {
                    map.remove(key)
                }
            }
            txn.block()
        }
    }

    @Test
    fun `three distinct blocklisted shas trip the give-up and it persists`() {
        val backing = mutableMapOf<String, String>()
        MapStore(backing).let { store ->
            GiveUp.recordBlocklisted(store, "sha-a")
            GiveUp.recordBlocklisted(store, "sha-b")
            assertFalse("two distinct shas must not trip", GiveUp.isDisabled(store))
            GiveUp.recordBlocklisted(store, "sha-c")
            assertTrue("the third distinct sha trips the give-up", GiveUp.isDisabled(store))
        }
        // A new store over the same backing map (a later launch) still sees the give-up.
        assertTrue(GiveUp.isDisabled(MapStore(backing)))
    }

    @Test
    fun `re-blocklisting the same sha does not double-count`() {
        val store = MapStore()
        repeat(5) { GiveUp.recordBlocklisted(store, "sha-a") }
        assertFalse("one build re-blocklisted many times must never trip", GiveUp.isDisabled(store))
        GiveUp.recordBlocklisted(store, "sha-b")
        GiveUp.recordBlocklisted(store, "sha-a") // interleaved repeat still ignored
        assertFalse(GiveUp.isDisabled(store))
        GiveUp.recordBlocklisted(store, "sha-c")
        assertTrue("three DISTINCT shas trip", GiveUp.isDisabled(store))
    }

    // ---- recoverAction: a cancellation is not a crash ----

    @Test
    fun `a live patch is adopted, never counted as a crash`() {
        // Even with a native crash recorded AND the count at threshold, a still-live patch means the process
        // never died (the run was merely cancelled): adopt it.
        assertEquals(RecoverAction.ADOPT, recoverAction(patchLive = true, exit = ExitKind.CRASH, newCrashCount = 99, threshold = 2))
        assertEquals(RecoverAction.ADOPT, recoverAction(patchLive = true, exit = ExitKind.CLEAN, newCrashCount = 1, threshold = 2))
    }

    @Test
    fun `a real native crash blocklists on the first strike`() {
        assertEquals(RecoverAction.NATIVE_BLOCKLIST, recoverAction(patchLive = false, exit = ExitKind.CRASH, newCrashCount = 1, threshold = 2))
    }

    @Test
    fun `a clean kill re-attempts without counting a strike, even at the count threshold`() {
        // The bug fix: a clean kill (user swipe-away) must never move a good build toward the blocklist, so it
        // re-attempts WITHOUT counting — and since the count never grows from clean kills, two (or more) in a
        // row still never reach COUNT_BLOCKLIST.
        assertEquals(RecoverAction.RETRY_CLEAN, recoverAction(patchLive = false, exit = ExitKind.CLEAN, newCrashCount = 1, threshold = 2))
        assertEquals(RecoverAction.RETRY_CLEAN, recoverAction(patchLive = false, exit = ExitKind.CLEAN, newCrashCount = 2, threshold = 2))
    }

    @Test
    fun `without a live patch or attributable exit, the count decides`() {
        assertEquals(RecoverAction.RETRY, recoverAction(patchLive = false, exit = ExitKind.UNKNOWN, newCrashCount = 1, threshold = 2))
        assertEquals(RecoverAction.COUNT_BLOCKLIST, recoverAction(patchLive = false, exit = ExitKind.UNKNOWN, newCrashCount = 2, threshold = 2))
    }

    // ---- classifyExitSince: a later clean session must not hide an earlier native crash ----

    @Test
    fun `classifyExitSince finds a native crash even behind a newer clean exit`() {
        // Newest main-process exit is clean (t=200) but a native crash (t=100) sits since the marker (t=50):
        // scanning ALL records since the marker still classifies it CRASH (fix: do not stop at the newest).
        val exits = listOf(MainExit(nativeCrash = false, timestampMs = 200), MainExit(nativeCrash = true, timestampMs = 100))
        assertEquals(ExitKind.CRASH, classifyExitSince(exits, markerMs = 50))
    }

    @Test
    fun `classifyExitSince is CLEAN with a post-marker exit and no crash, UNKNOWN with none`() {
        assertEquals(ExitKind.CLEAN, classifyExitSince(listOf(MainExit(nativeCrash = false, timestampMs = 200)), markerMs = 50))
        // A crash BEFORE the marker is ignored; only the clean exit falls in-window.
        assertEquals(ExitKind.CLEAN, classifyExitSince(listOf(MainExit(false, 200), MainExit(true, 10)), markerMs = 50))
        // No main-process record at/after the marker: nothing attributable.
        assertEquals(ExitKind.UNKNOWN, classifyExitSince(listOf(MainExit(true, 10)), markerMs = 50))
        assertEquals(ExitKind.UNKNOWN, classifyExitSince(emptyList(), markerMs = 50))
    }

    // ---- resolveSha: no re-read on a warm cache ----

    @Test
    fun `resolveSha reads and hashes only on a cache miss`() {
        val store = MapStore()
        var reads = 0
        val readAndHash = {
            reads++
            "deadbeef"
        }

        // Cold: the file is read + hashed once and the mapping recorded.
        assertEquals("deadbeef", resolveSha(store, "lib.key", readAndHash))
        assertEquals(1, reads)
        // Warm (same process or a later launch over the same store): the sha comes from the cheap key, no read.
        assertEquals("deadbeef", resolveSha(store, "lib.key", readAndHash))
        assertEquals("deadbeef", resolveSha(store, "lib.key", readAndHash))
        assertEquals("the warm cache must not re-read the .so", 1, reads)
    }

    @Test
    fun `resolveSha returns null and caches nothing when the read fails`() {
        val store = MapStore()
        assertNull(resolveSha(store, "lib.key") { null })
        assertNull(store.get("lib.key"))
    }

    // ---- version-stamped negative states ----

    @Test
    fun `stamp round-trips name and version, and a legacy or ok value reads as version 0`() {
        assertEquals("nomatch@3", stampState("nomatch", 3))
        assertEquals("nomatch", stateName("nomatch@3"))
        assertEquals(3, stateStamp("nomatch@3"))
        // Legacy bare value (no stamp) and an ok: value (no '@') read as version 0, name unchanged.
        assertEquals("nomatch", stateName("nomatch"))
        assertEquals(0, stateStamp("nomatch"))
        assertEquals("ok:1:2:3:4", stateName("ok:1:2:3:4"))
        assertEquals(0, stateStamp("ok:1:2:3:4"))
    }

    @Test
    fun `a negative state is stale only when an older version wrote it`() {
        assertTrue("legacy bare negative is stale under any version", negativeStateStale("nomatch", 1))
        assertTrue("older-version negative is stale", negativeStateStale("blocklisted@1", 2))
        assertFalse("same-version negative is honoured", negativeStateStale("blocklisted@2", 2))
        assertFalse("newer-version negative is honoured", negativeStateStale("inconclusive@3", 2))
    }

    @Test
    fun `give-up is version-gated so a newer lib retries an older device give-up`() {
        val store = MapStore()
        GiveUp.recordBlocklisted(store, "sha-a", version = 1)
        GiveUp.recordBlocklisted(store, "sha-b", version = 1)
        GiveUp.recordBlocklisted(store, "sha-c", version = 1)
        assertTrue("same version sees the give-up", GiveUp.isDisabled(store, version = 1))
        assertFalse("a newer lib ignores an older give-up and retries", GiveUp.isDisabled(store, version = 2))
        // A blocklist under the newer version (set already at threshold) re-confirms the give-up at that version.
        GiveUp.recordBlocklisted(store, "sha-a", version = 2)
        assertTrue(GiveUp.isDisabled(store, version = 2))
    }

    // ---- OK crash backstop ----

    @Test
    fun `only a native crash demotes a confirmed-OK build, clean resets and unknown leaves`() {
        assertEquals("a clean launch resets the count", OkBackstop.RESET, okBackstop(ExitKind.CLEAN, newCount = 5, threshold = 2))
        assertEquals("an unknown exit leaves the count untouched", OkBackstop.LEAVE, okBackstop(ExitKind.UNKNOWN, newCount = 5, threshold = 2))
        assertEquals("one post-OK crash only counts", OkBackstop.COUNT, okBackstop(ExitKind.CRASH, newCount = 1, threshold = 2))
        assertEquals("the threshold-th consecutive crash demotes", OkBackstop.DEMOTE, okBackstop(ExitKind.CRASH, newCount = 2, threshold = 2))
    }

    // ---- host support gate ----

    @Test
    fun `host support needs ART, a readable maps and an arm ABI`() {
        assertTrue(hostSupported("Dalvik", "aarch64", mapsReadable = true))
        assertTrue(hostSupported("Dalvik", "armv7l", mapsReadable = true))
        assertFalse("desktop JVM (Suwayomi/KCEF host)", hostSupported("OpenJDK 64-Bit Server VM", "amd64", mapsReadable = true))
        assertFalse("x86 emulator", hostSupported("Dalvik", "x86_64", mapsReadable = true))
        assertFalse("no /proc", hostSupported("Dalvik", "aarch64", mapsReadable = false))
        assertFalse("null signals", hostSupported(null, null, mapsReadable = true))
    }
}
