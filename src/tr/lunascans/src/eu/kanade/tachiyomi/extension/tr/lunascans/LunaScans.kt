package eu.kanade.tachiyomi.extension.tr.lunascans

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class LunaScans : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)

    override suspend fun getPopularManga(page: Int) = parseArchivePage(page, "views")
    override suspend fun getLatestUpdates(page: Int) = parseArchivePage(page, "latest")

    private suspend fun parseArchivePage(page: Int, order: String): MangasPage {
        val url = baseUrl.toHttpUrl().resolve("/$mangaSubString/")!!.newBuilder().apply {
            if (page > 1) addPathSegments("page/$page/")
            addQueryParameter("m_orderby", order)
        }.build()
        val response = client.get(url, ensureSuccess = false)
        if (response.code == 404) {
            response.close()
            return MangasPage(emptyList(), false)
        }
        val document = response.asJsoup()
        val mangas = parseArchive(document)
        return MangasPage(mangas, mangas.size >= 12)
    }

    override fun chapterListSelector() = "#tuhaf-chapter-grid a.tuhaf-ch-item, li.wp-manga-chapter"

    override fun chapterFromElement(element: Element, mangaPath: String): SChapter? {
        if (element.tagName() == "a") {
            val url = element.attr("abs:href").takeIf(String::isNotBlank) ?: return null
            val slug = url.toHttpUrl().encodedPath.trimEnd('/').substringAfterLast('/').takeIf(String::isNotEmpty) ?: return null
            val name = element.selectFirst(".tuhaf-ch-name")?.text()?.trim()
                ?: element.ownText().trim().ifBlank { element.text().trim() }
            val date = element.selectFirst(".tuhaf-ch-date")?.text()
                ?: element.selectFirst(chapterDateSelector)?.text()

            return SChapter.create().apply {
                this.url = slug
                this.name = name
                date_upload = parseChapterDate(date)
                memo = buildJsonObject { put("mangaPath", mangaPath) }
            }
        }
        return super.chapterFromElement(element, mangaPath)
    }

    override fun parsePages(document: Document): List<Page> {
        val pageList = super.parsePages(document)

        if (
            pageList.isEmpty() &&
            document.select(".content-blocked, .login-required").isNotEmpty()
        ) {
            throw Exception("Okumak için WebView üzerinden giriş yapın")
        }
        return pageList
    }
}
