package eu.kanade.tachiyomi.extension.tr.merlinscans

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class MerlinScans : InitManga() {

    override val popularUrlSlug = "seri-siralamasi"

    override val latestUrlSlug = "son-guncellenenler"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) {
                val document = client.get(getMangaUrl(manga)).asJsoup()
                parseMangaDetails(document).apply {
                    url = manga.url
                }
            } else {
                manga
            }
        }
        val chapterList = async {
            if (fetchChapters) {
                fetchChapterList(manga)
            } else {
                chapters
            }
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val slug = manga.url.trimEnd('/').substringAfterLast('/')
        val mangaUrl = "$baseUrl/wp-json/wp/v2/manga".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .addQueryParameter("_fields", "id,link")
            .build()
        val mangaResult = client.get(mangaUrl).parseAs<List<MangaIdDto>>().firstOrNull() ?: return emptyList()
        val chapters = mutableListOf<SChapter>()
        var page = 1

        do {
            val url = "$baseUrl/wp-json/initmanga/v1/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("manga_id", mangaResult.id.toString())
                .addQueryParameter("per_page", "50")
                .addQueryParameter("paged", page.toString())
                .build()
            val result = client.get(url).parseAs<ChapterListDto>()

            result.items.mapTo(chapters) { chapter ->
                SChapter.create().apply {
                    setUrlWithoutDomain("${mangaResult.link}${chapter.slug}/")
                    name = "Bölüm ${chapter.number.toString().removeSuffix(".0")}"
                    if (chapter.title.isNotBlank()) {
                        name += " - ${chapter.title}"
                    }
                    date_upload = merlinDateFormat.tryParseDateTime(chapter.createdAt, ZoneId.of("Europe/Istanbul"))
                }
            }
            page++
        } while (page <= result.totalPages)

        return chapters
    }

    companion object {
        private val merlinDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
