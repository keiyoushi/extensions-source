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
            .addFormDataPart("manga_req", "ping")
            .build()

        return POST(
            url = baseUrl + manga.url,
            headers = headersBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .build(),
            body = body,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseString = response.body.string()

        if (responseString.startsWith("ASTRAL_")) {
            val parts = responseString.split("|||")

            if (parts.size >= 3) {
                try {
                    val rawHtml = String(Base64.decode(parts[1], Base64.DEFAULT), Charsets.UTF_8)
                    val dynamicDataAttr = parts[2]

                    val document = Jsoup.parse(rawHtml)
                    val chapters = document.select("[$dynamicDataAttr]").mapNotNull { element ->
                        val isTrap = element.hasClass("trap") ||
                            element.attr("class").contains("trap") ||
                            element.closest("[class*=trap]") != null

                        if (isTrap) {
                            return@mapNotNull null
                        }

                        SChapter.create().apply {
                            val encodedUrl = element.attr(dynamicDataAttr)
                            val chapterUrl = String(Base64.decode(encodedUrl, Base64.DEFAULT), Charsets.UTF_8)
                            setUrlWithoutDomain(chapterUrl)

                            name = element.selectFirst("span[class^=n_]")?.text() ?: "Chapter"
                            date_upload = element.selectFirst("span[class^=d_]")?.text()?.parseChapterDate() ?: 0L
                        }
                    }

                    if (chapters.isNotEmpty()) {
                        return chapters
                    }
                } catch (_: Exception) {}
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
}
