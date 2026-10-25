package eu.kanade.tachiyomi.extension.ar.mangatek

import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addSingleton

class OverlayLiveTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            Injekt.addSingleton(Json { ignoreUnknownKeys = true })
        }
    }

    @Test
    fun translatedChaptersReturnArabicOverlaysAndLeaveIntroUntranslated() {
        assumeTrue(System.getenv("MANGATEK_LIVE_TEST") == "1")
        val client = OkHttpClient()
        val chapters = listOf(
            "clever-cleaning-life-of-the-returned-genius-hunter/115",
            "i-was-more-overpowered-than-the-hero-so-i-hid-my-power-xassadksd2-ilmo4b/123",
        )
        for (chapter in chapters) {
            val pageRequest = request("https://mangatek.com/reader/$chapter").build()
            val props = client.newCall(pageRequest).execute().use { response ->
                assertEquals(200, response.code)
                val document = Jsoup.parse(response.body.string())
                val serialized = document.selectFirst("[props*=imageUrls]")!!.attr("props")
                serialized.parseAs<JsonElement>().unwrapAstro().parseAs<ChapterProps>()
            }
            assertEquals("overlay", props.textMode)
            val token = props.unlockToken!!
            val id = props.chapterId!!
            val payload = UnlockRequest(id, token, OverlayCrypto.proof(token, id), OverlayCrypto.esid)
            val unlockRequest = request("https://api.mangatek.com/api/reader/unlock")
                .post(payload.toJsonRequestBody()).build()
            val unlock = client.newCall(unlockRequest).execute().use { response ->
                assertEquals(200, response.code)
                response.parseAs<UnlockResponse>()
            }
            val plaintext = OverlayCrypto.decryptOverlay(unlock.overlay!!, unlock.ks, unlock.key, token, id)
            val overlay = plaintext.parseAs<OverlayData>()
            assertTrue(overlay.pages.flatMap { it.overlays }.any { it.text.any { c -> c in '\u0600'..'\u06ff' } })
            val pages = props.toPageList(overlay, unlock.overlayPageOffset)
            assertEquals(props.imageUrls.size, pages.size)
            assertNull(pages.first().imageUrl!!.toHttpUrl().fragment)
            println("LIVE $chapter: ${pages.size} images, ${overlay.pages.size} translated pages, ${overlay.pages.sumOf { it.overlays.size }} bubbles")
        }
    }

    private fun request(url: String) = Request.Builder().url(url)
        .header("User-Agent", "Mozilla/5.0")
        .header("Origin", "https://mangatek.com")
        .header("Referer", "https://mangatek.com/")

    private fun JsonElement.unwrapAstro(): JsonElement = when (this) {
        is JsonArray -> if (size == 2 && this[0] is JsonPrimitive) this[1].unwrapAstro() else JsonArray(map { it.unwrapAstro() })
        is JsonObject -> JsonObject(mapValues { it.value.unwrapAstro() })
        else -> this
    }
}
