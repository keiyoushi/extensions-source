package keiyoushi.runtime.webviewpatch

import android.util.Log
import keiyoushi.utils.decodeHex
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The per-sha survival state machine that drops WebView's `X-Requested-With` header by an in-process patch
 * of the shared WebView library. All orchestration lives here, over three seams — [NativeOps] (the native
 * memory / locate / self-test / exit-reason / clock side effects), [KvStore] (the persistent per-sha store)
 * and [SharedState] (the process-wide coordination that survives a runtime hand-over) — so it runs off-device
 * against fakes. The Android entry [XrwStripper] builds the real seams and delegates here.
 *
 * The Keiyoushi Runtime is the one copy of this code in the app's process, and [Entry] single-flights the
 * apply so this engine never runs concurrently with itself. It coordinates through the shared in-process
 * record ([shared]) and a persistent store keyed by the library's sha256, so a newer runtime version adopts a
 * patch an older one applied over an in-place update; the state machine keeps a build that crashes a real run
 * from being re-applied on every launch.
 */
internal class XrwStripEngine(
    private val ops: NativeOps,
    kv: KvStore,
    private val shared: SharedState,
    private val version: Int = LOCATOR_VERSION,
) {
    private val store = Store(kv)

    /**
     * Locates and patches the per-request adder for the mapped WebView library. Returns [XrwStripper.Outcome.SETTLED]
     * once there is nothing more to do this run (patched, blocklisted, unsupported, or already applied), or
     * [XrwStripper.Outcome.NO_LIB] when the library isn't mapped yet (cold process). Never throws beyond
     * cancellation. The process lock is held only for brief coordination sections, never across the off-main
     * file reads or the loopback self-test.
     */
    suspend fun doPatch(): XrwStripper.Outcome {
        // Per-device give-up checked FIRST, before any locate/patch: a device that gave up locally reports
        // failed and never patches here again.
        if (GiveUp.isDisabled(store, version)) {
            Log.i(TAG, "code=patch_disabled")
            shared.setLastStatus("failed:patch disabled on this device")
            return XrwStripper.Outcome.SETTLED
        }

        // The patch is already live in this process (adopted over a runtime hand-over): nothing more to do.
        if (fastPathApplied()) return XrwStripper.Outcome.SETTLED

        val lib = ops.findLib() ?: run {
            return XrwStripper.Outcome.NO_LIB
        }

        val cheapKey = ops.cheapKey(lib)
        // An unsupported ABI (x86/x86_64/other) is a fixed property of this build: settle so later runs
        // don't rescan /proc/self/maps every time. The locators are arm-only and never claim it anyway.
        if (lib.abi != "arm64" && lib.abi != "arm32") {
            Log.i(TAG, "code=unsupported_abi abi=${lib.abi}")
            shared.setLastStatus("unsupported:WebView ${lib.abi} not covered")
            shared.markSettled(cheapKey, version)
            return XrwStripper.Outcome.SETTLED
        }
        // Cheap identity (path + size + mtime), no file read. A lib this process already settled needs
        // nothing more; otherwise a persisted cheap-key -> sha mapping gives the sha without reading or
        // hashing the ~150 MB .so.
        if (shared.isSettled(cheapKey, version)) {
            return XrwStripper.Outcome.SETTLED
        }

        // Resolve the sha. A cache miss must map the .so to hash it (off-heap); a hit skips the read.
        val libKey = LIB_PREFIX + cheapKey
        val knownSha = store.get(libKey)
        val mapped = if (knownSha == null) {
            ops.openLib(lib) ?: run {
                Log.i(TAG, "code=no_so")
                return XrwStripper.Outcome.NO_LIB
            }
        } else {
            null
        }
        val sha = knownSha ?: mapped!!.sha256().also { store.put(libKey, it) }

        val state = store.get(SHA_PREFIX + sha)
        val action = dispatchAction(state)

        // Only a fresh attempt or a recovery needs the .so located; map it (once) and RELEASE it before
        // any self-test so the ~150 MB mapping isn't held through the 5.5 s loopback window.
        val result = if (action == Action.ATTEMPT || action == Action.RECOVER) {
            val m = mapped ?: ops.openLib(lib)
            if (m == null) {
                return XrwStripper.Outcome.NO_LIB
            }
            try {
                ops.locate(m.source)
            } finally {
                m.close()
            }
        } else {
            mapped?.close()
            null
        }

        when (action) {
            Action.ATTEMPT -> attempt(lib, sha, result!!)
            Action.RECOVER -> recover(lib, sha, result!!)
            Action.CACHED_OK -> cachedApply(lib, sha, state!!)
            Action.CORRUPT -> {
                Log.w(TAG, "code=cache_corrupt")
                shared.setLastStatus("failed:unreadable stored state")
            }
            // A terminal cached state (blocklisted / nomatch / inconclusive) was logged when first reached.
            Action.NOTHING -> shared.setLastStatus(terminalStatus(stateName(state!!), lib.abi, sha))
        }
        shared.markSettled(cheapKey, version)
        return XrwStripper.Outcome.SETTLED
    }

    /**
     * Promotes the pending sha to OK and clears its crash-count. A no-op unless this process applied a patch
     * this run. The sha is pending only after the self-test's in-process load stripped cleanly, and reaching
     * here means the process survived from the patch application to now with the run that hosted the attempt
     * already ended — OK records that survival, not that a later load exercised the patch (a cancelled or
     * timed-out run still promotes).
     */
    fun markRunSurvived() {
        val (sha, okValue) = shared.takePending() ?: return
        store.promote(sha, okValue, ops.now())
        Log.i(TAG, "code=survived_ok sha=${sha.take(8)}")
    }

    private enum class Action { ATTEMPT, RECOVER, CACHED_OK, CORRUPT, NOTHING }

    /**
     * Maps a stored per-sha state to the launch's action. A negative state ([NOMATCH]/[INCONCLUSIVE]/
     * [BLOCKLISTED]) stamped by an older locator version is stale and re-attempted when a newer runtime
     * release may now handle it; an unknown/newer-format value is treated as "don't patch"
     * ([Action.CORRUPT]), so a runtime reading a value a newer runtime release left stays fail-safe.
     * An `ok:` record is plain data applied as-is ([Action.CACHED_OK]); there is no second copy to
     * supersede it.
     */
    private fun dispatchAction(state: String?): Action = when {
        state == null -> Action.ATTEMPT
        stateName(state) == BLOCKLISTED -> if (negativeStateStale(state, version)) Action.ATTEMPT else Action.NOTHING
        stateName(state) == NOMATCH -> if (negativeStateStale(state, version)) Action.ATTEMPT else Action.NOTHING
        stateName(state) == INCONCLUSIVE -> if (negativeStateStale(state, version)) Action.ATTEMPT else Action.NOTHING
        stateName(state) == ATTEMPTING || stateName(state) == UNCONFIRMED ->
            if (stateStamp(state) <= version) Action.RECOVER else Action.CORRUPT
        state.startsWith(OK_PREFIX) -> if (OkPatch.parse(state) == null) Action.CORRUPT else Action.CACHED_OK
        else -> Action.CORRUPT
    }

    /** Fast path: this process already patched its WebView lib (possibly under an earlier runtime version), and the live bytes still match. */
    private fun fastPathApplied(): Boolean {
        if (shared.appliedLive(ops::readMem)) return true
        shared.clearApplied()
        return false
    }

    /**
     * A non-terminal marker from a prior launch means a patch was written but no real run confirmed it. Decide
     * via [recoverAction]: ADOPT a still-live patch (a cancelled run, not a crash — never counted), blocklist a
     * real native crasher on the first strike, re-attempt a clean kill without counting, else fall to the count
     * path (blocklist at the crash-count threshold, else re-attempt and count).
     */
    private suspend fun recover(
        lib: NativeMemory.MappedLib,
        sha: String,
        result: LocateResult,
    ) {
        val adderVa = result.adder
        val verifyVa = result.verifyVa
        val verifyHex = result.verifyHex
        val patchHex = result.patchHex
        if (adderVa == null || verifyVa == null || verifyHex == null || patchHex == null) {
            // The strategy no longer claims this build; can't safely adopt or retry.
            store.put(SHA_PREFIX + sha, stampState(NOMATCH, version))
            Log.i(TAG, "code=recover_nomatch sha=${sha.take(8)} reason=${result.reason}")
            shared.setLastStatus(unsupportedBuild(lib.abi, sha))
            return
        }

        val liveBase = lib.loadBase + adderVa
        // A patch still live in THIS process is a cancelled attempt (the process never died), not a crash.
        val patchLive = alreadyPatched(liveBase, patchHex)
        val markerTime = store.get(MTIME_PREFIX + sha)?.toLongOrNull()
        val exit = if (markerTime != null) ops.exitSince(markerTime) else ExitKind.UNKNOWN
        val prospectiveCount = (store.crashCount(sha)) + 1

        when (recoverAction(patchLive, exit, prospectiveCount, CRASH_THRESHOLD)) {
            RecoverAction.ADOPT -> {
                // Adopt the live patch and hold it UNCONFIRMED; a surviving run promotes it to OK as usual.
                shared.markApplied(liveBase, patchHex)
                shared.setPending(sha, okValue(adderVa, verifyVa, verifyHex, patchHex))
                store.put(SHA_PREFIX + sha, stampState(UNCONFIRMED, version))
                shared.setLastStatus("applied")
            }
            // One state change, two diagnoses: an OS-recorded native crash of the main process, or an
            // unattributable exit whose crash-count reached the threshold.
            RecoverAction.NATIVE_BLOCKLIST, RecoverAction.COUNT_BLOCKLIST -> {
                blocklist(sha)
                shared.setLastStatus("failed:build crashed a run and is blocklisted")
                if (exit == ExitKind.CRASH) {
                    Log.w(TAG, "code=native_crash_blocklist sha=${sha.take(8)}")
                } else {
                    Log.w(TAG, "code=crash_blocklist sha=${sha.take(8)} count=$prospectiveCount")
                }
            }
            // A clean kill (user swipe-away before the attempt confirmed) is not the patch's fault: re-attempt
            // WITHOUT bumping the crash-count, and drop any leftover count, so two unattributable exits far apart
            // in time can't accumulate toward the blocklist. A patch-induced crash is never classified CLEAN.
            RecoverAction.RETRY_CLEAN -> {
                store.resetCrash(sha)
                attempt(lib, sha, result)
            }
            RecoverAction.RETRY -> {
                store.bumpCrash(sha)
                attempt(lib, sha, result)
            }
        }
    }

    private fun blocklist(sha: String) {
        // One atomic save for the whole state change (sha -> blocklisted, crash-count + marker dropped).
        store.setBlocklisted(sha)
        // A blocklisted sha counts toward the per-device give-up. Reached from the crash / write-ahead path
        // ([recover]), the self-test-failed revert and a refused write ([patchAndSelfTest]), so every
        // blocklist — however it happened — feeds the distinct-sha set.
        GiveUp.recordBlocklisted(store, sha, version)
    }

    /** A never-seen sha (or a retry): locate, re-verify, patch, self-test, and leave it UNCONFIRMED awaiting survival. */
    private suspend fun attempt(
        lib: NativeMemory.MappedLib,
        sha: String,
        result: LocateResult,
    ) {
        val adderVa = result.adder
        val verifyVa = result.verifyVa
        val verifyHex = result.verifyHex
        val patchHex = result.patchHex
        if (adderVa == null || verifyVa == null || verifyHex == null || patchHex == null) {
            store.put(SHA_PREFIX + sha, stampState(NOMATCH, version))
            Log.i(TAG, "code=nomatch abi=${lib.abi} reason=${result.reason}")
            shared.setLastStatus(unsupportedBuild(lib.abi, sha))
            return
        }

        val liveBase = lib.loadBase + adderVa
        // The spot is already patched (an earlier runtime version in this process wrote it): adopt it in-process.
        if (alreadyPatched(liveBase, patchHex)) {
            shared.markApplied(liveBase, patchHex)
            shared.setLastStatus("applied")
            return
        }

        // The verify window can start before the patch offset (NOP era: patch the inner bl, verify the
        // bytes leading up to it), so read it at verifyVa and write the patch at the adder.
        val verifyBytes = verifyHex.decodeHex()
        val onDevice = ops.readMem(lib.loadBase + verifyVa, verifyBytes.size)
        if (onDevice == null || !onDevice.contentEquals(verifyBytes)) {
            Log.w(TAG, "code=verify_mismatch sha=${sha.take(8)}")
            shared.setLastStatus("failed:verify window did not match")
            store.put(SHA_PREFIX + sha, stampState(NOMATCH, version))
            return
        }

        patchAndSelfTest(sha, liveBase, adderVa, verifyVa, verifyHex, patchHex)
    }

    /**
     * OK fast path: re-verify the window and re-apply the cached patch. No re-locate, no self-test, no marker
     * churn. Also the crash backstop for a build that only starts crashing AFTER it was OK'd: if the previous
     * launch's OK apply was followed by a main-process native crash, count it, and demote the build to
     * blocklisted at [OK_CRASH_THRESHOLD] consecutive such launches; a clean launch resets the count.
     */
    private fun cachedApply(lib: NativeMemory.MappedLib, sha: String, state: String) {
        // API < 30 has no exit reasons, so a post-promotion crash is unattributable and this backstop can't run: an
        // OK build that only starts crashing on a later launch is re-applied every launch with no demotion, and it
        // never feeds the per-device give-up. The UNCONFIRMED-phase crash-count still guards the initial risky window
        // on every API; the gap is only the post-promotion case on API < 30, and it is narrow (a build that passed
        // the self-test AND survived a real run, then crashes on a rarer later path). A write-ahead marker around
        // this re-apply would close it without exit reasons, but with no way to tell a crash from a clean kill the
        // marker would count swipe-aways as strikes and blocklist good builds — the gap is the cheaper side of that
        // trade, and it is accepted deliberately.
        if (ops.exitReasonsAvailable()) {
            val priorApply = store.get(OKAPPLY_PREFIX + sha)?.toLongOrNull()
            val exit = if (priorApply != null) ops.exitSince(priorApply) else ExitKind.UNKNOWN
            // Only a native crash bumps the post-OK count; a clean launch resets it, an unknown one leaves it.
            val newCount = if (exit == ExitKind.CRASH) store.bumpOkCrash(sha) else 0
            when (okBackstop(exit, newCount, OK_CRASH_THRESHOLD)) {
                OkBackstop.DEMOTE -> {
                    blocklist(sha)
                    Log.w(TAG, "code=ok_demote_blocklist sha=${sha.take(8)}")
                    shared.setLastStatus("failed:build crashed after it was confirmed; blocklisted")
                    return
                }
                OkBackstop.RESET -> store.resetOkCrash(sha)
                OkBackstop.LEAVE -> Unit
            }
        }

        val record = OkPatch.parse(state) ?: return
        val adderVa = record.adderVa
        val verifyVa = record.verifyVa
        val verifyHex = record.verifyHex
        val patchHex = record.patchHex
        val live = lib.loadBase + adderVa

        if (alreadyPatched(live, patchHex)) {
            shared.markApplied(live, patchHex)
            store.putOkApply(sha, ops.now())
            shared.setLastStatus("applied")
            return
        }
        val verifyBytes = verifyHex.decodeHex()
        val onDevice = ops.readMem(lib.loadBase + verifyVa, verifyBytes.size)
        if (onDevice == null || !onDevice.contentEquals(verifyBytes)) {
            Log.w(TAG, "code=cache_verify_mismatch sha=${sha.take(8)}")
            shared.setLastStatus("failed:verify window did not match on re-apply")
            return
        }
        val patchBytes = patchHex.decodeHex()
        // The revert image is the patch-sized bytes AT the patch site, never the verify window: verifyVa sits
        // before adderVa on the inlined and mode eras, so writing the window back at the site would shift
        // executing code. Without a revert image the write is not attempted at all.
        val original = ops.readMem(live, patchBytes.size) ?: run {
            Log.w(TAG, "code=cached_orig_read_failed sha=${sha.take(8)}")
            shared.setLastStatus("failed:could not read the original bytes")
            return
        }
        if (ops.writeMem(live, patchBytes)) {
            shared.markApplied(live, patchHex)
            store.putOkApply(sha, ops.now())
            Log.i(TAG, "code=cached_applied sha=${sha.take(8)}")
            shared.setLastStatus("applied")
        } else {
            ops.writeMem(live, original) // restore if the write was partial
            Log.w(TAG, "code=cached_write_failed sha=${sha.take(8)}")
            shared.setLastStatus("failed:kernel refused the write")
        }
    }

    /**
     * The write-to-verdict section, run in a [NonCancellable] scope: once the write-ahead marker is down and
     * the patch is in memory, the self-test must reach a verdict even if the run that launched this is
     * cancelled. A cancellation here would otherwise leave the patch live with the store stuck at its
     * `attempting` marker, which the next run would mistake for a crash and eventually blocklist a GOOD
     * build. The self-test stays off the main thread (NonCancellable overrides only the Job, not the IO
     * dispatcher).
     */
    private suspend fun patchAndSelfTest(
        sha: String,
        live: Long,
        adderVa: Long,
        verifyVa: Long,
        verifyHex: String,
        patchHex: String,
    ) = withContext(NonCancellable) {
        val patchBytes = patchHex.decodeHex()
        val original = ops.readMem(live, patchBytes.size) ?: run {
            Log.w(TAG, "code=orig_read_failed")
            shared.setLastStatus("failed:could not read the original bytes")
            return@withContext
        }

        // Write-ahead marker + its set-time: durable, before the risky write. A process death from here on
        // leaves these behind; the time lets the next launch tie a recorded native crash to this attempt.
        // The marker covers the self-test window too, so nothing else is written until a verdict exists.
        store.putAttemptMarker(sha, ops.now())

        if (!ops.writeMem(live, patchBytes)) {
            // Revert to the saved bytes so a partial write can't corrupt the adder. A refused write (a kernel
            // that denies /proc/self/mem, e.g. hardened proc_mem) is deterministic for this build, not an
            // ambiguous crash, so give up on this sha AT ONCE — blocklist, no crash-count detour.
            val reverted = ops.writeMem(live, original)
            blocklist(sha)
            Log.w(TAG, "code=write_failed sha=${sha.take(8)} reverted=$reverted")
            shared.setLastStatus("failed:kernel refused the write")
            return@withContext
        }

        when (ops.selfTest()) {
            LoopbackProbe.Result.STRIPPED -> {
                // Self-test passed; hold it UNCONFIRMED until a real run survives (markRunSurvived promotes to OK).
                shared.markApplied(live, patchHex)
                shared.setPending(sha, okValue(adderVa, verifyVa, verifyHex, patchHex))
                store.put(SHA_PREFIX + sha, stampState(UNCONFIRMED, version))
                Log.i(TAG, "code=patched_stripped sha=${sha.take(8)}")
                shared.setLastStatus("applied")
            }
            LoopbackProbe.Result.PRESENT -> {
                ops.writeMem(live, original)
                blocklist(sha)
                Log.w(TAG, "code=selftest_present_reverted sha=${sha.take(8)}")
                shared.setLastStatus("failed:self-test showed no change")
            }
            LoopbackProbe.Result.INCONCLUSIVE -> {
                // Can't conclude (e.g. cleartext to loopback blocked). Revert and cache the back-off so the next
                // launch doesn't re-read the .so and re-locate every time; this is environmental, not a crash.
                ops.writeMem(live, original)
                store.put(SHA_PREFIX + sha, stampState(INCONCLUSIVE, version))
                store.remove(MTIME_PREFIX + sha)
                Log.w(TAG, "code=selftest_inconclusive_reverted sha=${sha.take(8)}")
                shared.setLastStatus("failed:self-test could not conclude")
            }
        }
    }

    private fun alreadyPatched(live: Long, patchHex: String): Boolean {
        val byteLen = patchHex.length / 2
        return ops.readMem(live, byteLen)?.sliceHex(0, byteLen) == patchHex
    }

    private fun okValue(adderVa: Long, verifyVa: Long, verifyHex: String, patchHex: String) = OkPatch(adderVa, verifyVa, verifyHex, patchHex).encode()

    /** The `unsupported:` status naming this WebView build and ABI, so the user's error can say which. */
    private fun unsupportedBuild(abi: String, sha: String) = "unsupported:WebView $abi build ${sha.take(8)} not covered yet"

    /** The status for a terminal cached state re-read at launch (nothing new attempted). */
    private fun terminalStatus(name: String, abi: String, sha: String): String = when (name) {
        NOMATCH -> unsupportedBuild(abi, sha)
        BLOCKLISTED -> "failed:WebView $abi build ${sha.take(8)} is blocklisted after an earlier failure"
        INCONCLUSIVE -> "failed:self-test could not conclude on an earlier launch"
        else -> "failed:not applied"
    }

    /**
     * Per-sha survival store helpers over a [KvStore] backing (the app's `filesDir` file on device, an
     * in-memory map in tests). Delegates the plain [KvStore] surface and adds the atomic multi-key edits
     * (blocklist, promote, write-ahead marker, crash-count bumps) the state machine needs.
     */
    private inner class Store(private val backing: KvStore) : KvStore {
        override fun get(key: String): String? = backing.get(key)

        override fun put(key: String, value: String) = backing.put(key, value)

        override fun remove(key: String) = backing.remove(key)

        override fun transact(block: KvStore.Txn.() -> Unit) = backing.transact(block)

        /** Write-ahead marker + its set-time in one durable save, so a crash can't leave one without the other. */
        fun putAttemptMarker(sha: String, timeMs: Long) = backing.transact {
            set(SHA_PREFIX + sha, stampState(ATTEMPTING, version))
            set(MTIME_PREFIX + sha, timeMs.toString())
        }

        /** Blocklist a sha (version-stamped) and drop its crash-count + markers in one atomic save. */
        fun setBlocklisted(sha: String) = backing.transact {
            set(SHA_PREFIX + sha, stampState(BLOCKLISTED, version))
            remove(CRASH_PREFIX + sha)
            remove(MTIME_PREFIX + sha)
            remove(OKAPPLY_PREFIX + sha)
            remove(OKCRASH_PREFIX + sha)
        }

        /**
         * Promote a sha to its OK value, clearing its crash-count + attempt marker and SETTING the ok-apply
         * time to [nowMs] in one atomic save, so a same-session crash right after the promotion has a baseline
         * for the next launch's backstop (removing it left that crash unattributable).
         */
        fun promote(sha: String, okValue: String, nowMs: Long) = backing.transact {
            set(SHA_PREFIX + sha, okValue)
            remove(CRASH_PREFIX + sha)
            remove(MTIME_PREFIX + sha)
            set(OKAPPLY_PREFIX + sha, nowMs.toString())
            remove(OKCRASH_PREFIX + sha)
        }

        /** Record the wall-clock time this OK build was (re)applied, for the next launch's crash backstop. */
        fun putOkApply(sha: String, timeMs: Long) = backing.put(OKAPPLY_PREFIX + sha, timeMs.toString())

        /** Increment and return the consecutive post-OK-apply native-crash count, in one atomic read-modify-write. */
        fun bumpOkCrash(sha: String): Int {
            var next = 0
            backing.transact {
                next = (get(OKCRASH_PREFIX + sha)?.toIntOrNull() ?: 0) + 1
                set(OKCRASH_PREFIX + sha, next.toString())
            }
            return next
        }

        /** Clear the post-OK-apply crash count (a clean launch), only writing if it was set. */
        fun resetOkCrash(sha: String) {
            if (backing.get(OKCRASH_PREFIX + sha) != null) backing.remove(OKCRASH_PREFIX + sha)
        }

        /** The current per-sha crash-count (0 if unset), without mutating it. */
        fun crashCount(sha: String): Int = backing.get(CRASH_PREFIX + sha)?.toIntOrNull() ?: 0

        /** Clear the per-sha crash-count (an attributable clean end), only writing if it was set. */
        fun resetCrash(sha: String) {
            if (backing.get(CRASH_PREFIX + sha) != null) backing.remove(CRASH_PREFIX + sha)
        }

        /** Increments the per-sha crash-count and returns the new value, in one atomic read-modify-write. */
        fun bumpCrash(sha: String): Int {
            var next = 0
            backing.transact {
                next = (get(CRASH_PREFIX + sha)?.toIntOrNull() ?: 0) + 1
                set(CRASH_PREFIX + sha, next.toString())
            }
            return next
        }
    }

    companion object {
        private const val TAG = "XrwStripper"

        const val SHA_PREFIX = "sha."
        const val CRASH_PREFIX = "crash."
        const val MTIME_PREFIX = "mtime." // wall-clock ms the write-ahead marker was set, for exit-reason attribution
        const val LIB_PREFIX = "lib." // cheap lib-identity key -> sha, so a known build needs no .so read/hash
        const val OKAPPLY_PREFIX = "okapply." // wall-clock ms cachedApply last applied this OK build, for the crash backstop
        const val OKCRASH_PREFIX = "okcrash." // consecutive launches an OK apply was followed by a native crash

        // Per-sha state. The first two are non-terminal survival markers: finding one at launch means a
        // prior launch wrote the patch but the process died before a real run confirmed it survived.
        const val ATTEMPTING = "attempting" // written just before the /proc/self/mem write
        const val UNCONFIRMED = "unconfirmed" // self-test passed, awaiting survival of a real run
        const val BLOCKLISTED = "blocklisted" // crashed/failed enough times: never patch this build again
        const val NOMATCH = "nomatch" // locator found no clean adder
        const val INCONCLUSIVE = "inconclusive" // self-test could not conclude (e.g. cleartext blocked); back off
        const val OK_PREFIX = "ok" // survived a real run; an [OkPatch] record

        // Blocklist once the crash-count reaches this. Only UNKNOWN (unattributable) retries bump the count: a
        // native crash blocklists on the first strike and a clean kill is retried without counting, so the
        // count-of-2 covers just API < 30 or an exit reason that is unavailable or not attributable, letting one
        // such ambiguous exit be retried (and promoted if it survives) while an every-run crasher is still stopped.
        const val CRASH_THRESHOLD = 2

        // Demote a confirmed-OK build to blocklisted after this many consecutive launches whose prior OK apply was
        // followed by a main-process native crash. 2 lets one unrelated native crash in the window pass.
        const val OK_CRASH_THRESHOLD = 2
    }
}

/** Whether this process holds an applied patch whose bytes are still live. */
internal fun SharedState.appliedLive(readMem: (Long, Int) -> ByteArray?): Boolean {
    val address = fastPathAddr()?.toLong(16) ?: return false
    val patch = fastPathPatch() ?: return false
    val byteLen = patch.length / 2
    return readMem(address, byteLen)?.sliceHex(0, byteLen) == patch
}
