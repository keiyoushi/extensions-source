package eu.kanade.tachiyomi.extension.es.lmtoonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Lmtos : HttpSource() {
    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .rateLimit(3, 1.seconds) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/destacados", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("section > a.group").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                title = element.selectFirst("div > h3")!!.ownText()
                url = element.attr("href").removeSuffix("/").substringAfterLast("/")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = fetchSearchManga(page, "", FilterList(OrderFilter(listOf("" to "recents"))))

    @Volatile
    private var mangaCache = emptyList<Manga>()

    @Volatile
    private var cacheTimestamp = 0L

    private val cacheDuration = 10 * 60 * 1000L

    @Synchronized
    private fun fetchMangas() {
        val now = System.currentTimeMillis()

        if (mangaCache.isNotEmpty() && now - cacheTimestamp < cacheDuration) return

        val series = client.newCall(GET("$baseUrl/series", headers)).execute().extractNextJs<MangaList>()
        mangaCache = series!!.mangas
        cacheTimestamp = now
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        fetchMangas()
        return Observable.just(searchMangaParse(page, query, filters))
    }

    private fun searchMangaParse(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state
            ?.filter { it.state }
            ?.map { it.name }
            ?: emptyList()

        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected ?: ""
        val demographic = filters.firstInstanceOrNull<DemographicFilter>()?.selected ?: ""
        val type = filters.firstInstanceOrNull<TypeFilter>()?.selected ?: ""
        val nsfw = filters.firstInstanceOrNull<NsfwFilter>()?.selected ?: ""
        val order = filters.firstInstanceOrNull<OrderFilter>()?.selected ?: "a-z"

        val filteredMangas = mangaCache
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

    override fun getFilterList() = getFilters()

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/manga/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.extractNextJs<MangaDetails>()!!
        return result.manga.toSManga()
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.extractNextJs<ChapterList>() ?: return emptyList()
        val mangaSlug = result.manga.slug
        return result.chapters.map { it.toSChapter(mangaSlug) }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/manga/${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/manga/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = response.extractNextJs<ChapterPages>() ?: return emptyList()
        return result.chapter.pages.orEmpty().mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        const val PER_PAGE = 20
    }
}
