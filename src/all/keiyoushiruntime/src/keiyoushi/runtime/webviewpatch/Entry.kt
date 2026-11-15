package keiyoushi.runtime.webviewpatch

import android.content.Context
import keiyoushi.lib.runtime.RuntimeHost
import keiyoushi.lib.webviewpatch.WebViewPatches
import keiyoushi.utils.runWebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The Keiyoushi Runtime's implementation of [WebViewPatches]. It owns the one copy of the patch machinery in
 * the process ([XrwStripper] over [XrwStripEngine]); extensions reach it through the thin `lib:webviewpatch`
 * shim. A newer runtime loaded mid-session shares the applied-patch record through
 * [keiyoushi.lib.runtime.RuntimeHost.state] (see [RuntimeSharedState]), so it adopts a live patch and waits on
 * an in-flight apply instead of writing a second time.
 *
 * [apply] returns only once a patch has applied and self-tested, so a caller can hold every WebView request
 * until it sees `"applied"`. That means the WebView library has to be mapped **before** the run's first
 * navigation — [warmWebViewLibrary] maps it here rather than waiting for a page load.
 */
object Entry : WebViewPatches {

    private const val REQUESTED_WITH = "requested-with"

    // Key under RuntimeHost.state for the one in-flight requested-with apply. The value is a
    // kotlinx.coroutines.Deferred<String> (an app-loaded type, so a newer runtime in a fresh class loader can
    // read it); never a type from this module. A newer runtime finds the old one's Deferred here and awaits it
    // instead of starting a second apply.
    private const val INFLIGHT_KEY = "webviewpatch.requested-with.inflight"

    // The patch attempt runs detached from the run's Job so a run timeout or cancellation can't interrupt the
    // write or strand the store mid-self-test, and so promote-on-survival outlives the awaiting caller.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // How long a patched-but-unwitnessed build waits before promoting itself when no real run hosted it: long
    // enough that a page that crashes the patched process does so first, so the grace path never records an OK
    // for a build nothing survived. Used only on the no-active-run fallback (see [promoteOnAttemptCompletion]).
    private val SURVIVAL_GRACE: Duration = 30.seconds

    // The non-null, still-waiting run Jobs captured per in-flight attempt, keyed by that attempt's Deferred. The
    // attempt's completion hook reads this to decide who promotes: an active run's own hook, or the grace path.
    // The entry is dropped once the attempt completes.
    private val attemptWaiters = ConcurrentHashMap<Deferred<*>, MutableSet<Job>>()

    override suspend fun apply(id: String, context: Context, runJob: Job?): String {
        if (id != REQUESTED_WITH) return "unknown"
        val app = context.applicationContext
        return try {
            applyRequestedWith(app, runJob)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            failedStatus(t)
        }
    }

    private suspend fun applyRequestedWith(app: Context, runJob: Job?): String {
        XrwStripper.hostUnsupportedReason()?.let { return "unsupported:$it" }
        // Already live in this process (an earlier run, or a prior runtime version): nothing to write.
        if (XrwStripper.stripped()) return "applied"

        // Single-flight: the first caller starts the attempt in Entry's detached scope and publishes its
        // Deferred under RuntimeHost.state; every caller (first included, and a newer runtime that finds the
        // old Deferred) awaits that one attempt. compute is atomic, so two racing callers can't both start.
        val attempt = sharedAttempt(RuntimeHost.state, INFLIGHT_KEY, scope) { attemptRequestedWith(app) }

        // Promote the patch to OK only after a run that used the patched process survives it (markRunSurvived).
        // Record this caller's run as a waiter on the attempt; when the attempt completes, whichever of its
        // still-active waiters ends first promotes from its own completion hook. If none is active (every run
        // was cancelled, or the caller passed no runJob), the grace path promotes instead. takePending is atomic,
        // so nothing promotes twice.
        wirePromotion(app, attempt, runJob)

        // await() only in the caller: cancelling this caller cancels its await, not the shared attempt (which
        // is a child of Entry's scope). The attempt's own completion hook drops a finished non-applied (or
        // cancelled) attempt, so the next call retries regardless of what any waiter does.
        return awaitShared(RuntimeHost.state, INFLIGHT_KEY, attempt)
    }

