package eu.kanade.tachiyomi.extension.ar.mangatales

import eu.kanade.tachiyomi.multisrc.gmanga.Gmanga
import eu.kanade.tachiyomi.multisrc.gmanga.TagFilterData
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response

class MangaTales : Gmanga(
    "Manga Tales",
    "https://www.mangatales.com",
    "ar",
    "https://media.mangatales.com",
) {
    override fun createThumbnail(mangaId: String, cover: String): String {
        return "$cdnUrl/uploads/manga/cover/$mangaId/large_$cover"
    }

    override fun getTypesFilter() = listOf(
        TagFilterData("1", "عربية", Filter.TriState.STATE_INCLUDE),
        TagFilterData("2", "إنجليزي", Filter.TriState.STATE_INCLUDE),
    )

    override fun chaptersRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/mangas/$mangaId", headers)
    }

    override fun chaptersParse(response: Response): List<SChapter> {
        val releases = response.parseAs<ChapterListDto>().mangaReleases

        return releases.map { it.toSChapter() }
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.asJsoup()
            .select(".js-react-on-rails-component").html()
            .parseAs<ReaderDto>()

        return data.readerDataAction.readerData.release.pages
            .mapIndexed { idx, img ->
                Page(idx, imageUrl = "$cdnUrl/uploads/releases/$img?ak=${data.globals.mediaKey}")
            }
    }
}
