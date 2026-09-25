package eu.kanade.tachiyomi.extension.id.astralscans

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.utils.asJsoup
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.FormBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

@Source
abstract class AstralScans : MangaThemesia() {

    override val hasProjectPage = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) getChapterList(manga) else chapters }
        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val body = FormBody.Builder()
            .add("ts_action", "get_chapters")
            .build()

        val response = client.post(
            baseUrl + manga.url,
            headers = headersBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .add("X-Protect", "1")
                .build(),
            body = body,
        )

        return parseChapters(response.body.string())
    }

    private fun parseChapters(rawResponse: String): List<SChapter> {
        val responseText = rawResponse.trim()

        if (responseText.startsWith("AST_")) {
            try {
                val payload = responseText.removePrefix("AST_")
                val decoded = String(Base64.decode(payload.reversed(), Base64.DEFAULT), Charsets.UTF_8)
                val parts = decoded.split("^^^")

                if (parts.size >= 2) {
                    val rawHtml = parts[0]
                    val dynamicDataAttr = parts[1]

                    val chapters = rawHtml.asJsoup(baseUrl).select("[$dynamicDataAttr]").mapNotNull { element ->
                        val encodedUrl = element.attr(dynamicDataAttr)
                        val chapterUrl = try {
                            String(Base64.decode(encodedUrl, Base64.DEFAULT), Charsets.UTF_8)
                        } catch (_: Exception) {
                            encodedUrl
                        }

                        val spans = element.select("span")
                        val name = spans.firstOrNull()?.text() ?: "Chapter"
                        val dateText = spans.getOrNull(1)?.text()

                        val isTrap = chapterUrl.contains("chp_trap") ||
                            name.contains("trap", ignoreCase = true) ||
                            dateText?.contains("trap", ignoreCase = true) == true ||
                            element.hasClass("trap") ||
                            element.attr("class").contains("trap") ||
                            element.closest("[class*=trap]") != null

                        if (isTrap) {
                            return@mapNotNull null
                        }

                        SChapter.create().apply {
                            setUrlWithoutDomain(chapterUrl)
                            this.name = name
                            date_upload = dateText?.parseChapterDate() ?: 0L
                        }
                    }

                    if (chapters.isNotEmpty()) {
                        return chapters
                    }
                }
            } catch (_: Exception) {}
        }

        return chapterListParse(responseText.asJsoup(baseUrl))
    }

    override fun chapterListParse(document: Document): List<SChapter> {
        val text = document.body().text().trim()
        if (text.startsWith("AST_")) {
            return parseChapters(text)
        }

        // Fallback: If site reverts to standard MangaThemesia DOM elements
        return super.chapterListParse(document)
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
