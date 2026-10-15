package eu.kanade.tachiyomi.extension.ja.ganganonline

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class GanganOnline : KeiSource() {
    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/rensai").parseMangaList()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search/result".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return client.get(url).parseMangaList()
        }

        val filter = filters.firstInstance<CategoryFilter>()
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(filter.toUriPart().removePrefix("/"))
            .build()
        return client.get(url).parseMangaList()
    }

    private fun Response.parseMangaList(): MangasPage {
        val url = request.url.toString()
        val mangas = when {
            "/search/result" in url -> {
                val data = parseAsNextData<MangaListDto>()
                data.sections?.flatMap { it.titleLinks }
                    ?.filter { it.isNovel != true }
                    ?.map { it.toSManga(baseUrl) }
            }

            "/rensai" in url || "/finish" in url -> {
                val data = parseAsNextData<MangaListDto>()
                data.titleSections?.flatMap { it.titles }
                    ?.filter { it.isNovel != true }
                    ?.map { it.toSManga(baseUrl) }
            }

            "/ga" in url -> {
                val data = parseAsNextData<MangaListDto>()
                val ongoing = data.ongoingTitleSection?.titles!!
                val finished = data.finishedTitleSection?.titles!!
                (ongoing + finished)
                    .filter { it.isNovel != true }
                    .map { it.toSManga(baseUrl) }
            }

            "/pixiv" in url -> {
                val data = parseAsNextData<PixivPageDto>()
                data.ganganTitles?.map { it.toSManga(baseUrl) }
            }

            else -> null
        }
        return MangasPage(mangas!!, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val mangaUrl = response.request.url.toString()
            .substringBefore("/chapter")
            .substringAfter(baseUrl)
        val data = response.parseAsNextData<MangaDetailDto>().default

        return SMangaUpdate(
            data.toSManga(baseUrl),
            data.chapters
                .filter { it.status == null || it.status >= 4 }
                .map { it.toSChapter(mangaUrl) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val data = client.get(getChapterUrl(chapter)).parseAsNextData<PageListDto>()
        return data.pages.mapIndexed { i, page ->
            val imageUrl = (page.image ?: page.linkImage)!!.imageUrl
            Page(i, imageUrl = baseUrl + imageUrl)
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        CategoryFilter(getCategoryList()),
    )

    private class CategoryFilter(private val category: Array<Pair<String, String>>) : Filter.Select<String>("Category", category.map { it.first }.toTypedArray()) {
        fun toUriPart() = category[state].second
    }

    private fun getCategoryList() = arrayOf(
        Pair("連載作品", "/rensai"),
        Pair("連載終了作品", "/finish"),
        Pair("ガンガンpixiv", "/pixiv"),
        Pair("ガンガンGA", "/ga"),
    )

    private inline fun <reified T> Response.parseAsNextData(): T {
        val script = this.asJsoup().selectFirst("script#__NEXT_DATA__")!!.data()
        return script.parseAs<NextData<T>>().props.pageProps.data
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
}
