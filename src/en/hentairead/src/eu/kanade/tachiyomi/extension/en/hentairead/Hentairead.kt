package eu.kanade.tachiyomi.extension.en.hentairead

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Hentairead : Madara("HentaiRead", "https://hentairead.com", "en", dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)) {

    override val versionId: Int = 2

    private val cdnHeaders = super.headersBuilder()
        .add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
        .build()

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            if (url.contains("/wp-content/uploads/")) {
                return@addInterceptor chain.proceed(request.newBuilder().headers(cdnHeaders).build())
            }
            chain.proceed(request)
        }
        .build()

    override val mangaSubString = "hentai"
    override fun popularMangaNextPageSelector(): String? = "a[rel=next]"
    override fun mangaDetailsParse(document: Document): SManga {
        fun String.capitalizeEach() = this.split(" ").joinToString(" ") { s ->
            s.replaceFirstChar { sr ->
                if (sr.isLowerCase()) sr.titlecase(Locale.getDefault()) else sr.toString()
            }
        }
        return SManga.create().apply {
            val authors = document.select("a[href*=/circle/] span:first-of-type").eachText().joinToString()
            val artists = document.select("a[href*=/artist/] span:first-of-type").eachText().joinToString()
            initialized = true
            author = authors.ifEmpty { artists }
            artist = artists.ifEmpty { authors }
            genre = document.select("a[href*=/tag/] span:first-of-type").eachText().joinToString()

            description = buildString {
                document.select("a[href*=/characters/] span:first-of-type").eachText().joinToString().ifEmpty { null }?.let {
                    append("Characters: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/parody/] span:first-of-type").eachText().joinToString().ifEmpty { null }?.let {
                    append("Parodies: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/circle/] span:first-of-type").eachText().joinToString().ifEmpty { null }?.let {
                    append("Circles: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/convention/] span:first-of-type").eachText().joinToString().ifEmpty { null }?.let {
                    append("Convention: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/scanlator/] span:first-of-type").eachText().joinToString().ifEmpty { null }?.let {
                    append("Scanlators: ", it.capitalizeEach(), "\n\n")
                }
                document.selectFirst(".manga-titles h2")?.text()?.ifEmpty { null }?.let {
                    val titles = it.split("|").joinToString("\n") { "- ${it.trim()}" }
                    append("Alternative Titles: ", "\n", titles, "\n\n")
                }
                append(document.select(".items-center:contains(pages:)").text(), "\n")
            }
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }
    override fun getFilterList(): FilterList = getFilters()

    override fun searchLoadMoreRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl${searchPage(page)}".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("post_type", "wp-manga")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/$mangaSubString/${searchPage(page)}?sortby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/$mangaSubString/${searchPage(page)}?sortby=new", headers)
    override fun popularMangaSelector() = ".manga-item"
    override val popularMangaUrlSelector = "a.manga-item__link"

    private fun getTagId(tag: String, type: String): Int? {
        val ajax = "$baseUrl/wp-admin/admin-ajax.php?action=search_manga_terms&search=$tag&taxonomy=$type".replace("artist", "manga_artist")
        val res = client.newCall(GET(ajax, headers)).execute()
        val items = res.parseAs<Results>()
        val item = items.results.filter { it.text.lowercase() == tag.lowercase() }
        if (item.isNotEmpty()) {
            return item[0].id
        }
        return null
    }
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("page/$page")
            addQueryParameter("s", query)
            addQueryParameter("title-type", "contains")
            filters.forEach {
                when (it) {
                    is TypeFilter -> {
                        val (activeFilter, inactiveFilters) = it.state.partition { stIt -> stIt.state }
                        activeFilter.map { fil -> addQueryParameter("categories[]", fil.value) }
                    }

                    is PageFilter -> {
                        if (it.state.isNotBlank()) {
                            val (min, max) = parsePageRange(it.state)
                            addQueryParameter("pages", "$min-$max")
                        }
                    }

                    is UploadedFilter -> {
                        if (it.state.isNotBlank()) {
                            val type = when (it.state.firstOrNull()) {
                                '>' -> "after"
                                '<' -> "before"
                                else -> "in"
                            }
                            addQueryParameter("release-type", type)
                            addQueryParameter("release", it.state.filter(Char::isDigit))
                        }
                    }

                    is TextFilter -> {
                        if (it.state.isNotEmpty()) {
                            it.state.split(",").filter(String::isNotBlank).map { tag ->
                                val trimmed = tag.trim()
                                val id = getTagId(trimmed.removePrefix("-"), it.type)?.toString()
                                    ?: throw Exception("${it.type.lowercase().replaceFirstChar(Char::uppercase)} not found: ${trimmed.removePrefix("-")}")
                                if (it.type == "manga_tag") {
                                    if (trimmed.startsWith('-')) {
                                        addQueryParameter("excluding[]", id)
                                    } else {
                                        addQueryParameter("including[]", id)
                                    }
                                } else {
                                    addQueryParameter("${it.type}s[]", id)
                                }
                            }
                        }
                    }

                    is SortFilter -> {
                        addQueryParameter("sortby", it.getValue())
                        addQueryParameter("order", if (it.state!!.ascending) "asc" else "desc")
                    }
                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    private fun parsePageRange(query: String, minPages: Int = 1, maxPages: Int = 9999): Pair<Int, Int> {
        val num = query.filter(Char::isDigit).toIntOrNull() ?: -1
        fun limitedNum(number: Int = num): Int = number.coerceIn(minPages, maxPages)

        if (num < 0) return minPages to maxPages
        return when (query.firstOrNull()) {
            '<' -> 1 to if (query[1] == '=') limitedNum() else limitedNum(num + 1)
            '>' -> limitedNum(if (query[1] == '=') num else num + 1) to maxPages
            '=' -> when (query[1]) {
                '>' -> limitedNum() to maxPages
                '<' -> 1 to limitedNum(maxPages)
                else -> limitedNum() to limitedNum()
            }
            else -> limitedNum() to limitedNum()
        }
    }

    // chapterExtraData = ({...});
    private val chapterExtraDataRegex = Regex("""= (\{[^;]+)""")

    // window.mMjM5MjM2 = '(eyJkYX...);
    private val pagesDataRegex = Regex(""".(ey\S+).\s""")

    // From ManhwaHentai - modified
    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }

        val pageBaseUrl = document.selectFirst("[id=single-chapter-js-extra]")?.data()
            ?.let { chapterExtraDataRegex.find(it)?.groups }?.get(1)?.value
            ?.let { json.decodeFromString<ImageBaseUrlDto>(it).baseUrl }

        val pages = document.selectFirst("[id=single-chapter-js-before]")?.data()
            ?.let { pagesDataRegex.find(it)?.groups }?.get(1)?.value
            ?.let { json.decodeFromString<PagesDto>(String(Base64.decode(it, Base64.DEFAULT))) }
            ?: throw Exception("Failed to find page list. Non-English entries are not supported.")

        return pages.data.chapter.images.mapIndexed { idx, page ->
            Page(idx, document.location(), "$pageBaseUrl/${page.src}")
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.url
                    if (manga.description?.contains("Scanlators") == true) {
                        scanlator = manga.description?.substringAfter("Scanlators: ")?.substringBefore("\n")
                    }
                },
            ),
        )
    }

    override fun pageListRequest(chapter: SChapter): Request {
        // There's like 2 non-English entries where this breaks
        val url = "${chapter.url}english/p/1/"

        if (url.startsWith("http")) {
            return GET(url, headers)
        }
        return GET(baseUrl + url, headers)
    }

    override fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ").removeSuffix(",")
            element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
            else -> element.attr("abs:src")
        }
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }
}
