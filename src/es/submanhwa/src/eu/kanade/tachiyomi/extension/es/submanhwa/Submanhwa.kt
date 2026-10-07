package eu.kanade.tachiyomi.extension.es.submanhwa

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Submanhwa : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("d MMM. yyyy", Locale.ENGLISH)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("Accept-Language", "es-PE,es;q=0.9,en-US;q=0.8,en;q=0.7")

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()

        val mangas = document.select("article.up-card").map { element ->
            SManga.create().apply {
                title = element.selectFirst("a.up-title")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")!!.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.count(String::isNotBlank) < 2) return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = parseMangaList(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("filterList")
            addQueryParameter("page", page.toString())
            addQueryParameter("sortBy", "views")
            addQueryParameter("asc", "false")
            addQueryParameter("alpha", query)
        }.build(),
    )

    private suspend fun parseMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select(".series-card").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".series-title")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")!!.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("li a[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst(".manga-title-centered")!!.text()
            thumbnail_url = document.selectFirst("img")?.absUrl("src")
            description = document.selectFirst("h5:contains(Resumen) + p")?.text()

            val box = document.selectFirst(".main-content > .boxed-modern")

            status = when (box?.selectFirst(".detail-label:contains(Estado) + .detail-value span")?.text()?.lowercase()) {
                "completa" -> SManga.COMPLETED
                "en curso" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }

            author = box?.selectFirst(".detail-label:contains(Autor) + .detail-value a")?.text()
            artist = box?.selectFirst(".detail-label:contains(Artist) + .detail-value a")?.text()
            genre = box?.select(".detail-label:contains(Categor) + .detail-value a")?.joinToString { it.text() }
        }

        val chapterList = document.select(".chapters-grid [class^=chapter-card]").map { element ->
            SChapter.create().apply {
                val a = element.selectFirst("a.chapter-link")!!
                name = a.text()
                setUrlWithoutDomain(a.absUrl("href"))

                val date = element.selectFirst("span:has(i.glyphicon-time)")?.text()
                    ?: element.selectFirst(".chapter-preview-meta > span")?.text()

                date_upload = dateFormat.tryParseDate(date)
            }
        }

        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#all img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.imgAttr())
        }
    }

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }
}
