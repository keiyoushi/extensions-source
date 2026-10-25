package eu.kanade.tachiyomi.extension.en.mangalix

import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.Locale

@Source
abstract class Mangalix : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        val host = baseUrl.toHttpUrl().host
        rateLimit(2) { it.host == host }
    }

    private val imageHeaders: Headers
        get() = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
            .build()

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        loadCatalog().map { it.toSManga(baseUrl) },
        hasNextPage = false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = MangasPage(
        loadCatalog()
            .sortedByDescending(MangaDto::latestTimestamp)
            .map { it.toSManga(baseUrl) },
        hasNextPage = false,
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val statusFilter = filters.firstInstanceOrNull<StatusFilter>()
        val sortFilter = filters.firstInstanceOrNull<SortFilter>()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val terms = query.trim().lowercase(Locale.ROOT)
            .split(WHITESPACE_REGEX)
            .filter(String::isNotEmpty)

        var result = loadCatalog()
            .filter { manga -> terms.isEmpty() || manga.matchesTerms(terms) }
            .filter { manga -> statusFilter?.matches(manga.status) != false }
            .filter { manga -> genreFilter?.matches(manga.genres) != false }

        result = when (sortFilter?.selected) {
            SortFilter.LATEST -> result.sortedByDescending(MangaDto::latestTimestamp)
            SortFilter.RATING -> result.sortedByDescending(MangaDto::rating)
            SortFilter.TITLE -> result.sortedByDescending { it.title.lowercase(Locale.ROOT) }
            SortFilter.RELEASE_YEAR -> result.sortedByDescending(MangaDto::releaseYear)
            else -> result
        }

        if (sortFilter?.ascending == true) result = result.reversed()

        return MangasPage(result.map { it.toSManga(baseUrl) }, hasNextPage = false)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        StatusFilter(),
        SortFilter(),
        GenreFilter(),
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "manga") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        return loadCatalog().firstOrNull { it.slug == slug }?.toSManga(baseUrl)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url

        val updatedManga = if (fetchDetails) {
            async {
                loadCatalog().firstOrNull { it.slug == slug }?.toSManga(baseUrl) ?: manga
            }
        } else {
            null
        }

        val updatedChapters = if (fetchChapters) {
            async {
                readChapters(slug).distinctBy { it.id to it.number }.map { chapter ->
                    SChapter.create().apply {
                        val number = chapter.number.toString().removeSuffix(".0")
                        url = chapter.id
                        memo = buildJsonObject {
                            put("slug", slug)
                            put("number", number)
                        }
                        name = chapter.title.ifBlank { "Chapter $number" }
                        chapter_number = chapter.number.toFloat()
                        date_upload = chapter.releaseDate.toMangaTimestamp()
                    }
                }
            }
        } else {
            null
        }

        SMangaUpdate(
            manga = updatedManga?.await() ?: manga,
            chapters = updatedChapters?.await() ?: chapters,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]!!.string
        val number = chapter.memo["number"]!!.string
        return "$baseUrl/manga/$slug/chapter/$number"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.memo["slug"]?.string ?: throw Exception("Refresh chapter list")
        val number = chapter.memo["number"]!!.string.toDouble()
        val chapterId = chapter.url

        return readChapters(slug)
            .firstOrNull { it.id == chapterId && it.number == number }
            ?.pages
            .orEmpty()
            .mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl.resolveImageUrl())
            }
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, imageHeaders)

    private suspend fun loadCatalog(): List<MangaDto> {
        val document = client.get(baseUrl).asJsoup()
        val scriptUrl = document.selectFirst("link[rel=modulepreload][href*=/assets/manga-data-]")
            ?.absUrl("href")
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("Catalog script not found")

        val script = client.get(scriptUrl).body.string()

        CATALOG_START_REGEX.findAll(script).forEach { match ->
            val element = runCatching {
                JsLiteralParser(script, match.range.first).parse()
            }.getOrNull() ?: return@forEach
            val candidate = runCatching {
                element.parseAs<List<MangaDto>>()
            }.getOrNull() ?: return@forEach

            if (candidate.isNotEmpty() && candidate.all { it.slug.isNotBlank() && it.title.isNotBlank() }) {
                return candidate.distinctBy(MangaDto::slug)
            }
        }

        throw IOException("Manga catalog not found")
    }

    private fun MangaDto.matchesTerms(terms: List<String>): Boolean {
        val searchable = "$title $author $slug".lowercase(Locale.ROOT)
        return terms.all(searchable::contains)
    }

    private suspend fun readChapters(slug: String): List<ChapterDto> = client.get("$baseUrl/chapters/$slug.json").parseAs()

    private fun String.resolveImageUrl(): String = when {
        startsWith("\$TEMP") -> replaceFirst("\$TEMP", "https://temp.compsci88.com")
        startsWith("\$HOT") -> replaceFirst("\$HOT", "https://scans-hot.planeptune.us")
        startsWith("\$LST") -> replaceFirst("\$LST", "https://scans.lastation.us")
        startsWith("\$LOW") -> replaceFirst("\$LOW", "https://official.lowee.us")
        startsWith("\$MFK") -> replaceFirst("\$MFK", "https://images.mangafreak.me")
        startsWith("/cdn-readmanga/") -> "https://cdn.readmanga.cc${this.removePrefix("/cdn-readmanga")}"
        startsWith("//") -> "https:$this"
        startsWith("/") -> "$baseUrl$this"
        else -> this
    }

    companion object {
        private val CATALOG_START_REGEX = Regex("""\[\{id\s*:""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
