package keiyoushi.runtime.webviewpatch

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

/**
 * The process-wide single-flight behind [Entry.apply] for `"requested-with"`: one attempt per process, shared by
 * every caller and across a runtime update, started atomically and awaited (not owned) by each caller. Exercises
 * [sharedAttempt] and [awaitShared] directly with an injected attempt function, so no Android is needed.
 */
class SingleFlightApplyTest {

    private val key = "webviewpatch.requested-with.inflight"

    // Entry runs the attempt in its own detached scope; a separate scope here stands in for it, so cancelling a
    // caller (a child of runBlocking) can't reach the attempt.
    private val attemptScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() {
        attemptScope.cancel()
        // RuntimeHost.state lives in System properties under this key; keep tests from leaking into each other.
        System.getProperties().remove("keiyoushi.runtime.state")
    }

    @Test
    fun `ten concurrent callers run the attempt once and all get the same status`() {
        val state = ConcurrentHashMap<String, Any>()
        val runs = AtomicInteger(0)
        val entered = AtomicInteger(0)
        val allIn = CompletableDeferred<Unit>()
        // The lone attempt only finishes once all ten callers have reached the map, so they genuinely race.
        val attempt: suspend () -> String = {
            runs.incrementAndGet()
            allIn.await()
            "applied"
        }

        val results = runBlocking {
            val callers = (1..10).map {
                async(Dispatchers.Default) {
                    val d = sharedAttempt(state, key, attemptScope, attempt)
                    if (entered.incrementAndGet() == 10) allIn.complete(Unit)
                    awaitShared(state, key, d)
                }
            }
            withTimeoutOrNull(10_000L) { callers.map { it.await() } }
        }

        assertNotNull("the callers all settled", results)
        assertEquals("the attempt ran exactly once", 1, runs.get())
        assertEquals(List(10) { "applied" }, results)
    }

    @Test
    fun `a caller arriving after an applied completion starts no new attempt`() {
        val state = ConcurrentHashMap<String, Any>()
        val runs = AtomicInteger(0)
        val attempt: suspend () -> String = { runs.incrementAndGet(); "applied" }

        runBlocking {
            val first = awaitShared(state, key, sharedAttempt(state, key, attemptScope, attempt))
            assertEquals("applied", first)

            // The applied Deferred is kept under the key; the next caller adopts it and never re-runs.
            val again = sharedAttempt(state, key, attemptScope, attempt)
            assertEquals("applied", awaitShared(state, key, again))
        }

        assertEquals("applied is adopted, not re-attempted", 1, runs.get())
    }

    @Test
    fun `cancelling one waiting caller leaves the shared attempt to finish for the others`() {
        val state = ConcurrentHashMap<String, Any>()
        val runs = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val attempt: suspend () -> String = {
            runs.incrementAndGet()
            started.complete(Unit)
            release.await()
            "applied"
        }

        runBlocking {
            val shared = sharedAttempt(state, key, attemptScope, attempt)
            val survivor = async(Dispatchers.Default) { awaitShared(state, key, shared) }
            val doomed = async(Dispatchers.Default) { awaitShared(state, key, shared) }

            // Both are parked on await; cancel one, then let the attempt finish.
            started.await()
            doomed.cancel()
            release.complete(Unit)

            assertEquals("the survivor still gets the result", "applied", survivor.await())
            assertTrue("the shared attempt completed, not cancelled", shared.isCompleted)
            assertFalse(shared.isCancelled)
        }

        assertEquals("the attempt ran once despite the cancellation", 1, runs.get())
    }

    @Test
    fun `a non-applied result is dropped so the next call starts a fresh attempt`() {
        val state = ConcurrentHashMap<String, Any>()
        val runs = AtomicInteger(0)
        val attempt: suspend () -> String = { "failed:attempt ${runs.incrementAndGet()}" }

        runBlocking {
            val first = awaitShared(state, key, sharedAttempt(state, key, attemptScope, attempt))
            assertEquals("failed:attempt 1", first)
            assertFalse("a non-applied attempt is removed from state", state.containsKey(key))

            val second = awaitShared(state, key, sharedAttempt(state, key, attemptScope, attempt))
            assertEquals("failed:attempt 2", second)
        }

        assertEquals("the later call started a new attempt", 2, runs.get())
    }

