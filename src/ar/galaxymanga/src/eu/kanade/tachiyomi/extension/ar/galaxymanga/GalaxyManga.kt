package eu.kanade.tachiyomi.extension.ar.galaxymanga

import eu.kanade.tachiyomi.multisrc.flixscans.FlixScans
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request

class GalaxyManga : FlixScans(
    "جالاكسي مانجا",
    "https://flixscans.com",
    "ar",
    "https://ar.flixscans.site/api/v1",
) {
    override val versionId = 2

    override fun mangaDetailsRequest(manga: SManga): Request {
        val (prefix, id) = getPrefixIdFromUrl(manga.url)

        return GET("$apiUrl/series/$id/$prefix", headers)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val (prefix, id) = getPrefixIdFromUrl(manga.url)

        return GET("$apiUrl/chapters/$id-desc#$prefix", headers)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val (prefix, id) = getPrefixIdFromUrl(chapter.url)

        return GET("$apiUrl/chapters/webtoon/$id/$prefix", headers)
    }
}
