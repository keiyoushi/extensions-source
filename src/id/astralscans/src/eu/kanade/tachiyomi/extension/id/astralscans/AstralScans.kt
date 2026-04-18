package eu.kanade.tachiyomi.extension.id.astralscans

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AstralScans : MangaThemesia("Astral Scans", "https://astralscans.top", "id") {

    override val hasProjectPage = true

    override fun chapterListRequest(manga: SManga): Request {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chapter_action", "generate_list") // Updated parameter from HAR
            .build()

        return POST(
            url = baseUrl + manga.url,
            headers = headersBuilder()
                .add("X-Req-With", "XMLHttpRequest") // Updated header from HAR
                .build(),
            body = body,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseString = response.body.string()

        // 1. New Anti-Scraper Logic (ASTRAL_HTML|||<encoded_payload>)
        if (responseString.startsWith("ASTRAL_HTML|||")) {
            val encoded = responseString.substringAfter("ASTRAL_HTML|||")

            if (encoded.isNotEmpty()) {
                try {
                    // Step 1 & 2: Base64 Decode -> ROT13 = Raw HTML
                    val decodedBytes = Base64.decode(encoded, Base64.DEFAULT)
                    val decodedString = String(decodedBytes, Charsets.UTF_8)
                    val rawHtml = rot13(decodedString)

                    val document = Jsoup.parse(rawHtml)

                    // Step 3: Extract from decrypted HTML
                    val chapters = document.select(".chp-box").map { element ->
                        SChapter.create().apply {
                            // Step 4: The URL inside data-u is Base64 encoded
                            val dataU = element.attr("data-u")
                            val chapterUrl = String(Base64.decode(dataU, Base64.DEFAULT), Charsets.UTF_8)
                            setUrlWithoutDomain(chapterUrl)

                            name = element.selectFirst(".chp-num")?.text() ?: "Chapter"

                            val dateStr = element.selectFirst(".chp-date")?.text()
                            date_upload = dateStr?.parseChapterDate() ?: 0L
                        }
                    }

                    if (chapters.isNotEmpty()) {
                        return chapters
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Fallback: If site reverts to standard MangaThemesia DOM elements
        val document = Jsoup.parse(responseString, response.request.url.toString())
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    override fun chapterListSelector() = "div#kumpulan-bab-area .astral-item, div.eplister li"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val urlElement = element.selectFirst("a")
        val dataU = element.selectFirst(".js-link")?.attr("data-u") ?: ""

        if (dataU.isNotEmpty()) {
            val decoded = String(Base64.decode(dataU, Base64.DEFAULT), Charsets.UTF_8)
            setUrlWithoutDomain(decoded)
        } else {
            setUrlWithoutDomain(urlElement?.attr("href") ?: "")
        }

        name = element.selectFirst(".ch-title, .epl-num, .chapternum")?.text() ?: ""
        date_upload = element.selectFirst(".ch-date, .chapterdate")?.text()?.parseChapterDate() ?: 0L
    }

    private fun rot13(text: String): String = text.map {
        if (it.isLetter()) {
            val base = if (it.isUpperCase()) 'A' else 'a'
            ((it.code - base.code + 13) % 26 + base.code).toChar()
        } else {
            it
        }
    }.joinToString("")
}
