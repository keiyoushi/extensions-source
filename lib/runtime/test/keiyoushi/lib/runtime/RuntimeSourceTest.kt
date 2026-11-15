package keiyoushi.lib.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeSourceTest {
    private fun source(name: String) = RuntimeSource(
        id = 1L,
        name = name,
        versionName = "1.0.0",
        apis = emptyMap(),
        licenses = emptyMap(),
    )

    // The app titles a source's settings screen with source.toString(); it must read as the name,
    // not the obfuscated class toString() (e.g. "a1@8050f37").
    @Test
    fun `toString returns the source name`() {
        assertEquals("Keiyoushi Runtime", source("Keiyoushi Runtime").toString())
    }
}
