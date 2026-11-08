package eu.kanade.tachiyomi.extension.en.bakaloli

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class Bakaloli : KeiSource() {

    // The whole site is one folder tree (an h5ai index): every series is a folder of the root
    override suspend fun getPopularManga(page: Int) = MangasPage(series().map { it.toSManga(baseUrl) }, false)

    override suspend fun getLatestUpdates(page: Int) = MangasPage(
        series().sortedByDescending { it.lastModified }.map { it.toSManga(baseUrl) },
        false,
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = series().map { it.toSManga(baseUrl) }.filter { it.title.contains(query.trim(), ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val folder = url.encodedPathSegments.first().ifEmpty { return null }
        return list("/$folder/").firstOrNull()?.toSManga(baseUrl)
    }

    // A series folder lists its chapter folders; a oneshot folder holds its pages itself
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val items = list(manga.url)
        val folder = items.first()
        val chapterFolders = items.drop(1).filter { it.isFolder }
        val chapterList = if (chapterFolders.isEmpty()) {
            listOf(
                folder.toSChapter(baseUrl).apply {
                    name = ONESHOT
                    chapter_number = 1f
                },
            )
        } else {
            chapterFolders.map { it.toSChapter(baseUrl) }.sortedByDescending { it.chapter_number }
        }
        return SMangaUpdate(folder.toSManga(baseUrl), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = list(chapter.url).drop(1)
        .filter { !it.isFolder && IMAGE_EXTENSIONS.any { extension -> it.href.endsWith(extension, ignoreCase = true) } }
        .sortedWith(compareBy({ it.number(baseUrl) }, { it.name(baseUrl) }))
        .mapIndexed { index, item -> Page(index, imageUrl = baseUrl + item.href) }

    private suspend fun series() = list("/").drop(1).filter { it.isFolder }

    // The first item is the folder itself, then its content
    private suspend fun list(href: String): List<ItemDto> {
        val body = ListRequestDto("get", ListItemsDto(href, 1)).toJsonRequestBody()
        return client.post("$baseUrl/", Fetch.XHR, body).parseAs<ListDto>().items
    }

    companion object {
        private const val ONESHOT = "Oneshot"
        private val IMAGE_EXTENSIONS = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".avif")
    }
}
