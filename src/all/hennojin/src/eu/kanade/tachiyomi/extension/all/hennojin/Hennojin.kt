package eu.kanade.tachiyomi.extension.all.hennojin

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.head
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseZonedDateTime
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import org.jsoup.select.Evaluator
import java.time.format.DateTimeFormatter

@Source
abstract class Hennojin : KeiSource() {

    // Popular is latest
    override val supportsLatest = false

    private val httpUrl: HttpUrl get() = "$baseUrl/home".toHttpUrl()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = httpUrl.newBuilder().apply {
            when (lang) {
                "ja" -> {
                    addEncodedPathSegments("page/$page/")
                    addQueryParameter("archive", "raw")
                }
                else -> addEncodedPathSegments("page/$page")
            }
        }.build()

        return parseMangaList(client.get(url))
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // The search is ignored without the current WordPress nonce, which rotates
        val nonce = client.get(httpUrl).asJsoup().selectFirst("input#_wpnonce")!!.attr("value")
        val url = httpUrl.newBuilder()
            .addEncodedPathSegments("page/$page")
            .addQueryParameter("keyword", query)
            .addQueryParameter("_wpnonce", nonce)
            .build()

        return parseMangaList(client.get(url))
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".grid-items .layer-content").map { element ->
            SManga.create().apply {
                element.selectFirst(".title_link > a")?.let {
                    title = it.text()
                    setUrlWithoutDomain(it.absUrl("href"))
                }
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst(".paginate .next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = manga.apply {
            description = document.select(".manga-subtitle + p + p")
                .joinToString("\n") {
                    it
                        .apply { select(Evaluator.Tag("br")).prepend("\\n") }
                        .text()
                        .replace("\\n", "\n")
                        .replace("\n ", "\n")
                }
            genre = document.select(
                ".tags-list a[href*=/parody/]," +
                    ".tags-list a[href*=/tags/]," +
                    ".tags-list a[href*=/character/]",
            ).joinToString { it.text() }
            artist = document.selectFirst(".tags-list a[href*=/artist/]")?.text()
            author = document.selectFirst(".tags-list a[href*=/group/]")?.text() ?: artist
            status = SManga.COMPLETED
        }

        if (!fetchChapters) {
            return SMangaUpdate(updatedManga, chapters)
        }

        val date = document
            .selectFirst(".manga-thumbnail > img")
            ?.absUrl("src")
            ?.let { url -> client.head(url, ensureSuccess = false).use { it.date } }

        val updatedChapters = document.select("a:contains(Read Online)").map {
            SChapter.create().apply {
                setUrlWithoutDomain(
                    it
                        .absUrl("href")
                        .toHttpUrlOrNull()
                        ?.newBuilder()
                        ?.removeAllQueryParameters("view")
                        ?.addQueryParameter("view", "multi")
                        ?.build()
                        ?.toString()
                        ?: it.absUrl("href"),
                )
                name = "Chapter"
                date?.let { date_upload = it }
                chapter_number = -1f
            }
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".slideshow-container > img")
            .mapIndexed { idx, img -> Page(idx, imageUrl = img.absUrl("src")) }
    }

    private inline val Response.date: Long
        get() = DateTimeFormatter.RFC_1123_DATE_TIME.tryParseZonedDateTime(headers["Last-Modified"])
}
