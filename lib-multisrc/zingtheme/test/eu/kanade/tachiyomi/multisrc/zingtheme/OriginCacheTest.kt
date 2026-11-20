package eu.kanade.tachiyomi.multisrc.zingtheme

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OriginCacheTest {
    @Test
    fun switchingOriginsReloadsConfigurationAndCategoryIds() = runBlocking {
        var origin = "https://a.example"
        val cache = OriginCache<String> { origin }
        var configLoads = 0
        var categoryLoads = 0
        suspend fun config() = cache.get(origin, "config") {
            configLoads++
            "$origin/ajax:nonce-$configLoads"
        }
        suspend fun category() = cache.get(origin, "genre") {
            categoryLoads++
            "id-$categoryLoads"
        }

        assertEquals("https://a.example/ajax:nonce-1", config())
        assertEquals("id-1", category())
        assertEquals("id-1", category())
        origin = "https://b.example"
        assertEquals("https://b.example/ajax:nonce-2", config())
        assertEquals("id-2", category())
        origin = "https://a.example"
        assertEquals("https://a.example/ajax:nonce-3", config())
        assertEquals("id-3", category())
    }

    @Test
    fun aLateOldOriginResponseCannotReplaceCurrentOriginState() = runBlocking {
        var origin = "https://a.example"
        val cache = OriginCache<String> { origin }
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val old = async {
            cache.get("https://a.example", "genre") {
                started.complete(Unit)
                finish.await()
                "old-id"
            }
        }
        started.await()
        origin = "https://b.example"
        val current = async { cache.get("https://b.example", "genre") { "new-id" } }
        finish.complete(Unit)
        assertEquals("old-id", old.await())
        assertEquals("new-id", current.await())
        assertEquals("new-id", cache.get("https://b.example", "genre") { "unexpected reload" })
    }

    @Test
    fun parallelRequestsShareOneSuccessfulLookup() = runBlocking {
        val cache = OriginCache<String> { "https://a.example" }
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var loads = 0
        val first = async {
            cache.get("https://a.example", "genre") {
                loads++
                started.complete(Unit)
                finish.await()
                "id-1"
            }
        }
        started.await()
        val second = async {
            cache.get("https://a.example", "genre") {
                loads++
                "id-2"
            }
        }
        finish.complete(Unit)
        assertEquals("id-1", first.await())
        assertEquals("id-1", second.await())
        assertEquals(1, loads)
    }

    @Test
    fun anOldQueuedRequestCannotDiscardNewOriginData() = runBlocking {
        val cache = OriginCache<String> { "https://b.example" }
        assertEquals("new-id", cache.get("https://b.example", "genre") { "new-id" })
        assertEquals("old-id", cache.get("https://a.example", "genre") { "old-id" })
        assertEquals("new-id", cache.get("https://b.example", "genre") { "unexpected reload" })
    }
}
