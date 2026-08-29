package eu.kanade.tachiyomi.multisrc.pizzareader

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

abstract class PizzaReader : KeiSource() {

    protected open val apiPath: String = "/api"

    open val apiUrl get() = "$baseUrl$apiPath"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val result = client.get("$apiUrl/comics")
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
        val result = client.get("$apiUrl/comics")
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
        val result = client.get(searchUrl)
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
        val result = client.get(apiUrl + manga.url)
            .parseAs<PizzaResultDto>()

        val comic = result.comic!!
        val sManga = SManga.create().apply {
            title = comic.title
            author = comic.author
            artist = comic.artist
            description = comic.description
            genre = comic.genres.joinToString(", ") { it.name }
            status = comic.status?.toStatus() ?: SManga.UNKNOWN
            thumbnail_url = comic.thumbnail
        }

        val sChapters = comic.chapters.map(::chapterFromObject)

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
        val result = client.get(apiUrl + chapter.url)
            .parseAs<PizzaReaderDto>()
        return result.chapter!!.pages.mapIndexed { i, page -> Page(i, "", page) }
    }

    protected open fun String.toDate(): Long = Instant.parseOrNull(this)?.toEpochMilliseconds() ?: 0L

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
