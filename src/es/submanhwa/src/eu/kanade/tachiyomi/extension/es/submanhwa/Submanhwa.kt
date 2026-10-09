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
            title = document.selectFirst(".sr-info > h1")!!.text()
            thumbnail_url = document.selectFirst("img.sr-cover")?.absUrl("src")
            val info = document.selectFirst(".sr-info")
            val meta = info?.selectFirst(".sr-meta")

            description = buildString {
                append(info?.select(".sr-stats .sr-stat")?.joinToString(" | ") { it.text() })
                append("\n\n${document.selectFirst(".sr-summary > p")?.text()}")

                info?.selectFirst(".sr-alt")?.text()?.split(ALT_DELIMITER)?.let {
                    append("\n\nAlternative names\n")
                    it.forEach { name -> append("- ${name.trim()}\n") }
                }
            }

            status = when {
                info?.selectFirst(".ongoing") != null -> SManga.ONGOING
                info?.selectFirst(".ended") != null -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            author = meta?.select("dt:contains(Autor(es)) + dd a")?.joinToString { it.text() }
            artist = meta?.select("dt:contains(Artist(s)) + dd a")?.joinToString { it.text() }
            genre = info?.select(".sr-badge.type, .sr-cat, .sr-tag")?.joinToString { it.text() }
        }

        val chapterList = document.select(".chapters-grid [class^=chapter-card]").map { element ->
            SChapter.create().apply {
                val a = element.selectFirst("a.chapter-link")!!
                name = a.text()
                setUrlWithoutDomain(a.absUrl("href"))

                val date = element.selectFirst(".ch-date")?.text()
                date_upload = dateFormat.tryParseDate(date?.removePrefix("🕒 "))
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
    companion object {
        private val ALT_DELIMITER = Regex("""[|/•,;]""")
    }
}
