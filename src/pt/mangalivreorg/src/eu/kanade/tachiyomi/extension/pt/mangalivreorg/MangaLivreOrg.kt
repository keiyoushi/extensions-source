package eu.kanade.tachiyomi.extension.pt.mangalivreorg

import android.util.Base64
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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.get
import keiyoushi.utils.long
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaLivreOrg : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2) { it.host == API_URL.toHttpUrl().host }

    // The API rejects any Sec-Fetch-Site value now that it lives on its own host.
    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .add("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, order = "views", period = "ever")

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(page, order = "updates")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            return getMangaList(page, query = query)
        }

        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selectedValue
        if (!category.isNullOrEmpty()) {
            val url = "$API_URL/categories/series_list.json".toHttpUrl().newBuilder()
                .addQueryParameter("id_category", category)
                .build()
            val results = client.get(url).parseAs<CategoryListDto>()

            return MangasPage(results.series.map(ListItemDto::toSManga), hasNextPage = false)
        }

        val order = filters.firstInstanceOrNull<SortFilter>()?.selectedValue ?: "updates"
        val period = filters.firstInstanceOrNull<PeriodFilter>()?.selectedValue
            .takeIf { order == "views" }
        return getMangaList(page, order, period)
    }

    private suspend fun getMangaList(
        page: Int,
        order: String? = null,
        period: String? = null,
        query: String? = null,
    ): MangasPage {
        val url = "$API_URL/mangas/list".toHttpUrl().newBuilder()
            .apply {
                query?.let { addQueryParameter("filter", it) }
                order?.let { addQueryParameter("order", it) }
                period?.let { addQueryParameter("period", it) }
            }
            .addQueryParameter("page", page.toString())
            .build()

        val result = client.get(url).parseAs<MangaListDto>()
        return MangasPage(result.series.map(ListItemDto::toSManga), result.hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() !in MANGA_PATH_SEGMENTS) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotBlank) ?: return null
        val manga = SManga.create().apply { this.url = slug }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get("$API_URL/mangas/${manga.url}").parseAs<MangaDetailsDto>()

        return SMangaUpdate(
            manga = details.manga.toSManga(),
            chapters = details.chapters.map { it.toSChapter(details.manga.slug) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$API_URL/chapters/${chapter.url}"

        val response = client.get(url, nonceHeaders(), ensureSuccess = false)
        if (response.isSuccessful) {
            return response.parseAs<ChapterPagesDto>().toPageList()
        }

        response.close()
        cachedNonce = null
        return client.get(url, nonceHeaders()).parseAs<ChapterPagesDto>().toPageList()
    }

    private var cachedNonce: String? = null

    private suspend fun nonceHeaders(): Headers {
        val nonce = cachedNonce ?: fetchNonce().also { cachedNonce = it }
        return headers.newBuilder().set("X-ML-Nonce", nonce).build()
    }

    // The site keeps the nonce as a constant in its bundle and rotates it on every rebuild.
    private suspend fun fetchNonce(): String {
        val scriptUrl = client.get(baseUrl).asJsoup()
            .selectFirst("script[type=module][src*=/assets/]")
            ?.absUrl("src")
            ?: return DEFAULT_NONCE

        val script = client.get(scriptUrl).use { it.body.string() }
        val variable = NONCE_VARIABLE_REGEX.find(script)?.groupValues?.get(1) ?: return DEFAULT_NONCE

        // The minifier reuses variable names, so try every assignment until one decodes.
        return Regex("""\b$variable\s*=\s*""").findAll(script)
            .map { script.substring(it.range.last + 1).take(ASSIGNMENT_LENGTH).substringBefore(';') }
            .firstNotNullOfOrNull(::decodeNonce)
            ?: DEFAULT_NONCE
    }

    // The bundle inlines the nonce, or hides it behind base64 or a list of char codes, sometimes reversed.
    private fun decodeNonce(assignment: String): String? {
        val decoded = NONCE_LITERAL_REGEX.find(assignment)?.groupValues?.get(1)
            ?: NONCE_BASE64_REGEX.find(assignment)?.let { String(Base64.decode(it.groupValues[1], Base64.DEFAULT)) }
            ?: NONCE_CHAR_CODE_REGEX.find(assignment)?.let { match ->
                match.groupValues[1].split(",").mapNotNull { it.trim().toIntOrNull()?.toChar() }.joinToString("")
            }
            ?: return null

        return if ("reverse()" in assignment) decoded.reversed() else decoded
    }

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val genres = client.get("$API_URL/genres").parseAs<List<GenreDto>>()
        return FilterData(genres).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>() ?: return FilterList(SortFilter())

        return FilterList(
            SortFilter(),
            PeriodFilter(),
            CategoryFilter(filterData.genres),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]!!.string
        val legacyId = chapter.memo["legacyId"]!!.long
        val number = chapter.memo["number"]!!.string
        return "$baseUrl/ler/$slug/online/$legacyId/$number"
    }

    companion object {
        private const val API_URL = "https://api.mangalivre.org/api/v1"
        private val MANGA_PATH_SEGMENTS = listOf("manga", "ler")
        private const val ASSIGNMENT_LENGTH = 300

        // Matches both the plain header name and the array the bundle joins it from.
        private val NONCE_VARIABLE_REGEX = Regex("""(?:X-ML-Nonce|Nonce"]\.join\("-"\))"?]\s*=\s*(\w+)""")
        private val NONCE_LITERAL_REGEX = Regex("""["'`]([0-9a-f]{32})["'`]""")
        private val NONCE_BASE64_REGEX = Regex("""atob\(\s*["']([A-Za-z0-9+/=]+)["']""")
        private val NONCE_CHAR_CODE_REGEX = Regex("""fromCharCode\(([\d,\s]+)\)""")
        private const val DEFAULT_NONCE = "3dce95d4540e54086a970da4ea44cf46"
    }
}
