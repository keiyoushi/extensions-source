package eu.kanade.tachiyomi.extension.tr.mikrokosmosfansub

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class MikrokosmosFansub : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = fetchSeries()
        .sortedByDescending { it.rating }
        .toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchSeries()
        .sortedByDescending { it.updatedAt }
        .toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val type = filters.firstInstance<TypeFilter>().value
        val status = filters.firstInstance<StatusFilter>().value
        return fetchSeries()
            .filter {
                it.title.contains(query, ignoreCase = true) &&
                    (type == null || it.type == type) &&
                    (status == null || it.status == status)
            }
            .sortedByDescending { it.updatedAt }
            .toMangasPage()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) {
                fetchSeries().first { it.slug == manga.url }.toSManga()
            } else {
                manga
            }
        }
        val chapterList = async {
            if (fetchChapters) {
                fetchChapters(manga.url)
                    .sortedWith(compareByDescending<ChapterDto> { it.number }.thenByDescending { it.publishedDate })
                    .map { it.toSChapter(manga.url) }
            } else {
                chapters
            }
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, id) = chapter.url.split('/', limit = 2)
        return fetchChapters(slug).first { it.id == id }.toPages()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/#/seri/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/#/oku/${chapter.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.fragment?.split('/')?.filter(String::isNotEmpty) ?: return null
        val slug = segments.getOrNull(1)?.takeIf { segments[0] == "seri" || segments[0] == "oku" } ?: return null
        return fetchSeries().firstOrNull { it.slug == slug }?.toSManga()
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(TypeFilter(), StatusFilter())

    private suspend fun fetchSeries(): List<SeriesDto> = client.get("$baseUrl/api/series")
        .parseAs<SeriesListDto>()
        .data
        .filter { !it.isDraft && it.type != "Web Novel" }

    private suspend fun fetchChapters(slug: String): List<ChapterDto> = client.get("$baseUrl/api/series?seriesId=$slug")
        .parseAs<ChapterListDto>()
        .chapters

    private fun List<SeriesDto>.toMangasPage() = MangasPage(map { it.toSManga() }, false)
}
