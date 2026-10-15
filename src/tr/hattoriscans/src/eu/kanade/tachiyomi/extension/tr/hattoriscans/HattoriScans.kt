package eu.kanade.tachiyomi.extension.tr.hattoriscans

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.text.Collator
import java.util.Locale

@Source
abstract class HattoriScans : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/latest").asJsoup()
        // Skeleton placeholders share the card class but have no title
        val mangas = document.select(".latest-poster:has(h3)").map { card ->
            SManga.create().apply {
                url = card.selectFirst("a[style]")!!.absUrl("href").toHttpUrl().pathSegments[1]
                title = card.selectFirst("h3")!!.text()
                thumbnail_url = COVER_URL_REGEX.find(card.selectFirst("a[style]")!!.attr("style"))
                    ?.groupValues?.get(1)?.let { baseUrl + it }
            }
        }
        return MangasPage(mangas, false)
    }

    // The site's list API has no filtering or paging and the catalog is small,
    // so the whole list is fetched once and filtered locally.
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = coroutineScope {
        val listDeferred = async { client.get("$baseUrl/api/manga?limit=$LIST_LIMIT").parseAs<List<MangaListItemDto>>() }
        val matchedIds = query.trim().takeIf { it.isNotBlank() }?.let { q ->
            val url = "$baseUrl/api/manga/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .build()
            client.get(url).parseAs<List<SearchItemDto>>().mapTo(HashSet()) { it.id }
        }

        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected.orEmpty()
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected.orEmpty()
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty()
        val genreMode = filters.firstInstanceOrNull<GenreModeFilter>()?.selected ?: "all"
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected ?: "newest"

        val mangas = listDeferred.await()
            .asSequence()
            .filter { matchedIds == null || it.id in matchedIds }
            .filter { type.isEmpty() || it.type == type }
            .filter { status.isEmpty() || it.status == status }
            .filter { manga ->
                if (genres.isEmpty()) return@filter true
                val names = manga.genres.map { it.name }
                if (genreMode == "any") genres.any { it in names } else genres.all { it in names }
            }
            .toList()
            .let { list ->
                when (sort) {
                    "rating" -> list.sortedByDescending { it.bayesianRating }
                    "oldest" -> list.sortedBy { it.createdAt }
                    "az" -> list.sortedWith(compareBy(TURKISH_COLLATOR) { it.title })
                    "za" -> list.sortedWith(compareByDescending(TURKISH_COLLATOR) { it.title })
                    else -> list.sortedByDescending { it.createdAt }
                }
            }
            .map { it.toSManga(baseUrl) }

        MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "manga") {
            return null
        }
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return client.get("$baseUrl/api/manga/$slug").parseAs<MangaDetailsDto>().toSManga(baseUrl)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.memo.getString("manga")}/${chapter.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (!fetchDetails) return@async manga
            client.get("$baseUrl/api/manga/${manga.url}").parseAs<MangaDetailsDto>().toSManga(baseUrl)
        }
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            client.get("$baseUrl/api/manga/${manga.url}/chapters").parseAs<ChapterListDto>()
                .chapters.map { it.toSChapter(manga.url) }
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val pages = response.extractNextJs<ChapterPagesDto> { it is JsonObject && "pages" in it }
            ?: throw Exception("Sayfa listesi bulunamadı")
        return pages.pages.mapIndexed { index, path ->
            Page(index, imageUrl = baseUrl + path)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        GenreModeFilter(),
        GenreFilter(),
    )

    companion object {
        private const val LIST_LIMIT = 1000
        private val TURKISH_COLLATOR = Collator.getInstance(Locale("tr"))
        private val COVER_URL_REGEX = Regex("""url\(["']?([^"')]+)""")
    }
}
