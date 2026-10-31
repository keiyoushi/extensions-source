package eu.kanade.tachiyomi.extension.en.luxmanga

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class LuxManga :
    KeiSource(),
    ConfigurableSource {

    override val supportsFilterFetching = true

    private val preferences by getPreferencesLazy()

    private val apiUrl = "https://api.luxmanga.org".toHttpUrl()

    private fun api(vararg segments: String): HttpUrl.Builder = apiUrl.newBuilder().apply {
        segments.forEach(::addPathSegment)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchRanking("most-read", "20")

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchRanking("latest-updates", "24")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.slug
        val url = when {
            query.isNotBlank() -> api("works").addQueryParameter("search", query.trim()).addQueryParameter("limit", "24")
            genre != null -> api("genres", genre, "works").addQueryParameter("limit", "20")
            else -> api("works").addQueryParameter("limit", "24")
        }.addQueryParameter("page", page.toString()).build()

        val list = client.get(url).parseAs<WorkList>()
        return MangasPage(list.data.map { it.toSManga(apiUrl) }, page < list.meta.totalPages)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val lookupUrl = when (url.pathSegments[0]) {
            "series" -> api("resolve", "work", id)
            "reader" -> api("chapters", id)
            else -> return null
        }.build()

        val workId = try {
            client.get(lookupUrl).parseAs<WorkId>().workId
        } catch (e: HttpException) {
            if (e.code != 404) throw e
            return null
        }
        return getDetails(workId).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async { if (fetchDetails) getDetails(manga.url) else manga }
        val chapterList = async { if (fetchChapters) getChapters(manga.url) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/reader/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = client.get(api("chapters", chapter.url).build()).parseAs<Chapter>().images
        return images.mapIndexed { index, path ->
            Page(index, imageUrl = apiUrl.resolve(path)!!.toString())
        }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get(api("genres").build())
        .parseAs<GenreList>().data
        .filter { it.workCount > 0 }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Genre>>() ?: return FilterList()
        return FilterList(
            Filter.Header("Text search ignores the genre"),
            GenreFilter(genres.map { it.nameEn to it.slug }),
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_PAID
            title = "Show paid chapters"
            summary = "List chapters the site sells for points, marked 🔒. They can't be read here."
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private suspend fun fetchRanking(path: String, limit: String): MangasPage {
        val url = api("works", path).addQueryParameter("limit", limit).build()
        val works = client.get(url).parseAs<RankingList>().data
        return MangasPage(works.map { it.toSManga(apiUrl) }, false)
    }

    private suspend fun getDetails(id: String): SManga = coroutineScope {
        val work = async { client.get(api("works", id).build()).parseAs<Work>() }
        val metadata = async { client.get(api("works", id, "metadata").build()).parseAs<Metadata>() }
        val genres = async { client.get(api("works", id, "genres").build()).parseAs<GenreList>() }
        work.await().toSManga(apiUrl, metadata.await(), genres.await().data)
    }

    private suspend fun getChapters(id: String): List<SChapter> = coroutineScope {
        val first = fetchChapterPage(id, 1)
        val rest = (2..first.meta.totalPages).map { page -> async { fetchChapterPage(id, page) } }
        val showPaid = preferences.getBoolean(PREF_SHOW_PAID, false)

        (first.data + rest.awaitAll().flatMap { it.data })
            .filter { showPaid || it.coins == 0 }
            .map { it.toSChapter() }
    }

    private suspend fun fetchChapterPage(id: String, page: Int): ChapterList {
        val url = api("works", id, "chapters")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "100")
            .addQueryParameter("order", "desc")
            .build()
        return client.get(url).parseAs()
    }

    companion object {
        private const val PREF_SHOW_PAID = "show_paid_chapters"
    }
}
