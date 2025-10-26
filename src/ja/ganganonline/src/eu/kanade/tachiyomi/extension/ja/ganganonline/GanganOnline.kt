package eu.kanade.tachiyomi.extension.ja.ganganonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class GanganOnline : HttpSource() {
    override val name = "Gangan Online"
    override val baseUrl = "https://www.ganganonline.com"
    override val lang = "ja"
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("yyyy.MM.dd", Locale.JAPAN)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/rensai", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/search/result".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstance<CategoryFilter>()
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(filter.toUriPart().removePrefix("/"))
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val url = response.request.url.toString()
        val mangas = when {
            "/search/result" in url -> {
                val data = response.parseAsNextData<MangaListDto>()
                data.sections?.flatMap { it.titleLinks }
                    ?.filter { it.isNovel != true }
                    ?.map {
                        SManga.create().apply {
                            this.url = "/title/${it.titleId}"
                            title = it.name!!
                            thumbnail_url = baseUrl + it.imageUrl
                        }
                    }
            }
            "/rensai" in url || "/finish" in url -> {
                val data = response.parseAsNextData<MangaListDto>()
                data.titleSections?.flatMap { it.titles }
                    ?.filter { it.isNovel != true }
                    ?.map {
                        SManga.create().apply {
                            this.url = "/title/${it.titleId}"
                            title = it.header!!
                            thumbnail_url = baseUrl + it.imageUrl
                        }
                    }
            }
            "/ga" in url -> {
                val data = response.parseAsNextData<MangaListDto>()
                val ongoing = data.ongoingTitleSection?.titles!!
                val finished = data.finishedTitleSection?.titles!!
                (ongoing + finished)
                    .filter { it.isNovel != true }
                    .map {
                        SManga.create().apply {
                            this.url = "/title/${it.titleId}"
                            title = it.header!!
                            thumbnail_url = baseUrl + it.imageUrl
                        }
                    }
            }
            "/pixiv" in url -> {
                val data = response.parseAsNextData<PixivPageDto>()
                data.ganganTitles?.map {
                    SManga.create().apply {
                        this.url = "/title/${it.titleId}"
                        title = it.name
                        thumbnail_url = baseUrl + it.imageUrl
                    }
                }
            }
            else -> null
        }
        return MangasPage(mangas!!, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAsNextData<MangaDetailDto>().default
        return SManga.create().apply {
            title = data.titleName
            author = data.author
            description = data.description
            thumbnail_url = baseUrl + data.imageUrl
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaUrl = response.request.url.toString()
            .substringBefore("/chapter")
            .substringAfter(baseUrl)
        val data = response.parseAsNextData<MangaDetailDto>().default

        return data.chapters
            .filter { it.status == null || it.status >= 4 }
            .map { chapter ->
                SChapter.create().apply {
                    url = "$mangaUrl/chapter/${chapter.id}"
                    name = chapter.mainText + if (!chapter.subText.isNullOrEmpty()) " - ${chapter.subText}" else ""
                    date_upload = chapter.publishingPeriod?.substringBefore("〜").let { dateFormat.tryParse(it) }
                }
            }
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAsNextData<PageListDto>()
        return data.pages.mapIndexed { i, page ->
            val imageUrl = (page.image ?: page.linkImage)!!.imageUrl
            Page(i, imageUrl = baseUrl + imageUrl)
        }
    }

    override fun getFilterList() = FilterList(
        CategoryFilter(getCategoryList()),
    )

    private class CategoryFilter(private val category: Array<Pair<String, String>>) :
        Filter.Select<String>("Category", category.map { it.first }.toTypedArray()) {
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

    // Unsupported
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
