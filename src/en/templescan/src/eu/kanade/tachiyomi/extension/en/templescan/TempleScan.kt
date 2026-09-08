package eu.kanade.tachiyomi.extension.en.templescan

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

@Source
abstract class TempleScan : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1)
    }

    private val rscHeaders get() = headersBuilder()
        .set("rsc", "1")
        .build()

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", OrderFilter.POPULAR)

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", OrderFilter.LATEST)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val data = client.get("$baseUrl/comics", rscHeaders).extractNextJs<List<BrowseSeries>>()!!
        return parseDirectory(data, query, filters)
    }

    private fun parseDirectory(series: List<BrowseSeries>, query: String, filters: FilterList): MangasPage {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.selected
        val mangaList = series.filter { series ->

            val queryFilter = query.isBlank() ||
                series.title.contains(query, ignoreCase = true) ||
                series.alternativeNames?.contains(query, ignoreCase = true) == true

            val statusFilter = status == null || series.status == status

            queryFilter && statusFilter
        }.let {
            val order = filters.firstInstanceOrNull<OrderFilter>()?.selected

            when (order) {
                "updated" -> it.sortedByDescending { series -> series.updated }
                "created" -> it.sortedByDescending { series -> series.created }
                "views" -> it.sortedByDescending { series -> series.views }
                else -> it
            }
        }

        return MangasPage(
            mangas = mangaList.map { it.toSManga() },
            hasNextPage = false,
        )
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "comic") {
            return null
        }

        val mangaUrl = "/comic/${url.pathSegments[1]}"
        val manga = SManga.create().apply {
            this.url = mangaUrl
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
                this.url = mangaUrl
            }
    }

    // =========================== Manga Updates ============================

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = client.get(baseUrl + manga.url, rscHeaders).extractNextJs<SeriesDetails>()!!

        val manga = SManga.create().apply {
            url = "/comic/${details.slug}"
            title = details.title
            thumbnail_url = details.thumbnail
            status = when (details.status) {
                "Ongoing" -> SManga.ONGOING
                "Hiatus" -> SManga.ON_HIATUS
                "Completed" -> SManga.COMPLETED
                "Canceled" -> SManga.CANCELLED
                "Dropped" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            author = details.author
            artist = details.studio
            // Sometimes site adds #tags at the end of description
            // Site can use any word to indicate tags, I saw at least: "Tags:", "Keywords:", TAGS
            val cleanDescription = if (details.description?.contains("#") == true) {
                details.description.substringBefore("#").replace(LAST_WORD_REGEX, "").trim()
            } else {
                details.description.toString()
            }
            description = buildString {
                append(Jsoup.clean(cleanDescription, Safelist.none()))
                details.alternativeNames?.takeIf { it.isNotBlank() }?.let {
                    append("\n\n")
                    append("Alternative Name: $it\n")
                }
            }
            genre = buildList {
                add(details.badge)
                add(details.year)
                if (details.adult) {
                    add("Adult")
                }
                details.tags?.map { it.tag.name }?.let { addAll(it) }
                details.description?.takeIf { it.contains("#") }?.let { desc ->
                    addAll(TEXT_TAGS_REGEX.findAll(desc).map { it.groupValues[1] })
                }
            }.filterNotNull().joinToString()
        }

        val chapters = details.seasons?.flatMap { season ->
            season.chapters.filter {
                it.price == 0
            }.map { chapter ->
                SChapter.create().apply {
                    url = "/comic/${manga.url.substringAfterLast('/')}/${chapter.slug}"
                    name = buildString {
                        append(chapter.name)
                        if (!chapter.title.isNullOrBlank()) {
                            append(": ", chapter.title)
                        }
                    }
                    date_upload = chapter.created
                }
            }
        } ?: chapters

        return SMangaUpdate(manga, chapters)
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(baseUrl + chapter.url, rscHeaders).extractNextJs<PagesList>() ?: return emptyList()
        return data.pages.mapIndexed { idx, url ->
            Page(idx, imageUrl = url)
        }
    }

    companion object {
        private val TEXT_TAGS_REGEX = """(?i)#(\w+)""".toRegex()
        private val LAST_WORD_REGEX = """[\w\s]+:?\s*$""".toRegex()
    }
}
