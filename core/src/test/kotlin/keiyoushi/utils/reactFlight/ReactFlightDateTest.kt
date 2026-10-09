package keiyoushi.utils.reactFlight

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class ReactFlightDateTest {

    private val json = Json

    @Test
    fun parsesUtcTimestamp() {
        assertEquals(1718454896789L, decode("2024-06-15T12:34:56.789Z").time)
        assertEquals(0L, decode("1970-01-01T00:00:00.000Z").time)
    }

    @Test
    fun rejectsUnparseableDate() {
        try {
            decode("not-a-date")
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    // The serializer is a singleton, so its formatter is shared by every thread. SimpleDateFormat is
    // not thread-safe and returned wrong values / threw under contention; DateTimeFormatter is immutable.
    @Test
    fun parsesConcurrently() {
        val samples = listOf(
            "2024-01-01T00:00:00.000Z" to 1704067200000L,
            "2024-06-15T12:34:56.789Z" to 1718454896789L,
            "2025-12-31T23:59:59.999Z" to 1767225599999L,
            "2023-03-03T03:03:03.003Z" to 1677812583003L,
        )
        val failures = ConcurrentLinkedQueue<String>()
        val pool = Executors.newFixedThreadPool(8)
        repeat(8) { t ->
            pool.submit {
                repeat(10_000) { i ->
                    val (text, expected) = samples[(t + i) % samples.size]
                    try {
                        val actual = decode(text).time
                        if (actual != expected) failures.add("$text -> $actual, expected $expected")
                    } catch (e: Exception) {
                        failures.add("$text -> ${e::class.simpleName}: ${e.message}")
                    }
                }
            }
        }
        pool.shutdown()
        assertTrue("pool did not finish", pool.awaitTermination(60, TimeUnit.SECONDS))
        assertTrue("concurrent deserialization produced ${failures.size} failure(s): ${failures.take(5)}", failures.isEmpty())
    }

    private fun decode(text: String) = json.decodeFromString(ReactFlightDateSerializer, "\"$text\"")
}
