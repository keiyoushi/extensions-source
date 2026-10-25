package eu.kanade.tachiyomi.extension.ar.mangatek

import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton
import javax.crypto.AEADBadTagException

class OverlayTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            Injekt.addSingleton(Json { ignoreUnknownKeys = true })
        }
    }

    private fun fixture(): CryptoFixture = javaClass.getResource("/overlay-v3.json")!!.readText().parseAs()

    @Test
    fun decodesVersionThreeFromIndependentWebCryptoVector() {
        val f = fixture()
        assertEquals(f.plaintext, OverlayCrypto.decryptOverlay(f.overlay, f.ks, null, f.token, f.chapterId))
    }

    @Test
    fun rejectsVersionThreeForAnotherChapter() {
        val f = fixture()
        assertThrows(AEADBadTagException::class.java) {
            OverlayCrypto.decryptOverlay(f.overlay, f.ks, null, f.token, f.chapterId + 1)
        }
    }

    @Test
    fun keepsLegacyOverlayWithoutReturnedKey() {
        val f = fixture()
        assertEquals(f.plaintext, OverlayCrypto.decryptOverlay(f.legacy, null, null, f.token, f.chapterId))
    }

    @Test
    fun usesCurrentProofAndSolvesReaderChallenge() = runBlocking {
        val f = fixture()
        assertEquals(f.proof, OverlayCrypto.proof(f.token, f.chapterId))
        val answer = OverlayCrypto.pow("reader-challenge", 12)!!
        assertTrue("reader-challenge:$answer".sha256().toHexString().startsWith("000"))
    }

    @Test
    fun introHasNoOverlayAndFirstAndLastTranslationsStayOnTheirOwnPages() {
        val props = """{"imageUrls":["https://example.com/intro.webp","https://example.com/first.webp","https://example.com/last.webp"],"overlayPageOffset":1}""".parseAs<ChapterProps>()
        val overlay = OverlayData(listOf(OverlayPage(1, listOf(Bubble(text = "الأولى"))), OverlayPage(2, listOf(Bubble(text = "الأخيرة")))))
        val pages = props.toPageList(overlay)
        assertNull(pages[0].imageUrl!!.toHttpUrl().fragment)
        assertEquals("الأولى", pages[1].imageUrl!!.toHttpUrl().fragment!!.parseAs<List<Bubble>>().single().text)
        assertEquals("الأخيرة", pages[2].imageUrl!!.toHttpUrl().fragment!!.parseAs<List<Bubble>>().single().text)
    }

    @Test
    fun keepsZeroBasedOverlaysAndApiOffset() {
        val props = ChapterProps(imageUrls = listOf("https://example.com/intro.webp", "https://example.com/page.webp"))
        val overlay = OverlayData(listOf(OverlayPage(0, listOf(Bubble(text = "الترجمة")))))
        val pages = props.toPageList(overlay, apiOffset = 1)
        assertNull(pages[0].imageUrl!!.toHttpUrl().fragment)
        assertEquals("الترجمة", pages[1].imageUrl!!.toHttpUrl().fragment!!.parseAs<List<Bubble>>().single().text)
    }

    @Test
    fun missingOverlayDoesNotRepeatThePreviousPagesText() {
        val props = ChapterProps(imageUrls = listOf("https://example.com/first.webp", "https://example.com/blank.webp", "https://example.com/last.webp"))
        val overlay = OverlayData(listOf(OverlayPage(1, listOf(Bubble(text = "الأولى"))), OverlayPage(3, listOf(Bubble(text = "الأخيرة")))))
        val pages = props.toPageList(overlay)
        assertNull(pages[1].imageUrl!!.toHttpUrl().fragment)
        assertEquals("الأخيرة", pages[2].imageUrl!!.toHttpUrl().fragment!!.parseAs<List<Bubble>>().single().text)
    }

    @Test
    fun ordinaryChapterKeepsOriginalImageUrls() {
        val urls = listOf("https://example.com/1.webp", "https://example.com/2.webp")
        assertEquals(urls, ChapterProps(imageUrls = urls).toPageList(null).map { it.imageUrl })
    }
}

@Serializable
private class CryptoFixture(
    val chapterId: Long,
    val token: String,
    val ks: String,
    val plaintext: String,
    val overlay: String,
    val legacy: String,
    val proof: String,
)
