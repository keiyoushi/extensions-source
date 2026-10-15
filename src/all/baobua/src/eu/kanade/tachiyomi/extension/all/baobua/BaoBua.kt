package eu.kanade.tachiyomi.extension.all.baobua

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class BaoBua : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangasPage(client.get("$baseUrl/?page=$page").asJsoup())

    // ========================= Latest  =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ========================= Search  =========================
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrlOrNull()?.host) {
            return MangasPage(emptyList(), false)
        }

        val response = client.get(url)
        val finalUrl = response.request.url
        val document = response.asJsoup()

        if (document.selectFirst(IMAGE_SELECTOR) != null) {
            val manga = mangaDetailsParse(document, SManga.create()).apply {
                this.url = finalUrl.encodedPath
                title = document.selectFirst("h2.box-mt-output")!!.text()
                    .removePrefix("BaoBua.Net: ")
                    .replace(PAGE_SUFFIX_REGEX, "")
                thumbnail_url = document.selectFirst(IMAGE_SELECTOR)?.absUrl("src")
                    ?.let { normalizeImageUrl(it) }
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
            return MangasPage(listOf(manga), false)
        }

        return parseMangasPage(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()
            return parseMangasPage(client.get(url).asJsoup())
        }

        val filter = filters.firstInstance<SourceCategorySelector>()
        return filter.selectedCategory?.let {
            parseMangasPage(client.get(it.buildUrl(baseUrl, page)).asJsoup())
        } ?: getPopularManga(page)
    }

    // ========================= Details =========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val requestUrl = response.request.url.toString()
        val document = response.asJsoup()

        val chapter = SChapter.create().apply {
            chapter_number = 0F
            val absUrl = document.selectFirst("link[rel=canonical]")?.absUrl("href")
                ?: requestUrl
            url = absUrl.toHttpUrlOrNull()?.encodedPath ?: absUrl
            date_upload = Instant.tryParse(DATE_REGEX.find(document.html())?.groupValues?.get(1))
            name = "Gallery"
        }

        return SMangaUpdate(mangaDetailsParse(document, manga), listOf(chapter))
    }

    private fun mangaDetailsParse(document: Document, manga: SManga): SManga = manga.apply {
        genre = document.select(".it-categories a").joinToString { it.text() }
        status = SManga.COMPLETED
    }

    // ========================= Pages   =========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val pages = mutableListOf<Page>()
        var document = client.get(getChapterUrl(chapter)).asJsoup()

        while (true) {
            val offset = pages.size
            document.select(IMAGE_SELECTOR).mapIndexedTo(pages) { index, element ->
                Page(offset + index, imageUrl = normalizeImageUrl(element.absUrl("src")))
            }

            val nextPageUrl = document.selectFirst("a.page-numbers:contains(Next)")
                ?.absUrl("href")
                ?: break

            document = client.get(nextPageUrl).asJsoup()
        }

        return pages
    }

    // ========================= Filters =========================
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SourceCategorySelector.create(),
    )

    // ========================= Helpers =========================
    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".thcovering-video").mapNotNull { element ->
            SManga.create().apply {
                val link = element.selectFirst("a.denomination") ?: return@mapNotNull null
                url = link.absUrl("href").toHttpUrl().encodedPath
                title = link.text()
                thumbnail_url = element.selectFirst("img.xld")?.absUrl("src")
                    ?.let { normalizeImageUrl(it) }
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }

        val hasNextPage = document.selectFirst("a.page-numbers.next") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun normalizeImageUrl(url: String): String = if (WP_COM_REGEX.containsMatchIn(url)) {
        url.replace(WP_COM_REPLACE_REGEX, "https://")
            .replace("?w=640", "")
    } else {
        url
    }

    companion object {
        private const val IMAGE_SELECTOR = "div.contentme a[href^=/img.html] img"
        private val PAGE_SUFFIX_REGEX = Regex(""" \| Page \d+/\d+$""")
        private val DATE_REGEX = Regex(""""datePublished":"([^"]+)"""")
        private val WP_COM_REGEX = Regex("""^https://i\d+\.wp\.com/""")
        private val WP_COM_REPLACE_REGEX = Regex("""https://i\d+\.wp\.com/""")
    }
}
