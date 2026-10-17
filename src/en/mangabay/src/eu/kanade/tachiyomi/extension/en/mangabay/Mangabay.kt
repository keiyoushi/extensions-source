package eu.kanade.tachiyomi.extension.en.mangabay

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
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import java.security.MessageDigest

@Source
abstract class Mangabay : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(DleGuardResolver.interceptor(baseUrl))
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            val fragment = request.url.fragment
            if (response.code == 404 && fragment != null && fragment.startsWith(FALLBACK_PREFIX)) {
                response.close()
                val fallbackUrl = fragment.removePrefix(FALLBACK_PREFIX)
                chain.proceed(
                    request.newBuilder()
                        .url(fallbackUrl)
                        .build(),
                )
            } else {
                response
            }
        }
        addNetworkInterceptor { chain ->
            val request = chain.request()
            if (!request.url.encodedPath.startsWith("/reader/")) {
                return@addNetworkInterceptor chain.proceed(request)
            }
            val mangaId = request.url.pathSegments.getOrNull(1).orEmpty()
            val existingCookies = request.header("Cookie")
                ?.split("; ")
                ?.filter { it.isNotEmpty() && !it.startsWith("adult=") }
                .orEmpty()
            val finalCookies = (existingCookies + "adult=$mangaId").joinToString("; ")
            chain.proceed(
                request.newBuilder()
                    .header("Cookie", finalCookies)
                    .build(),
            )
        }
    }

    // the guard serves a stall page for /search/ and /ComicList/ requests without an Accept header
    override fun Headers.Builder.configureHeaders() = apply {
        set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.POPULAR_STATE)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(SortFilter(SortFilter.LATEST_STATE)))

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host != LEGACY_HOST) return null
        if (!MANGA_PATH_REGEX.matches(url.encodedPath)) return null
        val manga = SManga.create().apply { setUrlWithoutDomain(url.encodedPath) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("search")
                .addPathSegment(query.trim())
                .apply {
                    if (page > 1) {
                        addPathSegment("page")
                        addPathSegment(page.toString())
                        addPathSegment("")
                    }
                }
                .build()
            return parseMangaList(client.get(url))
        }

        val urlBuilder = baseUrl.toHttpUrl().newBuilder()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val filtersApplied = genreFilter?.state?.all { it.isIgnored() } == false

        if (filtersApplied) {
            urlBuilder.addPathSegment("ComicList")
            genreFilter.addToUrl(urlBuilder)
        } else {
            urlBuilder.addPathSegment("comix")
        }
        if (page > 1) {
            urlBuilder.addPathSegment("page")
            urlBuilder.addPathSegment(page.toString())
        }
        urlBuilder.addPathSegment("")
        val url = urlBuilder.build()

        val sort = filters.firstInstance<SortFilter>()
        if (sort.getSort().isEmpty()) {
            return parseMangaList(client.get(url))
        }

        val form = FormBody.Builder()
            .add("dlenewssortby", sort.getSort())
            .add("dledirection", sort.getDirection())
            .apply {
                if (filtersApplied) {
                    add("set_new_sort", "dle_sort_xfilter")
                    add("set_direction_sort", "dle_direction_xfilter")
                } else {
                    add("set_new_sort", "dle_sort_cat_1")
                    add("set_direction_sort", "dle_direction_cat_1")
                }
            }
            .build()
        return parseMangaList(client.post(url, form))
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val entries = document.select("#dle-content > a.cd").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".cd__title")!!.text()
                val miniUrl = element.selectFirst(".cd__ph img")?.let { img ->
                    img.absUrl("data-src").ifEmpty { img.absUrl("data-adult-src") }.ifEmpty { null }
                }
                val hdUrl = hdPosterUrl(url)
                thumbnail_url = when {
                    hdUrl == null -> miniUrl
                    miniUrl == null -> hdUrl
                    else -> "$hdUrl#$FALLBACK_PREFIX$miniUrl"
                }
            }
        }
        val hasNextPage = document.selectFirst("div.pagination__pages")
            ?.children()?.last()?.tagName() == "a"
        return MangasPage(entries, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = mangaDetailsParse(document).apply { url = manga.url },
            chapters = extractData(document)?.parseAs<ChapterListDto>()?.toSChapterList().orEmpty(),
        )
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("article.page header.page__header h1")!!.text()
        thumbnail_url = document.selectFirst("div.page__poster img")?.absUrl("src")

        val altTitles = document.selectFirst("header.page__header > .page__alt")
            ?.text()
            ?.split(Regex("""\s*[;,/·]\s*"""))
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val pageText = document.selectFirst("div.page__desc-text")?.text()
        description = buildString {
            if (pageText != null) append(pageText)
            if (altTitles.isNotEmpty()) {
                if (pageText != null) append("\n\n")
                append("Alternative titles:")
                altTitles.forEach { append("\n- ").append(it) }
            }
        }.ifEmpty { null }

        author = document.infoValue("Author")
        artist = document.infoValue("Artist")

        val type = document.infoValue("Language")?.let {
            when (it.lowercase()) {
                "korean" -> "Manhwa"
                "chinese" -> "Manhua"
                "japanese" -> "Manga"
                else -> it
            }
        }
        val tagGenres = document.select(".page__list > div:has(> dt:containsOwn(Genres)) > dd > a")
            .map { it.text().replaceFirstChar(Char::uppercaseChar) }
        genre = (listOfNotNull(type) + tagGenres).joinToString()

        status = when (document.infoValue("Status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed", "finished" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled", "canceled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private fun Document.infoValue(label: String): String? = selectFirst(".page__list > div:has(> dt:containsOwn($label)) > dd")?.text()

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return document.select("section.sect--hot > .sect__content > a.poster").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                title = element.selectFirst(".poster__title")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val data = extractData(document)?.parseAs<PageListDto>() ?: return emptyList()
        return data.images.mapIndexed { idx, img ->
            val imageUrl = if (img.startsWith("http")) img.trim() else baseUrl + img.trim()
            Page(idx, imageUrl = imageUrl)
        }
    }

    private fun extractData(document: Document): String? {
        val script = document.selectFirst("script:containsData(window.__DATA__)")?.data()
            ?: return null
        return script
            .substringAfter("window.__DATA__ = ")
            .substringBefore(";window.")
            .trim()
            .removeSuffix(";")
            .trim()
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val script = client.get("$baseUrl/comix/").asJsoup()
            .selectFirst("script:containsData(window.__XFILTER__)")!!.data()
        return script
            .substringAfter("window.__XFILTER__ = ")
            .trim()
            .removeSuffix(";")
            .parseAs<XFilters>()
            .genres
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            Filter.Header("Filters are ignored for text search"),
            SortFilter(),
        )
        data?.parseAs<List<FilterValue>>()?.also { genres ->
            filters.add(GenreFilter(genres.map { it.value to it.id }))
        }
        return FilterList(filters)
    }

    private fun hdPosterUrl(mangaUrl: String): String? {
        val slug = MANGA_PATH_REGEX.matchEntire(mangaUrl)?.groupValues?.get(1) ?: return null
        val firstByte = MessageDigest.getInstance("MD5").digest(slug.toByteArray())[0].toInt() and 0xff
        val prefix = "%02x".format(firstByte)
        return "$baseUrl/uploads/posts/poster/$prefix/$slug.jpg"
    }

    companion object {
        private val MANGA_PATH_REGEX = Regex("""^/\d+-([^/]+)\.html$""")
        private const val FALLBACK_PREFIX = "fallback="
        private const val LEGACY_HOST = "read.manga-bay.org"
    }
}
