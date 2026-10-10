package eu.kanade.tachiyomi.extension.th.bullymanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document

@Source
abstract class BullyManga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/").asJsoup()
        return MangasPage(parsePopular(document), hasNextPage = false)
    }

    // ========================= Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get("$baseUrl/genres/all/$page").asJsoup().parseMangaList()

    // ========================= Search =========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .addQueryParameter("page", page.toString())
                .build()
            return client.get(url).asJsoup().parseMangaList()
        }

        val genre = filters.firstInstance<GenreFilter>().selected
        return client.get("$baseUrl/genres/${genre.slug}/$page").asJsoup().parseMangaList()
    }

    // ========================= Filters =========================
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(GenreFilter())

    // ========================= URL =========================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        // chapter URLs point at the same series, strip the "-epNNNN" suffix
        val path = CHAPTER_PATH_REGEX.find(url.encodedPath)?.groupValues?.get(1) ?: url.encodedPath
        val document = client.get("$baseUrl$path").asJsoup()
        if (document.selectFirst("img.sh-cover") == null) return null

        return document.parseDetails(
            SManga.create().apply {
                this.url = path
                initialized = true
            },
        )
    }

    // ========================= Details =========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        // details and chapters come from the same page, so parse both regardless of the flags
        return SMangaUpdate(
            manga = document.parseDetails(manga),
            chapters = document.parseChapters(),
        )
    }

    // ========================= Pages =========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val imageMap = IMAGE_MAP_REGEX.find(document.html())?.groupValues?.get(1).orEmpty()

        return IMAGE_URL_REGEX.findAll(imageMap)
            .mapIndexed { index, match -> Page(index, imageUrl = baseUrl + match.groupValues[1]) }
            .toList()
    }

    // ========================= Parsers =========================
    private fun parsePopular(document: Document): List<SManga> = document.select("a.hit-card").mapNotNull { card ->
        val path = card.absUrl("href").toHttpUrlOrNull()?.encodedPath ?: return@mapNotNull null
        val title = card.selectFirst(".hit-title")?.text() ?: return@mapNotNull null

        SManga.create().apply {
            url = path
            this.title = title
            thumbnail_url = card.selectFirst("img.hit-img")?.absUrl("data-src")
        }
    }

    private fun Document.parseMangaList(): MangasPage {
        val mangas = select("div.mc-card").mapNotNull { card ->
            val link = card.selectFirst("a.mc-title") ?: return@mapNotNull null
            val path = link.absUrl("href").toHttpUrlOrNull()?.encodedPath ?: return@mapNotNull null

            SManga.create().apply {
                url = path
                title = link.text()
                thumbnail_url = card.selectFirst("img.mc-img")?.absUrl("data-src")
            }
        }

        return MangasPage(mangas, selectFirst("#nextBtn")?.hasAttr("disabled") == false)
    }

    private fun Document.parseDetails(manga: SManga): SManga = manga.apply {
        val dto = selectFirst("script[type=application/ld+json]")?.data()?.parseAs<SeriesDto>()

        title = dto?.name ?: title
        description = dto?.description
        thumbnail_url = selectFirst("img.sh-cover")?.absUrl("src") ?: dto?.image
        genre = select(".sh-genre").joinToString { it.text() }
        status = when (selectFirst(".sh-badge-status")?.text()?.trim()?.lowercase()) {
            "completed" -> SManga.COMPLETED
            "on going", "ongoing" -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.parseChapters(): List<SChapter> = select("a.sh-ep").mapNotNull { element ->
        val path = element.absUrl("href").toHttpUrlOrNull()?.encodedPath ?: return@mapNotNull null

        SChapter.create().apply {
            url = path
            name = element.selectFirst(".sh-ep-label")?.text() ?: "ตอนที่ ${element.attr("data-title")}"
            chapter_number = element.attr("data-title").toFloatOrNull() ?: -1f
        }
    }.sortedByDescending { it.chapter_number }

    companion object {
        private val CHAPTER_PATH_REGEX = Regex("""^(/.+)-ep\d+$""")
        private val IMAGE_MAP_REGEX = Regex("""const IMAGE_MAP\s*=\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        private val IMAGE_URL_REGEX = Regex(""""(/[^"]+)"""")
    }
}