    @Test
    fun `every waiter cancelled then a failed finish clears the key so the next call retries`() {
        val state = ConcurrentHashMap<String, Any>()
        val runs = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val attempt: suspend () -> String = {
            val n = runs.incrementAndGet()
            started.complete(Unit)
            release.await()
            "failed:attempt $n"
        }

        val second = runBlocking {
            val shared = sharedAttempt(state, key, attemptScope, attempt)
            val a = async(Dispatchers.Default) { awaitShared(state, key, shared) }
            val b = async(Dispatchers.Default) { awaitShared(state, key, shared) }

            // Both waiters give up before the detached attempt finishes.
            started.await()
            a.cancel()
            b.cancel()
            // Now let it finish non-applied: its own completion hook, not a waiter, must drop the entry.
            release.complete(Unit)
            shared.await()
            withTimeoutOrNull(10_000L) { while (state.containsKey(key)) yield() }
            assertFalse("the finished failed attempt is cleared by its own hook", state.containsKey(key))

            awaitShared(state, key, sharedAttempt(state, key, attemptScope, attempt))
        }

        assertEquals("the next call started a fresh attempt", 2, runs.get())
        assertEquals("failed:attempt 2", second)
    }

    @Test
    fun `a cancelled attempt settles an active caller as failed clears the key and retries`() {
        val state = ConcurrentHashMap<String, Any>()
        val runs = AtomicInteger(0)
        val started = CompletableDeferred<Unit>()
        // Parks until the attempt Deferred itself is cancelled (not the caller). Models a timeout added inside
        // the attempt one day: the attempt ends cancelled while a caller still waits on it.
        val attempt: suspend () -> String = {
            runs.incrementAndGet()
            started.complete(Unit)
            awaitCancellation()
        }

        val status = runBlocking {
            val shared = sharedAttempt(state, key, attemptScope, attempt)
            val caller = async(Dispatchers.Default) { awaitShared(state, key, shared) }
            started.await()
            shared.cancel()
            val result = caller.await()

            withTimeoutOrNull(10_000L) { while (state.containsKey(key)) yield() }
            assertFalse("the cancelled attempt is cleared by its own hook", state.containsKey(key))

            val retry = awaitShared(state, key, sharedAttempt(state, key, attemptScope) { "failed:retry" })
            assertEquals("the next call started a fresh attempt", "failed:retry", retry)
            result
        }

        assertTrue("an active caller sees a failure, not a CancellationException", status.startsWith("failed:"))
        assertEquals("the cancelled attempt ran once", 1, runs.get())
    }

    @Test
    fun `an attempt that throws settles every caller as failed and the next call retries`() {
        val state = ConcurrentHashMap<String, Any>()
        val runs = AtomicInteger(0)
        // A non-cancellation throw inside the attempt body: the stored Deferred must still complete with a
        // "failed:" status (not exceptionally), so every waiter gets it and the entry is dropped for a retry.
        val attempt: suspend () -> String = {
            runs.incrementAndGet()
            throw RuntimeException("boom")
        }

        val results = runBlocking {
            val shared = sharedAttempt(state, key, attemptScope, attempt)
            val callers = (1..5).map { async(Dispatchers.Default) { awaitShared(state, key, shared) } }
            withTimeoutOrNull(10_000L) { callers.map { it.await() } }
        }

        assertEquals("every waiting caller gets the mapped failure", List(5) { "failed:boom" }, results)
        assertFalse("the thrown attempt is dropped from state", state.containsKey(key))

        val second = runBlocking { awaitShared(state, key, sharedAttempt(state, key, attemptScope, attempt)) }
        assertEquals("the retry runs and reports its own failure", "failed:boom", second)
        assertEquals("the later call started a fresh attempt", 2, runs.get())
    }

