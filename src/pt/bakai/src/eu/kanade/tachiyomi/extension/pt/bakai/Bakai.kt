package eu.kanade.tachiyomi.extension.pt.bakai

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class Bakai : KeiSource() {

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()

        val mangas = document.select("[data-blockid^=app_tools_taMostViewedRecords] [id$=_week_panel] li.ipsData__item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a.ipsLinkPanel")!!.attr("href"))
                val img = element.selectFirst(".ipsData__image img")
                title = img?.attr("alt").orEmpty()
                thumbnail_url = img?.attr("abs:src")
            }
        }

        return MangasPage(mangas, false)
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) {
            "$baseUrl/home/"
        } else {
            "$baseUrl/home/page/$page/"
        }
        val document = client.get(url).asJsoup()

        val mangas = document.select("article.ipsCmsEntries__item").map { element ->
            val a = element.selectFirst(".ipsCmsEntries__header h2 > a")!!
            SManga.create().apply {
                title = a.text()
                setUrlWithoutDomain(a.attr("href"))
                thumbnail_url = element.selectFirst(".ipsCmsEntries__thumb img")?.attr("abs:src")
            }
        }

        return MangasPage(mangas, document.hasNextPage())
    }

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("quick", "1")
            .addQueryParameter("type", "cms_records1")
            .addQueryParameter("search_and_or", "and")
            .addQueryParameter("sortby", "relevancy")
            .build()

        val response = client.get(url, ensureSuccess = false)
        if (response.code == 429) {
            response.close()
            throw Exception("Wait 1 second before retrying or login to speed up")
        }
        val document = response.asJsoup()

        // Only keep "Hentai" posts themselves (not reviews/comments/videos)
        val mangas = document.select("li.ipsStreamItem:has(.ipsStreamItem__header[data-ips-hook=itemHeader])")
            .filter { it.selectFirst(".ipsStreamItem__summary i.fa-file-text") != null }
            .map { element ->
                val a = element.selectFirst(".ipsStreamItem__title a")!!
                SManga.create().apply {
                    title = a.text()
                    setUrlWithoutDomain(a.attr("href"))
                    thumbnail_url = element.selectFirst(".ipsStreamItem__content-thumb img")?.attr("abs:src")
                }
            }

        return MangasPage(mangas, document.hasNextPage())
    }

    private fun Document.hasNextPage() = selectFirst("li.ipsPagination__next:not(.ipsPagination__inactive) > a") != null

    // =========================== Manga Details ============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val finalUrl = response.request.url.toString()
        val document = response.asJsoup()

        fun info(label: String) = document.select(".mangaInfoBox__row:has(.mangaInfoBox__label:contains($label)) .mangaInfoBox__value a")
            .map { it.text() }

        val updatedManga = manga.apply {
            title = document.selectFirst("h1")?.text() ?: title
            thumbnail_url = document.selectFirst(".mangaInfoBox__cover img")?.attr("abs:src")
            author = info("Artista").joinToString().ifEmpty { null }
            genre = (info("Tipo") + info("Cor") + info("Parody") + document.select(".mangaGenres__item a").map { it.text() })
                .distinct()
                .joinToString()
            description = document.selectFirst("article:not(.ipsEntry) div[data-role=commentContent]")?.text()?.takeIf { it != "-" }

            // Site behaves mainly as a gallery
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

        // Single post gallery structure
        val chapter = SChapter.create().apply {
            name = document.selectFirst("h1")?.text() ?: "Chapter"
            setUrlWithoutDomain(finalUrl)

            val dateStr = document.selectFirst("time")?.attr("datetime")
            if (dateStr != null) {
                date_upload = Instant.tryParse(dateStr)
            }
        }

        return SMangaUpdate(updatedManga, listOf(chapter))
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        return document.select("img.mangaReaderImage").mapIndexed { i, img ->
            val url = img.attr("abs:data-src").ifEmpty { img.attr("abs:src") }
            Page(i, imageUrl = url)
        }
    }
}
