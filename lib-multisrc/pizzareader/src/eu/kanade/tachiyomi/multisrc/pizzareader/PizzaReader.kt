package eu.kanade.tachiyomi.multisrc.pizzareader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale

abstract class PizzaReader : KeiSource() {

    protected open val apiPath: String = "/api"

    protected open val dateParser: SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.ITALY)

    override val supportsLatest = true

    open val apiUrl by lazy { "$baseUrl$apiPath" }

    protected open val pizzaHeaders by lazy {
        headers.newBuilder()
            .set("Referer", baseUrl)
            .build()
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.newCall(GET("$apiUrl/comics", pizzaHeaders)).await()
            .parseAs<PizzaResultsDto>()
        val comicList = result.comics.map(::popularMangaFromObject)
        return MangasPage(comicList, hasNextPage = false)
    }

    protected open fun popularMangaFromObject(comic: PizzaComicDto): SManga = SManga.create().apply {
        title = comic.title
        thumbnail_url = comic.thumbnail
        url = comic.url
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val result = client.newCall(GET("$apiUrl/comics", pizzaHeaders)).await()
            .parseAs<PizzaResultsDto>()
        val comicList = result.comics
            .filter { comic -> comic.lastChapter != null }
            .sortedByDescending { comic -> comic.lastChapter!!.publishedOn }
            .map(::popularMangaFromObject)
            .take(10)
        return MangasPage(comicList, hasNextPage = false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val searchUrl = "$apiUrl/search/".toHttpUrl().newBuilder()
            .addPathSegment(query)
            .toString()
        val result = client.newCall(GET(searchUrl, pizzaHeaders)).await()
            .parseAs<PizzaResultsDto>()
        val comicList = result.comics.map(::popularMangaFromObject)
        return MangasPage(comicList, hasNextPage = false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val result = if (fetchDetails || fetchChapters) {
            client.newCall(GET(apiUrl + manga.url, pizzaHeaders)).await()
                .parseAs<PizzaResultDto>()
        } else {
            null
        }

        val sManga = if (fetchDetails) {
            val comic = result!!.comic!!
            SManga.create().apply {
                title = comic.title
                author = comic.author
                artist = comic.artist
                description = comic.description
                genre = comic.genres.joinToString(", ") { it.name }
                status = comic.status?.toStatus() ?: SManga.UNKNOWN
                thumbnail_url = comic.thumbnail
            }
        } else {
            manga
        }

        val sChapters = if (fetchChapters) {
            result!!.comic!!.chapters.map(::chapterFromObject)
        } else {
            chapters
        }

        return SMangaUpdate(sManga, sChapters)
    }

    protected open fun chapterFromObject(chapter: PizzaChapterDto): SChapter = SChapter.create().apply {
        name = chapter.fullTitle
        chapter_number = (chapter.chapter ?: -1).toFloat() +
            ("0." + (chapter.subchapter?.toString() ?: "0")).toFloat()
        date_upload = chapter.publishedOn.toDate()
        scanlator = chapter.teams.filterNotNull()
            .joinToString(" & ") { it.name }
        url = chapter.url
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.newCall(GET(apiUrl + chapter.url, pizzaHeaders)).await()
            .parseAs<PizzaReaderDto>()
        return result.chapter!!.pages.mapIndexed { i, page -> Page(i, "", page) }
    }

    protected open fun String.toDate(): Long = runCatching { dateParser.parse(this)?.time }
        .getOrNull() ?: 0L

    protected open fun String.toStatus(): Int = when (take(7)) {
        "In cors" -> SManga.ONGOING
        "On goin" -> SManga.ONGOING
        "Complet" -> SManga.COMPLETED
        "Conclus" -> SManga.COMPLETED
        "Conclud" -> SManga.COMPLETED
        "Licenzi" -> SManga.LICENSED
        "License" -> SManga.LICENSED
        else -> SManga.UNKNOWN
    }
}