    // The body of one requested-with attempt, run once per flight in Entry's scope. Maps the WebView native
    // library before patching: it loads when a WebView is constructed, and nothing may navigate before the
    // patch is confirmed, so we can't wait for the run's first page to map it.
    private suspend fun attemptRequestedWith(app: Context): String {
        warmWebViewLibrary()
        XrwStripper.patchProcess(app)

        // stripped() is authoritative (it reads the live bytes). lastStatus only supplies the failure detail;
        // a stale or "applied" value while the patch isn't live can't promote a failure to success.
        if (XrwStripper.stripped()) return "applied"
        val detail = XrwStripper.lastStatus()
        return if (detail == null || detail == "applied") "failed:patch did not take" else detail
    }

    // Wire promote-on-survival for one caller of one shared attempt, binding in the Android-side promote.
    private fun wirePromotion(app: Context, attempt: Deferred<String>, runJob: Job?) {
        wireAttemptPromotion(
            attemptWaiters,
            attempt,
            runJob,
            scope,
            SURVIVAL_GRACE,
            { XrwStripper.stripped() },
        ) { promoteIfPending(app) }
    }

    private fun promoteIfPending(app: Context) {
        if (XrwStripper.hasPendingSurvival()) XrwStripper.markRunSurvived(app)
    }

    // Constructing a WebView loads the provider's native library into this process. We go through the core
    // runWebView so creation and teardown use the same renderer isolation, timeout and cleanup every other
    // WebView use does; the run loads nothing (it resolves at once), so it maps the library and sends no request.
    // Failure is tolerated: a host that can't construct a WebView falls through to patchProcess finding no library,
    // which surfaces as a failure the caller throws on, so nothing loads with the header.
    private suspend fun warmWebViewLibrary() {
        runCatching { runWebView<Unit> { resolve(Unit) } }
    }
}

/**
 * Records one caller's run as a waiter on the shared [attempt] and, for the caller that first reaches the attempt,
 * registers its promotion wiring. The first caller creates the waiter set and — **outside** any map remapping
 * function — registers both [promoteOnAttemptCompletion] and the entry's cleanup hook; later in-flight callers add
 * their own non-null [runJob] to that set.
 *
 * A caller that arrives after [attempt] has already completed returns without wiring anything: the promotion
 * decision was made from the attempt's completion hook when it finished, and a late caller has no run that could
 * have witnessed the patch. This also keeps completion hooks off the map from inside a remapping function: an
 * already-completed [Deferred] fires [kotlinx.coroutines.Deferred.invokeOnCompletion] synchronously on the calling
 * thread, so registering [attemptWaiters] removal inside [java.util.concurrent.ConcurrentHashMap.computeIfAbsent]
 * would re-enter the map under its bin lock ("Recursive update" on the JVM). The set is created and claimed with
 * [java.util.concurrent.ConcurrentHashMap.putIfAbsent], and only the caller whose set won registers the hooks.
 */
internal fun wireAttemptPromotion(
    attemptWaiters: ConcurrentHashMap<Deferred<*>, MutableSet<Job>>,
    attempt: Deferred<String>,
    runJob: Job?,
    scope: CoroutineScope,
    grace: Duration,
    stillLive: () -> Boolean,
    promote: () -> Unit,
) {
    // A late caller adopting an already-applied Deferred has nothing to promote and must not wire a completion
    // hook (it would fire synchronously here). The promotion was decided when the attempt completed.
    if (attempt.isCompleted) return

    val created = ConcurrentHashMap.newKeySet<Job>()
    val waiters = attemptWaiters.putIfAbsent(attempt, created) ?: created
    if (waiters === created) {
        // Only the caller that created the entry registers the hooks, and only outside the map's remapping
        // function, so no completion handler touches attemptWaiters while a bin lock is held.
        promoteOnAttemptCompletion(attempt, created, scope, grace, stillLive, promote)
        attempt.invokeOnCompletion { attemptWaiters.remove(attempt) }
    }
    runJob?.let { waiters.add(it) }
}

/**
 * Promote-on-survival, decided when the shared [attempt] completes. OK must mean "a real WebView run used the
 * patched process and the process survived it", so promotion is owned by a run, not by the attempt finishing.
 *
 * At completion the still-active members of [waiters] (the non-null run Jobs that were waiting on this attempt)
 * are the runs that could still witness the patch. If there is at least one, [promote] runs from the completion
 * hook of whichever ends first; runs that had already ended never promote (they may have ended without loading a
 * real page). If none is active — every run was cancelled, or only null-`runJob` callers waited — no run can host
 * the promotion, so a delayed launch in [scope] promotes after [grace], but only if [stillLive] still holds
 * (the patch is still applied) and [promote]'s own pending check still finds something to promote. [grace] is a
 * parameter so tests can shorten it.
 *
 * [promote] is idempotent at the source ([XrwStripper.markRunSurvived] → takePending fires at most once), so
 * several active runs ending, or an active run and the grace path, can all call it without promoting twice.
 */
