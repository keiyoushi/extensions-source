package eu.kanade.tachiyomi.extension.en.cutiecomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class CutieComics : KeiSource() {
    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2) { it.host == baseUrlHost }
    }

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/page/$page").asJsoup())

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("#dle-content > div.w25").map { element ->
            SManga.create().apply {
                with(element.selectFirst("strong.field-content > a")!!) {
                    title = ownText()
                    setUrlWithoutDomain(attr("href"))
                }
                thumbnail_url = element.selectFirst("a > img")?.absUrl("src")
            }
        }

        val hasNextPage = document.selectFirst(".navigation > a > i.fa-angle-right") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost) return null
        val id = url.pathSegments.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return null

        val response = client.get("$baseUrl/$id")
        val responseUrl = response.request.url.toString()
        return parseDetails(response.asJsoup()).apply {
            setUrlWithoutDomain(responseUrl)
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        require(query.isNotBlank() && query.length >= 4) { "Invalid search! It should have at least 4 non-blank characters." }
        val body = FormBody.Builder()
            .add("do", "search")
            .add("subaction", "search")
            .add("full_search", "0")
            .add("search_start", "$page")
            .add("result_from", "${(page - 1) * 20 + 1}")
            .add("story", query)
            .build()
        return parseMangaList(client.post("$baseUrl/index.php?do=search", body).asJsoup())
    }

    // =========================== Manga Details ============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) {
            parseDetails(client.get(getMangaUrl(manga)).asJsoup()).apply { url = manga.url }
        } else {
            manga
        }

        val chapter = SChapter.create().apply {
            url = manga.url
            chapter_number = 1F
            name = "Chapter"
        }

        return SMangaUpdate(updatedManga, listOf(chapter))
    }

    private fun parseDetails(document: Document) = SManga.create().apply {
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE

        title = document.selectFirst("h1#page-title")!!.text()
        thumbnail_url = document.selectFirst("div.galery > img")?.absUrl("src")
        genre = document.select("h3.field-label ~ span").joinToString { it.text() }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.galery > img").mapIndexed { index, item ->
            Page(index, imageUrl = item.absUrl("src"))
        }
    }
}
