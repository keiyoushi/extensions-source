package eu.kanade.tachiyomi.extension.en.readvagabondmanga

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

@Source
abstract class ReadVagabondManga : KeiSource() {
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/api/mihon/mangas").parseAs<List<MangaDto>>()
        return MangasPage(mangas.map { manga -> manga.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/mihon/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        val mangas = client.get(url).parseAs<List<MangaDto>>()
        return MangasPage(mangas.map { manga -> manga.toSManga() }, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaId = "$baseUrl${manga.url}".toHttpUrl().fragment

        val details = async {
            if (fetchDetails) {
                client.get("$baseUrl/api/mihon/mangas/$mangaId").parseAs<MangaDto>().toSManga()
            } else {
                manga
            }
        }
        val chapterList = async {
            if (fetchChapters) {
                client.get("$baseUrl/api/mihon/mangas/$mangaId/chapters")
                    .parseAs<List<ChapterDto>>()
                    .map { chapter -> chapter.toSChapter() }
            } else {
                chapters
            }
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaId = "$baseUrl${chapter.url}".toHttpUrl().fragment
        val chapterDto = client.get("$baseUrl/api/mihon/mangas/$mangaId/chapters/${chapter.chapter_number.toInt()}")
            .parseAs<ChapterDto>()
        return (1..chapterDto.pageCount).map { page ->
            Page(
                index = page - 1,
                imageUrl = "https://pub.moleve.net/chapter-${chapterDto.number}/page-$page.png",
            )
        }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}"
}
