package eu.kanade.tachiyomi.extension.es.lmtoonline

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Lmtos : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds) { it.host == baseUrl.toHttpUrl().host }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/destacados").asJsoup()
        val mangas = document.select("section > a.group").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                title = element.selectFirst("div > h3")!!.ownText()
                url = element.attr("href").removeSuffix("/").substringAfterLast("/")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(OrderFilter(listOf("" to "recents"))))

    private val cacheMutex = Mutex()

    private var mangaCache = emptyList<Manga>()

    private var cacheTimestamp = 0L

    private val cacheDuration = 10 * 60 * 1000L

    private suspend fun fetchMangas(): List<Manga> = cacheMutex.withLock {
        val now = System.currentTimeMillis()

        if (mangaCache.isNotEmpty() && now - cacheTimestamp < cacheDuration) return@withLock mangaCache

        val series = client.get("$baseUrl/series").asJsoup().extractNextJs<MangaList>()
        mangaCache = series!!.mangas
        cacheTimestamp = now
        mangaCache
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = fetchMangas()

        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state
            ?.filter { it.state }
            ?.map { it.name }
            ?: emptyList()

        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected ?: ""
        val demographic = filters.firstInstanceOrNull<DemographicFilter>()?.selected ?: ""
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected ?: ""
        val nsfw = filters.firstInstanceOrNull<NsfwFilter>()?.selected ?: ""
        val order = filters.firstInstanceOrNull<OrderFilter>()?.selected ?: "a-z"

        val filteredMangas = mangas
            .asSequence()
            .filter { manga ->
                query.isBlank() ||
                    manga.title.contains(query, ignoreCase = true) ||
                    manga.alternativeTitles?.any {
                        it.contains(query, ignoreCase = true)
                    } == true
            }
            .filter { manga ->
                when (nsfw) {
                    "only" -> manga.isAdult
                    "hide" -> !manga.isAdult
                    else -> true
                }
            }
            .filter { manga ->
                type.isBlank() || manga.type == type
            }
            .filter { manga ->
                status.isBlank() || manga.status == status
            }
            .filter { manga ->
                demographic.isBlank() || manga.demographic == demographic
            }
            .filter { manga ->
                genres.isEmpty() || genres.all { genre ->
                    manga.genres?.contains(genre) == true
                }
            }
            .let { sequence ->
                when (order) {
                    "a-z" -> sequence.sortedBy { it.title }
                    "recents" -> sequence.sortedByDescending {
                        it.latestChapterCreatedAt
                    }
                    "views" -> sequence.sortedByDescending {
                        it.totalViews
                    }
                    else -> sequence
                }
            }
            .toList()

        val pageCount = (filteredMangas.size + PER_PAGE - 1) / PER_PAGE
        val pagedMangas = filteredMangas.drop((page - 1) * PER_PAGE).take(PER_PAGE)
        return MangasPage(pagedMangas.map { it.toSManga() }, page < pageCount)
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val details = document.extractNextJs<MangaDetails>()!!.manga.toSManga()

        val chapterList = document.extractNextJs<ChapterList>()?.let { result ->
            val mangaSlug = result.manga.slug
            result.chapters.map { it.toSChapter(mangaSlug) }
        }.orEmpty()

        return SMangaUpdate(details, chapterList)
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.get(getChapterUrl(chapter)).asJsoup().extractNextJs<ChapterPages>() ?: return emptyList()
        return result.chapter.pages.orEmpty().mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    companion object {
        const val PER_PAGE = 20
    }
}
