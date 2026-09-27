package eu.kanade.tachiyomi.multisrc.monochrome

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

abstract class MonochromeCMS : KeiSource() {
    override val supportsLatest = false

    protected open val apiUrl: String
        get() = baseUrl.replaceFirst("://", "://api.")

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = client.get("$apiUrl/manga?limit=10&offset=${10 * (page - 1)}&title=$query")
        .parseAs<Results>().let {
            MangasPage(it.map(::mangaFromAPI), it.hasNext)
        }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2) return null
        return mangaFromAPI(client.get("$apiUrl/manga/${url.pathSegments[1]}").parseAs())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val chapterList = client.get("$apiUrl/manga/${manga.url}/chapters").parseAs<List<Chapter>>().map { ch ->
            SChapter.create().apply {
                name = ch.title
                url = manga.url + ch.parts
                chapter_number = ch.number
                date_upload = ch.timestamp
                scanlator = ch.scanGroup
            }
        }
        return SMangaUpdate(manga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (uuid, version, length) = chapter.url.split('|')
        return IntRange(1, length.toInt()).map {
            Page(it, "", "$apiUrl/media/$uuid/$it.jpg?version=$version")
        }
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/chapters/${chapter.url.subSequence(37, 73)}"

    private fun mangaFromAPI(manga: Manga) = SManga.create().apply {
        url = manga.id
        title = manga.title
        author = manga.author
        artist = manga.artist
        description = manga.description
        thumbnail_url = apiUrl + manga.cover
        status = when (manga.status) {
            "ongoing", "hiatus" -> SManga.ONGOING
            "completed", "cancelled" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}