    @Test
    fun `a throw with no message falls back to the exception class name`() {
        val state = ConcurrentHashMap<String, Any>()
        val attempt: suspend () -> String = { throw IllegalStateException() }

        val status = runBlocking { awaitShared(state, key, sharedAttempt(state, key, attemptScope, attempt)) }

        assertEquals("failed:IllegalStateException", status)
    }

    @Test
    fun `a pre-existing Deferred under the key is awaited and starts no new attempt`() {
        val state = ConcurrentHashMap<String, Any>()
        val runs = AtomicInteger(0)
        // An older runtime version left its in-flight apply here; the value is an app-loaded Deferred<String>.
        val older: Deferred<String> = attemptScope.async { "applied" }
        state[key] = older

        val attempt: suspend () -> String = { runs.incrementAndGet(); "applied" }

        runBlocking {
            val adopted = sharedAttempt(state, key, attemptScope, attempt)
            assertSame("the older runtime's Deferred is adopted", older, adopted)
            assertEquals("applied", awaitShared(state, key, adopted))
        }

        assertEquals("no second attempt while one is in flight", 0, runs.get())
    }

    // Models XrwStripper.markRunSurvived → takePending: promotion fires at most once. Each test's promote closes
    // over one of these, so repeated would-be promotions (several runs ending, or a run plus the grace path)
    // collapse to a single recorded promotion the way the real atomic take does.
    private class OncePromote {
        val count = AtomicInteger(0)
        private val pending = AtomicBoolean(true)
        val promote: () -> Unit = { if (pending.compareAndSet(true, false)) count.incrementAndGet() }
    }

    // (a) A is cancelled before the attempt completes, B is still active: nothing promotes at completion, and B
    // alone promotes — exactly once — when it ends. The run that had already ended never promotes.
    @Test
    fun `a cancelled waiter does not promote, an active waiter promotes when it ends`() {
        val waiters = ConcurrentHashMap.newKeySet<Job>()
        val a = Job()
        val b = Job()
        waiters.add(a)
        waiters.add(b)
        a.cancel()

        val attempt = CompletableDeferred<String>()
        val once = OncePromote()
        promoteOnAttemptCompletion(attempt, waiters, attemptScope, 50.milliseconds, { true }, once.promote)

        attempt.complete("applied")
        assertEquals("nothing promotes while B is still active", 0, once.count.get())

        b.complete()
        assertEquals("B promotes exactly once when it ends", 1, once.count.get())
    }

    // (b) A lone caller is cancelled before completion: no run is active at completion, so nothing promotes then;
    // the grace path (injected short here) promotes once after it elapses.
    @Test
    fun `a lone cancelled waiter promotes only after the grace period`() {
        val waiters = ConcurrentHashMap.newKeySet<Job>()
        val a = Job()
        waiters.add(a)
        a.cancel()

        val attempt = CompletableDeferred<String>()
        val once = OncePromote()

        runBlocking {
            promoteOnAttemptCompletion(attempt, waiters, attemptScope, 80.milliseconds, { true }, once.promote)
            attempt.complete("applied")
            assertEquals("no promotion before the grace elapses", 0, once.count.get())
            withTimeoutOrNull(5_000L) { while (once.count.get() == 0) yield() }
        }

        assertEquals("the grace path promotes once", 1, once.count.get())
    }

    // (c) A single still-active caller promotes when its own run completes, never before.
    @Test
    fun `a lone active waiter promotes when its run completes`() {
        val waiters = ConcurrentHashMap.newKeySet<Job>()
        val a = Job()
        waiters.add(a)

        val attempt = CompletableDeferred<String>()
        val once = OncePromote()
        promoteOnAttemptCompletion(attempt, waiters, attemptScope, 50.milliseconds, { true }, once.promote)

        attempt.complete("applied")
        assertEquals("not promoted while the run is active", 0, once.count.get())

        a.complete()
        assertEquals("the run promotes when it ends", 1, once.count.get())
    }

