package eu.kanade.tachiyomi.extension.fr.mangamoins

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Locale

@Source
abstract class MangaMoins : KeiSource() {

    private val apiUrl get() = "$baseUrl/api/v1"

    override fun OkHttpClient.Builder.configureClient() = addInterceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 403 && request.url.toString().contains("/api/v1/")) {
            response.close()
            val homeRequest = GET(baseUrl, headers)
            network.client.newCall(homeRequest).execute().close()
            return@addInterceptor chain.proceed(request)
        }
        response
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$apiUrl/trend").parseAs<TrendResponse>()
        val mangas = result.data.map { it.toSManga() }
        return MangasPage(mangas, false) // Trend API has no pagination
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", MANGA_PAGE_LIMIT.toString())
            .build()
        return client.get(url).parseAs<MangaListResponse>().toMangasPage()
    }

    private fun MangaListResponse.toMangasPage(): MangasPage {
        val mangas = data.map { it.toSManga() }
        val hasNextPage = page * limit < total
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/explore".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", MANGA_PAGE_LIMIT.toString())
            .apply {
                if (query.isNotEmpty()) {
                    addQueryParameter("q", query)
                }
            }
            .build()
        return client.get(url).parseAs<MangaListResponse>().toMangasPage()
    }

    // ============================== Details ===============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val detailsUrl = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("manga", manga.url.toMangaSlug())
            .build()
        val result = client.get(detailsUrl).parseAs<MangaDetailsResponse>()

        val info = result.info
        val updatedManga = manga.apply {
            title = info.title.unescapeHtml()
            author = info.author.unescapeHtml()
            artist = info.author.unescapeHtml()
            description = info.description.unescapeHtml().ifBlank { null }
            status = when {
                info.status.lowercase(Locale.FRENCH).contains("en cours") -> SManga.ONGOING
                info.status.lowercase(Locale.FRENCH).contains("termin") -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = info.cover
        }

        val chapterList = result.chapters.map { ch ->
            SChapter.create().apply {
                name = buildString {
                    val chapterName = "Chapitre ${ch.num.toString().removeSuffix(".0")}"
                    append(chapterName)
                    val title = ch.title.unescapeHtml()
                    if (title.isNotBlank() && title.lowercase() != chapterName.lowercase()) {
                        append(" - ")
                        append(title)
                    }
                }
                url = ch.slug
                date_upload = ch.time * 1000L
            }
        }

        return SMangaUpdate(updatedManga, chapterList)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url.toMangaSlug()}"

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterSlug = chapter.url.removePrefix("/scan/")
        return "$baseUrl/scan/$chapterSlug"
    }

    // ============================== Pages =================================

    private var cachedSalts: List<String> = emptyList()
    private var lastSaltFetch: Long = 0

    private suspend fun getSalts(pagesBaseUrl: String): List<String> {
        val now = System.currentTimeMillis()
        if (cachedSalts.isNotEmpty() && now - lastSaltFetch < SALT_EXPIRY) {
            return cachedSalts
        }

        try {
            val scriptUrl = "$baseUrl/includes/components/js/reader.js"
            client.get(scriptUrl).use { response ->
                val script = response.body.string()

                val pathSegment = pagesBaseUrl.removeSuffix("/").substringAfterLast("/")
                val salts = mutableListOf<String>()

                val polochonMatch = POLOCHON_REGEX.find(script)
                if (polochonMatch != null) {
                    val polochonVal = polochonMatch.groupValues[1]
                    if (polochonVal.isNotEmpty() && pathSegment.contains(polochonVal)) {
                        salts.add(polochonVal)
                    }
                }

                STRINGS_REGEX.findAll(script).forEach { match ->
                    val s = match.groupValues[1].replace(ESCAPE_REGEX) { m ->
                        m.groupValues[1].toInt(16).toChar().toString()
                    }
                    if (s.length >= 3 && pathSegment.contains(s)) {
                        salts.add(s)
                    }
                }

                val result = salts.distinct().sortedByDescending { it.length }
                if (result.isNotEmpty()) {
                    cachedSalts = result
                    lastSaltFetch = now
                }
            }
        } catch (_: Exception) { }

        return cachedSalts.ifEmpty { FALLBACK_SALTS }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterSlug = chapter.url.removePrefix("/scan/")
        val url = "$apiUrl/scan".toHttpUrl().newBuilder()
            .addQueryParameter("slug", chapterSlug)
            .build()
        val data = client.get(url).parseAs<ScanResponse>()
        val salts = getSalts(data.pagesBaseUrl)

        val baseUrl = salts.fold(data.pagesBaseUrl.removeSuffix("/").removeSuffix("_b")) { url, salt ->
            url.replace(salt, "")
        }

        return (1..data.pageNumbers).map { i ->
            val pageNum = i.toString().padStart(2, '0')
            Page(i - 1, imageUrl = "$baseUrl/$pageNum.webp")
        }
    }

    companion object {
        private const val MANGA_PAGE_LIMIT = 20
        private val FALLBACK_SALTS = listOf("a1f", "Z0_9")
        private const val SALT_EXPIRY = 3 * 60 * 60 * 1000L // 3 hours
        private val POLOCHON_REGEX = Regex("""polochon['"]?\s*\]?\s*=\s*['"]([^'"]+)['"]""")

        private val STRINGS_REGEX = Regex("""['"]([^'"]*)['"]""")
        private val ESCAPE_REGEX = Regex("""\\x([a-f\d]{2})""", RegexOption.IGNORE_CASE)
    }
}
