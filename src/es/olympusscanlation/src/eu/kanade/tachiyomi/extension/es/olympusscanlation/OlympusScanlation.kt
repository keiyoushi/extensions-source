package eu.kanade.tachiyomi.extension.es.olympusscanlation

import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@Source
abstract class OlympusScanlation : KeiSource() {

    private val preferences = getPreferences()

    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> {
        val domain = client.get(DOMAIN_URL, Fetch.DOCUMENT).asJsoup().selectFirst("meta[property=og:url]")!!.attr("content")
        return listOf(client.get(domain, Fetch.DOCUMENT).use { it.request.url.toString() })
    }

    private var seriesList: List<MangaDto> = emptyList()

    private var lastFetchTime = Instant.DISTANT_PAST

    private val seriesListMutex = Mutex()

    private suspend fun fetchSeriesList() = seriesListMutex.withLock {
        val now = Clock.System.now()

        if (seriesList.isNotEmpty() && (now - lastFetchTime) < CACHE_DURATION) {
            return@withLock
        }

        val series = client.get("$baseUrl/api/series/list", Fetch.XHR).parseAs<PayloadMangaDto>()

        val comics = series.data.asSequence()
            .filter { it.type == "comic" }
            .toList()

        seriesList = comics
        lastFetchTime = now

        val newSlugMap = comics.associate { it.id to it.slug }

        preferences.slugMap += newSlugMap
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        fetchSeriesList()
        val result = client.get("$baseUrl/api/rankings?page=$page&period=total_ranking", Fetch.XHR).parseAs<RankingDto>()
        val slugMap = preferences.slugMap.toMutableMap()
        val mangaList = result.data
            .filter { it.type == "comic" }
            .map {
                slugMap[it.id] = it.slug
                it.toSManga()
            }
        preferences.slugMap = slugMap
        return MangasPage(mangaList, hasNextPage = result.hasNextPage())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        fetchSeriesList()
        val result = client.get("$baseUrl/api/new-chapters?page=$page", Fetch.XHR).parseAs<NewChaptersDto>()
        val slugMap = preferences.slugMap.toMutableMap()
        val mangaList = result.data.filter { it.type == "comic" }
            .map {
                slugMap[it.id] = it.slug
                it.toSManga()
            }
        preferences.slugMap = slugMap
        return MangasPage(mangaList, result.hasNextPage())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        fetchSeriesList()
        val filteredList = seriesList.filter { it.name.contains(query, ignoreCase = true) }
        val paginatedList = filteredList.drop((page - 1) * 20).take(20)
        val hasNextPage = page * 20 < filteredList.size
        return MangasPage(paginatedList.map { it.toSManga() }, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = preferences.slugMap[manga.url.toInt()]!!
        return "$baseUrl/series/comic-$slug"
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != "series") return null
        val series = url.pathSegments.getOrNull(1)?.takeIf { it.startsWith("comic-") } ?: return null
        val slug = series.removePrefix("comic-").takeIf(String::isNotBlank) ?: return null
        val manga = client.get("$baseUrl/api/series/$slug?type=comic", Fetch.XHR).parseAs<MangaDetailDto>().data
        preferences.slugMap += manga.id to manga.slug
        return manga.toSMangaDetails()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        fetchSeriesList()
        val mangaId = manga.url
        val slug = preferences.slugMap[mangaId.toInt()]!!

        return coroutineScope {
            val details = async {
                if (fetchDetails) {
                    client.get("$baseUrl/api/series/$slug?type=comic", Fetch.XHR)
                        .parseAs<MangaDetailDto>().data.toSMangaDetails()
                } else {
                    manga
                }
            }
            val chapterList = async {
                if (fetchChapters) fetchChapterList(slug, mangaId) else chapters
            }
            SMangaUpdate(details.await(), chapterList.await())
        }
    }

    private suspend fun fetchChapterList(mangaSlug: String, mangaId: String): List<SChapter> {
        val data = fetchChapterPage(mangaSlug, 1)
        val result = data.data.toMutableList()
        var page = 2
        while (data.meta.total > result.size) {
            val newData = fetchChapterPage(mangaSlug, page)
            result += newData.data
            page += 1
        }
        return result.map { it.toSChapter(mangaId) }
    }

    private suspend fun fetchChapterPage(mangaSlug: String, page: Int): PayloadChapterDto = client.get("$baseUrl/api/series/$mangaSlug/chapters?page=$page&direction=desc&type=comic", Fetch.XHR)
        .parseAs<PayloadChapterDto>()

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaId = chapter.url.substringBefore("/")
        val chapterId = chapter.url.substringAfter("/")
        val mangaSlug = preferences.slugMap[mangaId.toInt()]!!
        return "$baseUrl/capitulo/$chapterId/comic-$mangaSlug"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("main.strip div.panel > img")
        .mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }

    private var slugMapCache: Map<Int, String>? = null
    private var SharedPreferences.slugMap: Map<Int, String>
        get() {
            slugMapCache?.let { return it }
            val json = getString(SLUG_MAP, "{}")!!
            slugMapCache = try {
                json.parseAs<Map<Int, String>>()
            } catch (_: SerializationException) {
                emptyMap()
            }
            return slugMapCache!!
        }
        set(map) {
            slugMapCache = map
            edit().putString(SLUG_MAP, map.toJsonString()).apply()
        }

    companion object {
        private const val DOMAIN_URL = "https://olympus.pages.dev"

        private const val SLUG_MAP = "slugMap"

        private val CACHE_DURATION = 1.hours
    }
}
