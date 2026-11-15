package eu.kanade.tachiyomi.extension.zh.tibiucomics

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig
import eu.kanade.tachiyomi.multisrc.mccms.ResultDto
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Entities

@Source
abstract class TibiuComics : MCCMS() {

    // The category page has no genre list: its filters come from another API
    override val config = MCCMSConfig(hasCategoryPage = false)

    // Some descriptions keep their line breaks as an escaped "<br />"
    override fun SManga.cleanup() = apply { description = description?.replace("\"br /\"", "\n") }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        val id = segments.getOrNull(1)?.takeIf { it.isNotEmpty() }
        if (url.host != baseUrl.toHttpUrl().host || segments[0] !in LINK_PATHS || id == null) return null
        // The theme looks a series up by its title
        val name = client.get("$baseUrl/index.php/api/data/comicinfo?cid=$id", Fetch.XHR).parseAs<ComicInfoDto>().data?.name ?: return null
        val manga = SManga.create().apply {
            this.url = "/comic/$id"
            title = Entities.unescape(name)
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // The theme's chapter dates need a time, while the site gives the day only
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        val details = super.fetchMangaUpdate(manga, chapters, fetchDetails, fetchChapters = false).manga
        SMangaUpdate(details, chapterList.await())
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val id = manga.url.substringAfterLast('/')
        return client.get("$baseUrl/api/comic/chapter?mid=$id", Fetch.XHR)
            .parseAs<ResultDto<List<ChapterDto>>>().data
            .map { it.toSChapter() }
            .asReversed()
    }

    // The reader loads the pictures from the API
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringAfterLast('/')
        val pictures = client.get("$baseUrl/index.php/api/data/pic?cid=$id", Fetch.XHR.from(getChapterUrl(chapter))).parseAs<PicListDto>()
        // Chapters for members or for sale come without pictures
        if (pictures.code != 1) return emptyList()
        return pictures.toPages()
    }

    companion object {
        private val LINK_PATHS = setOf("comic", "chapter")
    }
}
