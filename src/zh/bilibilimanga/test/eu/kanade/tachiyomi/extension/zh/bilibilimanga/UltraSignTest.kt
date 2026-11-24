package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import org.junit.Assert.assertEquals
import org.junit.Test

// Expected values come from the site's wasm (a5b84fd26eefaff32016) run on the same inputs.
class UltraSignTest {

    @Test
    fun hash() {
        assertEquals("XXds4sXKhDRskkKaGQRYLGahKLDkLKd6", UltraSign.hash(""))
        assertEquals("4hDGLRG4aLDKQQdQwXaRRDK64RhkwKRK", UltraSign.hash("a"))
        assertEquals("XdGaKkskadwYdR6K4hXwkhsDRKdGRDaD", UltraSign.hash("hello world"))
        assertEquals("XhhkGshKhDk4kDLsKKhDwQD4LhsQQaLs", UltraSign.hash("日本語テスト★"))
    }

    @Test
    fun hashAcrossBlocks() {
        // 63 and 64 bytes need a second block for the padding (one block holds at most 55 bytes of input).
        assertEquals("sRaXskDXDdDLQGRaRa4GD4YkaQs6Xk66", UltraSign.hash("x".repeat(63)))
        assertEquals("RYLhds6YksYGQRwQQwGwLdGKDw6Q6sLY", UltraSign.hash("y".repeat(64)))
        assertEquals("4d6KGX4wQs6kY4QaLdh6YsaQw4dX6sQR", UltraSign.hash("z".repeat(130)))
    }

    @Test
    fun signEvenTs() {
        assertEquals("XKakd36edDQ0Y7h640Qa6GR3hRdRQ0GsR6LRk94Kd1GhGsLd", UltraSign.sign("""{"comic_id":26009}""", 1731000000000))
        assertEquals("K4GXs36eLY4047wkR0GaXGK34dhQY0Y6KKdXs9DLk1RLKswd", UltraSign.sign("""{"ep_id":300318}""", 1731000000000))
        assertEquals("YRaDG3QeXD40a7DLa0haYsQ3Xss6G0YkGDKKk9DhL16GDsKd", UltraSign.sign("", 1731000000000))
    }

    @Test
    fun signOddTs() {
        assertEquals("YD4XL34eKRs0Y7wkk1LaQDa3KXYQ604dRhXKD9a4D1YY4Ydd", UltraSign.sign("""{"comic_id":26009}""", 1731000000001))
        assertEquals(
            "RDXKL5Q8L4d06fkww1Xes6GbGh4Yac4Gw4aaG8Xhh1sDXGG6",
            UltraSign.sign("""{"style_id":13,"page_num":2,"page_size":18}""", 1700000000001),
        )
    }

    @Test
    fun signBodies() {
        assertEquals(
            "XDaRK567dR4fwfLLLfLeKkwbRXXGhcQDGhwwa8hKL1Y4aRd6",
            UltraSign.sign("""{"ep_id":424242,"m2":"=n1AVRmftc4iVQvLHaEm"}""", 1699999999999),
        )
        assertEquals(
            "DkDGa3YeQsh0D764a5QaDXR3RhRGK0KQYQGdY9kaK1L6dXYd",
            UltraSign.sign("""{"urls":"[\"/bfs/manga/x.jpg\",\"/bfs/manga/y.jpg\"]","m1":"deadbeef"}""", 1731000000005),
        )
        assertEquals("dhGkw1D0KK40s5GdR0a9kY67kQwQL9KKk6asQbdk41Qa4D4c", UltraSign.sign("""{"uni":"αβγ日本語★😀"}""", 1888000000000))
    }

    // Long enough for the body mask's keystream to pass every key schedule quirk (GetImageIndex bodies are ~750 bytes).
    @Test
    fun signLongBodies() {
        assertEquals("dwhs46w5XYY0h4hRR0KbkRdaQdwRaaXYwKwwa9GQX1wXDXk4", UltraSign.sign("0123456789".repeat(30), 1763700000000))
        assertEquals(
            "akdaK6Y5DRY0Q46Q61GbdGLaDKwKKaQLa6sKX9s4s1hQkhL4",
            UltraSign.sign("{\"ep_id\":272408,\"m2\":\"" + "Zz9".repeat(240) + "\"}", 1763700000001),
        )
        assertEquals("R4kwk6k5wkk0s444K2wbwwYa4LwRwawGGdQ449ws61LaYLR4", UltraSign.sign("日本語".repeat(100), 1763700000002))
    }

    @Test
    fun signTimestampRange() {
        assertEquals("sDXwK5G0wdD0K4Q6h0Ya6Yh86QGDsd666QwsKedwa0ddkks1", UltraSign.sign("""{"comic_id":26009}""", 1000000000000))
        assertEquals("RRKk42QfsQDfkekLQfR7Q468wQK6h44XRdhRL1KhQ9DdLkL9", UltraSign.sign("""{"comic_id":26009}""", 9999999999999))
    }
}
