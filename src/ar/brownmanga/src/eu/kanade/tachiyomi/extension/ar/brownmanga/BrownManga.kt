package eu.kanade.tachiyomi.extension.ar.brownmanga

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
import keiyoushi.utils.string
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class BrownManga : KeiSource() {
    private val apiUrl: String
        get() = baseUrl.replace("https://", "https://cdn.")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/rest/v1/manhwa".toHttpUrl().newBuilder().apply {
            addQueryParameter("select", "*")
            addQueryParameter("order", "views .desc")
        }.build()

        val entries = client.get(url).parseAs<List<ManhwaDto>>().map { it.toSManga() }

        return MangasPage(entries, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/rest/v1/manhwa".toHttpUrl().newBuilder().apply {
            addQueryParameter("select", "*")
            addQueryParameter("order", "updated_at.desc")
        }.build()

        val entries = client.get(url).parseAs<List<ManhwaDto>>().map { it.toSManga() }

        return MangasPage(entries, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/rest/v1/manhwa".toHttpUrl().newBuilder().apply {
            addQueryParameter("select", "*")
            addQueryParameter("or", "(title.ilike.*$query*,title_ar.ilike.*$query*)")
        }.build()

        val entries = client.get(url).parseAs<List<ManhwaDto>>().map { it.toSManga() }

        return MangasPage(entries, false)
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.memo["slug"]!!.string
        return "$baseUrl/series/$slug"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$apiUrl/rest/v1/manhwa".toHttpUrl().newBuilder().apply {
            addQueryParameter("select", "*,genres(name),chapters(id,title,published_at,chapter_number,translator_name)")
            addQueryParameter("id", "eq.${manga.url}")
            addQueryParameter("chapters.is_locked", "eq.false")
            addQueryParameter("chapters.order", "chapter_number.desc")
        }.build()

        val data = client.get(url).parseAs<List<ManhwaDto>>().first()

        return SMangaUpdate(data.toSManga(), data.chapters.map { it.toSChapter(data.slug) })
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo["slug"]!!.string
        val number = chapter.memo["number"]!!.string
        return "$baseUrl/series/$slug/chapter/$number"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/rest/v1/chapter_pages".toHttpUrl().newBuilder().apply {
            addQueryParameter("select", "image_url")
            addQueryParameter("chapter_id", "eq.${chapter.url}")
            addQueryParameter("order", "page_number.asc")
        }.build()

        return client.get(url).parseAs<List<ChapterPageDto>>().mapIndexed { index, page ->
            Page(index, imageUrl = page.imageUrl)
        }
    }
}
