package keiyoushi.runtime.webviewpatch

import keiyoushi.lib.runtime.RuntimeHost
import keiyoushi.lib.runtime.RuntimeModule
import keiyoushi.lib.webviewpatch.WebViewPatches
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Proves the lib's [WebViewPatches] contract binds against the runtime's [Entry] through [RuntimeModule.bind]:
 * every contract method resolves to an `Entry` method with matching name, parameter types and return type, and
 * each is `@Throws(Exception::class)`. A mismatch (a renamed or re-signatured `Entry` method, a missing
 * `@Throws`) makes `bind()` throw, so a green bind is the check.
 */
class WebViewPatchesContractTest {

    @Test
    fun `the webviewpatch contract binds against Entry`() {
        RuntimeHost.register(
            versionCode = 1,
            versionName = "test",
            apis = mapOf("webviewpatch" to 1),
            minApis = mapOf("webviewpatch" to 1),
            entries = mapOf("webviewpatch" to Entry),
        )

        val patches = RuntimeModule("webviewpatch", api = 1).bind<WebViewPatches>()
        assertNotNull(patches)
    }
}