internal fun promoteOnAttemptCompletion(
    attempt: Deferred<*>,
    waiters: Set<Job>,
    scope: CoroutineScope,
    grace: Duration,
    stillLive: () -> Boolean,
    promote: () -> Unit,
) {
    attempt.invokeOnCompletion {
        val active = waiters.filter { it.isActive }
        if (active.isNotEmpty()) {
            active.forEach { job -> job.invokeOnCompletion { promote() } }
        } else {
            scope.launch {
                delay(grace)
                if (stillLive()) promote()
            }
        }
    }
}

/**
 * The in-flight apply shared across the process (and across a runtime update). Returns the one [Deferred] under
 * [key] in [state], atomically starting [start] in [scope] when none is in flight. [compute] on the
 * [ConcurrentHashMap] runs its remapping once under the bin lock, so two racing callers can never both start a
 * [start]; whoever loses reuses the winner's [Deferred]. The stored value is a `Deferred<String>` — an
 * app-loaded type — so a newer runtime version in a fresh class loader can read and await an older one's attempt.
 *
 * Cleanup rides on the [Deferred] itself, not on any waiter: a completion hook drops the entry unless the attempt
 * finished `"applied"`, so a failed, exceptional or cancelled attempt always clears for a retry even if every
 * caller was cancelled first, or the attempt itself ended cancelled. [remove] is conditional on the same
 * [Deferred], so a newer entry started after this one cleared is never evicted. (A [Deferred] adopted from an
 * older runtime carries that runtime's own hook, so it is not re-registered here.)
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun sharedAttempt(
    state: ConcurrentHashMap<String, Any>,
    key: String,
    scope: CoroutineScope,
    start: suspend () -> String,
): Deferred<String> {
    var started: Deferred<String>? = null
    val value = state.compute(key) { _, existing ->
        existing ?: scope.async { runAttempt(start) }.also { started = it }
    }
    // Register the cleanup outside compute, and only on the Deferred this call started. The attempt can already
    // have completed by now, in which case the handler fires synchronously on this thread; running it inside
    // compute would re-enter the map while its bin lock is held ("Recursive update").
    started?.let { deferred ->
        deferred.invokeOnCompletion { cause ->
            if (cause != null || deferred.getCompleted() != "applied") state.remove(key, deferred)
        }
    }
    @Suppress("UNCHECKED_CAST")
    return value as Deferred<String>
}

/**
 * Runs one shared attempt so its [Deferred] completes **with a status string** for any non-cancellation outcome:
 * a thrown error is mapped to `"failed:<detail>"` (the same mapping [Entry.apply] uses), so every waiter reads a
 * status rather than re-throwing. Cancellation still propagates, so a cancelled attempt isn't recorded as a
 * failure; its completion hook drops the entry either way, so the next call retries.
 */
private suspend fun runAttempt(start: suspend () -> String): String = try {
    start()
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    failedStatus(t)
}

/** The `"failed:<detail>"` status for a thrown attempt: its message, or its class name when it has none. */
internal fun failedStatus(t: Throwable): String = "failed:${t.message ?: t.javaClass.simpleName}"

/**
 * Awaits the shared [attempt] and returns its status. Entry-side removal lives on the attempt's completion hook
 * (see [sharedAttempt]), so this only maps outcomes to a status; [state] and [key] are passed for call-site
 * symmetry and are no longer touched here. An `"applied"` attempt is left mapped, so a later caller awaits it and
 * gets `"applied"` without re-patching.
 *
 * A [CancellationException] from [await] has two causes. If *this caller* was cancelled, it is honoured and
 * rethrown. If instead the *shared attempt* ended cancelled while this caller is still active, that is reported as
 * `"failed:<detail>"` — never surfaced as a [CancellationException] to a caller that wasn't cancelled — and the
 * next call retries (the hook has already cleared the entry).
 */
internal suspend fun awaitShared(
    @Suppress("UNUSED_PARAMETER") state: ConcurrentHashMap<String, Any>,
    @Suppress("UNUSED_PARAMETER") key: String,
    attempt: Deferred<String>,
): String = try {
    attempt.await()
} catch (c: CancellationException) {
    if (!currentCoroutineContext().isActive) throw c
    failedStatus(c)
} catch (t: Throwable) {
    // Backstop for an attempt that completed exceptionally (e.g. an older runtime's Deferred this code didn't
    // wrap); that runtime's own hook clears the entry.
    failedStatus(t)
}
