package eu.kanade.tachiyomi.extension.tr.hattorimanga

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.format.DateTimeFormatter

@Source
abstract class HattoriManga : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(4)

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val dto = client.get("$baseUrl/latest-chapters?page=$page").parseAs<HMLatestUpdateDto>()

        val mangas = dto.chapters.map { chapter ->
            SManga.create().apply {
                title = chapter.manga.title
                thumbnail_url = "$baseUrl/storage/${chapter.manga.thumbnail}"
                url = "/manga/${chapter.manga.slug}"
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, dto.hasMorePages)
    }

    // Popular
    override suspend fun getPopularManga(page: Int) = parsePopular(client.get("$baseUrl/manga?page=$page"))

    private fun parsePopular(response: Response) = response.asJsoup().let {
        val mangas = it.select(".product-card.grow-box").map { it ->
            SManga.create().apply {
                title = it.selectFirst("h5")!!.text()
                thumbnail_url = it.selectFirst(".img-con")?.absUrl("data-setbg")
                genre = it.select(".product-card-con ul li").joinToString { it.text() }
                val script = it.attr("onclick")
                setUrlWithoutDomain(REGEX_MANGA_URL.find(script)!!.groups[1]!!.value)
            }
        }
        val hasNextPage = it.selectFirst(".pagination .page-item:last-child:not(.disabled)") != null
        MangasPage(mangas, hasNextPage)
    }

    // Search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.count(String::isNotBlank) < 2) return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = if (query.isNotBlank()) {
        getSearchMangas(query).parseAs<List<SearchManga>>().map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = "$baseUrl/storage/${it.thumbnail}"
                url = "/manga/${it.slug}"
            }
        }.let {
            MangasPage(it, false)
        }
    } else {
        val url = "$baseUrl/manga-index".toHttpUrl().newBuilder()
        val selection = filters.filterIsInstance<GenreList>()
            .flatMap { it.state }
            .filter { it.state }

        url.addQueryParameter("page", "$page")

        if (selection.isNotEmpty()) {
            selection.forEach { genre ->
                url.addQueryParameter("genres[]", genre.name)
            }
        }
        parsePopular(client.get(url.build()))
    }

    // Details + Chapters
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = coroutineScope {
        SMangaUpdate(
            async { if (fetchDetails) getDetails(manga) else manga }.await(),
            async { if (fetchChapters) getChapterList(manga) else chapters }.await(),
        )
    }

    private suspend fun getDetails(manga: SManga) = SManga.create().apply {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        with(doc) {
            title = selectFirst("h3")!!.text()
            thumbnail_url = selectFirst(".set-bg")?.absUrl("data-setbg")
            description = selectFirst(".anime-details-text p")?.text()
            author = selectFirst(".anime-details-widget li:has(span:contains(Yazar))")?.ownText()
            artist = selectFirst(".anime-details-widget li:has(span:contains(Çizer))")?.ownText()

            selectFirst(".anime-details-widget li:has(span:contains(Durum))")?.ownText()?.let {
                status = when (it.lowercase()) {
                    "devam ediyor" -> SManga.ONGOING
                    "tamamlandı" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }

            genre = selectFirst(".anime-details-widget li:has(span:contains(Etiketler))")
                ?.ownText()
                ?.split(",")
                ?.map { it.trim() }
                ?.joinToString()
            setUrlWithoutDomain(location())
        }
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val slug = manga.url.substringAfterLast('/')
        val chapters = mutableListOf<SChapter>()
        var page = 1

        do {
            val dto = client.get(
                "$baseUrl/load-more-chapters/$slug?page=$page",
            ).parseAs<HMChapterDto>()

            chapters += dto.chapters.map {
                SChapter.create().apply {
                    name = it.title
                    date_upload = dateFormat.tryParseDate(it.date)
                    url = "${manga.url}/${it.chapterSlug}"
                }
            }
            page = dto.nextPage()
        } while (dto.hasNextPage())

        return chapters
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter) = client.get(getChapterUrl(chapter))
        .asJsoup().select(".image-wrapper img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("data-src"))
        }.takeIf { it.isNotEmpty() } ?: throw Exception("Oturum açmanız, WebView'ı açmanız ve oturum açmanız gerekir")

    // Filters
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get("$baseUrl/manga-index").asJsoup()
        .select(".tags-blog a").map { it.text() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            val genres = data?.parseAs<List<String>>().orEmpty()

            if (genres.isNotEmpty()) add(GenreList("Türler", genres))
        },
    )

    internal class GenreList(
        title: String,
        genres: List<String>,
    ) : Filter.Group<Filter.CheckBox>(
        title,
        genres.map { object : Filter.CheckBox(it) {} },
    )

    // Utils
    private var csrfToken: String? = null

    private fun Response.isPageExpired() = code == 419

    private suspend fun getCsrftoken() = csrfToken ?: client.get(
        baseUrl,
    ).asJsoup().selectFirst("meta[name=csrf-token]")!!.attr("content")
        .also { csrfToken = it }

    private suspend fun searchForm(query: String) = FormBody.Builder()
        .add("_token", getCsrftoken())
        .add("query", query)
        .build()

    private suspend fun getSearchMangas(query: String): Response {
        val headers = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        var response = client.post("$baseUrl/manga/search", headers, searchForm(query), ensureSuccess = false)

        if (response.isPageExpired()) {
            response.close()
            csrfToken = null
            response = client.post("$baseUrl/manga/search", headers, searchForm(query))
        }

        return response
    }

    private companion object {
        val REGEX_MANGA_URL = """='([^']+)""".toRegex()
        val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    }
}
