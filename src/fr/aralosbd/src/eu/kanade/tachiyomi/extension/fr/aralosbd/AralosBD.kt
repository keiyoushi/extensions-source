package eu.kanade.tachiyomi.extension.fr.aralosbd

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.parser.Parser

@Source
abstract class AralosBD : KeiSource() {

    companion object {
        val LINK_REGEX = "\\[([^]]+)\\]\\(([^)]+)\\)".toRegex()
        val BOLD_REGEX = "\\*+\\s*([^\\*]*)\\s*\\*+".toRegex()
        val ITALIC_REGEX = "_+\\s*([^_]*)\\s*_+".toRegex()
        val ICON_REGEX = ":+[a-zA-Z]+:".toRegex()
    }

    private fun cleanString(string: String): String = Parser.unescapeEntities(string, false)
        .substringBefore("---")
        .replace(LINK_REGEX, "$2")
        .replace(BOLD_REGEX, "$1")
        .replace(ITALIC_REGEX, "$1")
        .replace(ICON_REGEX, "")
        .trim()

    // Sorted by total views (title + chapters)
    override suspend fun getPopularManga(page: Int): MangasPage = searchMangas("$baseUrl/manga/search?s=sort:allviews;limit:24;-id:3;page:${page - 1};order:desc", page)

    // A new title will always have a greater ID, so we can sort by ID. Using year would not be
    // accurate because it's the release year. Last updated is not yet in the API
    override suspend fun getLatestUpdates(page: Int): MangasPage = searchMangas("$baseUrl/manga/search?s=sort:id;limit:24;-id:3;page:${page - 1};order:desc", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = searchMangas("$baseUrl/manga/search?s=page:${page - 1};sort:id;order:desc;text:$query", page)

    private suspend fun searchMangas(url: String, page: Int): MangasPage {
        val searchResult = client.get(url).parseAs<AralosBDSearchResult>()

        return MangasPage(searchResult.mangas.map { it.toSManga(baseUrl) }, page < searchResult.pageCount)
    }

    override fun getMangaUrl(manga: SManga): String = manga.url

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val id = manga.url.toHttpUrl().queryParameter("id")!!

        val details = async {
            if (fetchDetails) {
                client.get("$baseUrl/manga/api?get=manga&id=$id").parseAs<AralosBDManga>().toSManga(manga)
            } else {
                manga
            }
        }
        val chapterList = async {
            if (fetchChapters) {
                client.get("$baseUrl/manga/api?get=chapters&manga=$id").parseAs<List<AralosBDChapter>>()
                    .filter { it.isReleased }
                    .map { it.toSChapter(baseUrl) }
            } else {
                chapters
            }
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private fun AralosBDManga.toSManga(manga: SManga) = manga.apply {
        title = mainTitle
        author = authors?.joinToString { it.name }
        description = cleanString("${this@toSManga.description}\n\n" + (fulldescription ?: ""))
        genre = tags?.joinToString { it.tag }
        thumbnail_url = "$baseUrl/$icon"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.toHttpUrl().queryParameter("id")!!

        return client.get("$baseUrl/manga/api?get=pages&chapter=$id").parseAs<AralosBDPages>()
            .links.mapIndexed { index, link ->
                Page(index, imageUrl = "$baseUrl/$link")
            }
    }
}
