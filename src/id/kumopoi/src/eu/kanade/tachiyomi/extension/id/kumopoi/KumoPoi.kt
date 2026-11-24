package eu.kanade.tachiyomi.extension.id.kumopoi

import eu.kanade.tachiyomi.multisrc.aesir.Aesir
import eu.kanade.tachiyomi.multisrc.aesir.ChapterDto
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.toJsonElement

@Source
abstract class KumoPoi : Aesir() {

    override val apiUrl = "https://api.kumopoi.com/api/v1"

    override val cdnUrl = "https://kumo.gorae.my.id"

    // The site's siteURL
    override val siteHosts get() = super.siteHosts + "kumopoi.com"

    // Stored chapter urls carry the chapter number, with the chapter id in the fragment
    override fun chapterUrl(comicSlug: String, chapter: ChapterDto) = "/comic/$comicSlug/chapter/${chapter.number.trim()}#${chapter.id}"

    override fun chapterId(chapter: SChapter) = chapter.url.substringAfterLast('#', "").ifEmpty { error("Please refresh chapter list") }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url.substringBeforeLast('/')}/${chapterId(chapter)}"

    override suspend fun fetchFilterData() = client.get("$baseUrl/comics", Fetch.DOCUMENT)
        .extractNextJs<GenresDto>()!!
        .genres
        .toJsonElement()
}
