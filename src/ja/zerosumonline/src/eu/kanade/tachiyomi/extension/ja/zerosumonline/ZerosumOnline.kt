package eu.kanade.tachiyomi.extension.ja.zerosumonline

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAsProto
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

@Source
abstract class ZerosumOnline : KeiSource() {
    private val domain get() = baseUrl.toHttpUrl().host
    private val apiUrl get() = "https://api.$domain/api/v1"

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/list".toHttpUrl().newBuilder()
            .addQueryParameter("category", "series")
            .addQueryParameter("sort", "date")
            .build()

        return client.get(url).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SelectFilter>().value
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("keyword", query)
            } else {
                addPathSegment("list")
                addQueryParameter("category", "series")
                addQueryParameter("sort", sort)
            }
        }.build()

        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val result = this.parseAsProto<TitleListView>()
        val mangas = result.titles.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$apiUrl/title".toHttpUrl().newBuilder()
            .addQueryParameter("tag", manga.url.substringAfterLast("/"))
            .build()

        val details = client.get(url).parseAsProto<TitleDetailView>()
        return SMangaUpdate(
            details.title.toSManga(),
            details.chapters.map { it.toSChapter(details.title.slug) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringAfterLast("/")
        val url = "$apiUrl/viewer".toHttpUrl().newBuilder()
            .addQueryParameter("chapter_id", id)
            .build()

        val result = client.post(url, ByteArray(0).toRequestBody()).parseAsProto<ViewerView>()
        return result.pages
            .filter { it.url.isNotEmpty() }
            .mapIndexed { i, img ->
                Page(i, imageUrl = img.url)
            }
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBeforeLast("/")

    // Filter
    override fun getFilterList(data: JsonElement?) = FilterList(
        SelectFilter(
            "Sort by",
            arrayOf(
                Pair("更新日が新しい順", "date"),
                Pair("作品名順", "title"),
                Pair("著者名順", "author"),
            ),
        ),
    )

    private open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val value: String
            get() = vals[state].second
    }
}