    // (d) Several runs active at completion each register a promote, and a grace-style call could also race: the
    // once-only take collapses them all to a single promotion.
    @Test
    fun `promotion never fires twice even when several runs could promote`() {
        val waiters = ConcurrentHashMap.newKeySet<Job>()
        val a = Job()
        val b = Job()
        waiters.add(a)
        waiters.add(b)

        val attempt = CompletableDeferred<String>()
        val once = OncePromote()
        promoteOnAttemptCompletion(attempt, waiters, attemptScope, 50.milliseconds, { true }, once.promote)

        attempt.complete("applied")
        a.complete()
        b.complete()
        // A stray grace-style promotion racing the run hooks must not double-count either.
        once.promote()

        assertEquals("every would-be promotion collapses to one", 1, once.count.get())
    }

    // A late caller that wires against an attempt which already completed "applied" (the normal case once the
    // first apply succeeds and later callers adopt the kept Deferred) must not re-enter attemptWaiters from a
    // synchronously-firing completion hook ("Recursive update" on the JVM): it wires nothing, promotes nothing,
    // and leaks no entry.
    @Test
    fun `a late caller against a completed attempt wires nothing, promotes nothing and leaks no entry`() {
        val attemptWaiters = ConcurrentHashMap<Deferred<*>, MutableSet<Job>>()
        val once = OncePromote()
        val attempt = CompletableDeferred<String>()

        // First caller wires while the attempt is still in flight.
        val firstJob = Job()
        wireAttemptPromotion(attemptWaiters, attempt, firstJob, attemptScope, 50.milliseconds, { true }, once.promote)
        assertTrue("the first caller created the waiter entry", attemptWaiters.containsKey(attempt))

        // The attempt completes applied: its own hook drops the entry; the still-active first run has not ended,
        // so nothing has promoted yet.
        attempt.complete("applied")
        assertFalse("the completion hook drops the entry", attemptWaiters.containsKey(attempt))
        assertEquals("nothing promotes while the first run is still active", 0, once.count.get())

        // A second caller wires against the now-completed Deferred. The real bug threw IllegalStateException here.
        val lateJob = Job()
        wireAttemptPromotion(attemptWaiters, attempt, lateJob, attemptScope, 50.milliseconds, { true }, once.promote)

        assertFalse("the late caller leaks no entry", attemptWaiters.containsKey(attempt))
        assertEquals("the late caller triggers no promotion", 0, once.count.get())
    }

    // Best effort: an attempt completing concurrently with a caller wiring against it must never throw and must
    // never leave a stray attemptWaiters entry, whichever side wins the race.
    @Test
    fun `wiring races completion without throwing or leaking`() {
        repeat(200) {
            val attemptWaiters = ConcurrentHashMap<Deferred<*>, MutableSet<Job>>()
            val once = OncePromote()
            val attempt = CompletableDeferred<String>()

            runBlocking {
                val completer = launch(Dispatchers.Default) { attempt.complete("applied") }
                val wirer = launch(Dispatchers.Default) {
                    wireAttemptPromotion(
                        attemptWaiters, attempt, Job(), attemptScope, 50.milliseconds, { true }, once.promote,
                    )
                }
                completer.join()
                wirer.join()
            }

            // Whether the wirer observed the attempt in flight or already completed, the entry is removed by the
            // completion hook (if it was ever created) and never leaks.
            assertFalse("no stray waiter entry after the race", attemptWaiters.containsKey(attempt))
        }
    }

    // The grace path declines to promote when the patch is no longer live at the end of the grace window.
    @Test
    fun `the grace path does not promote when the patch is no longer live`() {
        val waiters = ConcurrentHashMap.newKeySet<Job>()
        val attempt = CompletableDeferred<String>()
        val once = OncePromote()

        runBlocking {
            promoteOnAttemptCompletion(attempt, waiters, attemptScope, 40.milliseconds, { false }, once.promote)
            attempt.complete("applied")
            withTimeoutOrNull(300L) { while (once.count.get() == 0) yield() }
        }

        assertEquals("a patch that is no longer applied never promotes on the grace path", 0, once.count.get())
    }
}
