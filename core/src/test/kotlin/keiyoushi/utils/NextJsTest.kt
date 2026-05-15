package keiyoushi.utils

import keiyoushi.utils.reactFlight.ReactFlightBigInt
import keiyoushi.utils.reactFlight.ReactFlightDate
import keiyoushi.utils.reactFlight.ReactFlightNumber
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

class NextJsTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            // jsonInstance = Injekt.get(); register one for the unit-test JVM.
            Injekt.addSingleton(Json { ignoreUnknownKeys = true })
        }
    }

    private fun fixture(name: String): String = javaClass.getResource("/reactflight/$name.txt")!!.readText()

    // Fixtures captured from real React 19 Flight output (regenerate via src/test/rsc-capture).

    @Serializable
    data class FixtureMarkers(
        val name: String,
        val big: ReactFlightBigInt,
        val date: ReactFlightDate,
        val inf: ReactFlightNumber,
        val negInf: ReactFlightNumber,
        val nan: ReactFlightNumber,
        val negZero: ReactFlightNumber,
        val plain: ReactFlightNumber,
        val missing: String? = null,
    )

    @Test
    fun realFlightMarkersFixture() {
        val m = fixture("markers").extractNextJsRsc<FixtureMarkers>()
        assertNotNull(m)
        m!!
        assertEquals("hello", m.name)
        assertEquals("123456789012345678901234567890", m.big.toString())
        assertEquals(1704164645000L, m.date.time)
        assertTrue(m.inf.toDouble() == Double.POSITIVE_INFINITY)
        assertTrue(m.negInf.toDouble() == Double.NEGATIVE_INFINITY)
        assertTrue(m.nan.toDouble().isNaN())
        assertEquals(-0.0, m.negZero.toDouble(), 0.0)
        assertEquals(3.14, m.plain.toDouble(), 0.0) // bare JSON number, not a $-token
        assertEquals(null, m.missing) // $undefined -> JSON null
    }

    @Serializable
    data class LargeText(val title: String, val body: String, val unicode: String)

    @Test
    fun realFlightLargeTextFixture() {
        // body/unicode are outlined as binary "T<byteLen>," chunks, referenced via $1/$2.
        val expectedBody = "Lorem ipsum dolor sit amet. ".repeat(60)
        val expectedUnicode = "héllo wörld 你好 🚀 ".repeat(80)
        val t = fixture("largetext").extractNextJsRsc<LargeText>()
        assertNotNull(t)
        t!!
        assertEquals("big", t.title)
        assertEquals(expectedBody, t.body)
        // Multibyte + surrogate (🚀) content survives UTF-8 byte-length walking intact.
        assertEquals(expectedUnicode, t.unicode)
    }

    @Serializable
    data class Collections(val items: Map<String, Int>, val tags: List<Int>)

    @Test
    fun realFlightCollectionsFixture() {
        val c = fixture("collections").extractNextJsRsc<Collections>()
        assertNotNull(c)
        assertEquals(mapOf("a" to 1, "b" to 2), c!!.items)
        assertEquals(listOf(10, 20, 30), c.tags)
    }
}
