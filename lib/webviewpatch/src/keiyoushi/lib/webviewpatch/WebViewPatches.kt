package keiyoushi.lib.webviewpatch

import android.content.Context
import keiyoushi.lib.runtime.RuntimeContract
import kotlinx.coroutines.Job

/**
 * The Keiyoushi Runtime's `webviewpatch` module: a catalog of compatibility patches to the app's own WebView,
 * each named by a string id. The runtime's `keiyoushi.runtime.webviewpatch.Entry` object implements this, and
 * extensions reach it through [stripRequestedWith] rather than calling it directly.
 *
 * It is a catalog so that a later patch ships as a runtime-only release: a new id needs no change to any
 * adopting extension's build file. The first id is `"requested-with"`.
 */
interface WebViewPatches : RuntimeContract {

    /**
     * Applies the patch [id] to this app process and returns a status string:
     *  - `"applied"` — the patch is live for the rest of the app's run.
     *  - `"unsupported:<detail>"` — this device or WebView build isn't covered; the detail names the build and ABI.
     *  - `"failed:<detail>"` — the patch is known but could not be applied here.
     *  - `"unknown"` — this runtime doesn't know [id]. It never throws for an unknown id.
     *
     * It returns only once the patch has finished applying and passed its self-test, so a caller can hold every
     * WebView request until it sees `"applied"`. [context] is any app [Context]; [runJob] is the WebView run's
     * [Job] (so a patch can confirm itself only after a run survives), or null when there is no run to tie it to.
     */
    @Throws(Exception::class)
    suspend fun apply(id: String, context: Context, runJob: Job?): String
}
