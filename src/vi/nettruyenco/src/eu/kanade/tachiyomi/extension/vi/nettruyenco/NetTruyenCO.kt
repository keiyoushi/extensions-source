package eu.kanade.tachiyomi.extension.vi.nettruyenco

import eu.kanade.tachiyomi.multisrc.wpcomics.ChapterDto
import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import okhttp3.HttpUrl
import org.jsoup.nodes.Document

@Source
abstract class NetTruyenCO : WPComics() {

    override val popularPath = "truyen-tranh-hot"

    override val useChapterListApi = true

    override fun chapterListApiUrl(manga: SManga): HttpUrl = super.chapterListApiUrl(manga).newBuilder()
        .setQueryParameter("slug", manga.slug())
        .addQueryParameter("comicId", manga.url.substringAfterLast("-"))
        .build()

    override fun chapterListApiChapterUrl(manga: SManga, chapter: ChapterDto): String = "/truyen-tranh/${manga.slug()}/${chapter.chapterSlug}/${chapter.chapterId}"

    // Manga urls end with "slug-12345", the api wants them split
    private fun SManga.slug() = url.substringAfterLast("/").substringBeforeLast("-")

    // Details
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        description = document.select("div.detail-content div.shortened").flatMap { it.children() }
            .joinToString("\n\n") { it.wholeText().trim() }
    }
}
