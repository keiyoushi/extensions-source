package keiyoushi.lib.webviewpatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM tests for the crash-safe store. Pure `java.io`, no Android: proves a truncated/empty file is
 * tolerated (the blocklist/give-up state isn't lost to a crash mid-write) and that writes are atomic.
 */
class FileKvStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store(file: File) = FileKvStore(file, Any())

    @Test
    fun `put, get and remove round-trip and persist across instances`() {
        val file = tmp.newFile("xrw.properties")
        store(file).apply {
            put("sha.a", "blocklisted")
            put("crash.a", "1")
        }
        // A fresh store over the same file (a later launch) still sees the state.
        store(file).apply {
            assertEquals("blocklisted", get("sha.a"))
            assertEquals("1", get("crash.a"))
            remove("crash.a")
            assertNull(get("crash.a"))
            assertEquals("blocklisted", get("sha.a"))
        }
    }

    @Test
    fun `a truncated or garbage file is tolerated, not fatal`() {
        val file = tmp.newFile("xrw.properties")
        // Simulate a half-written save from a crash: non-properties garbage / an empty file.
        file.writeText("\u0000\u0000 not a properties file ￿")
        val s = store(file)
        assertNull("a corrupt file reads as empty rather than throwing", s.get("sha.a"))
        // And it recovers: a subsequent write produces a valid, readable store.
        s.put("sha.a", "ok:1:2:3:4")
        assertEquals("ok:1:2:3:4", store(file).get("sha.a"))

        file.writeText("") // empty file
        assertNull(store(file).get("anything"))
    }

    @Test
    fun `edit applies several mutations in one save`() {
        val file = tmp.newFile("xrw.properties")
        val s = store(file)
        s.put("crash.a", "1")
        s.put("mtime.a", "123")
        s.edit {
            setProperty("sha.a", "blocklisted")
            remove("crash.a")
            remove("mtime.a")
        }
        store(file).apply {
            assertEquals("blocklisted", get("sha.a"))
            assertNull(get("crash.a"))
            assertNull(get("mtime.a"))
        }
    }

    @Test
    fun `editAndGet returns the computed value and persists the mutation`() {
        val file = tmp.newFile("xrw.properties")
        val s = store(file)
        val first = s.editAndGet {
            val n = (getProperty("crash.a")?.toIntOrNull() ?: 0) + 1
            setProperty("crash.a", n.toString())
            n
        }
        assertEquals(1, first)
        assertEquals("1", store(file).get("crash.a"))
    }

    @Test
    fun `a failed rename fails safe - existing state kept, no in-place write, temp dropped`() {
        // Force renameTo to fail deterministically: the destination is a non-empty directory, which
        // rename(2) refuses to replace. The old in-place fallback would throw here (a directory can't be
        // opened for writing); the fail-safe path must swallow the failure, leave the destination untouched,
        // and remove the temp rather than attempt a torn in-place write.
        val dest = tmp.newFolder("occupied")
        File(dest, "keep").writeText("state")
        store(dest).put("sha.a", "blocklisted") // must not throw
        assertTrue("the existing destination is left intact", dest.isDirectory)
        assertEquals("existing content is preserved", "state", File(dest, "keep").readText())
        assertFalse("the temp file is dropped, not left behind", File(dest.parentFile, dest.name + ".tmp").exists())
    }

    @Test
    fun `no stray temp file is left after a successful save`() {
        val file = tmp.newFile("xrw.properties")
        store(file).put("sha.a", "nomatch")
        assertTrue(file.exists())
        assertFalse("the temp file must be renamed away, not left behind", File(file.parentFile, file.name + ".tmp").exists())
    }
}
