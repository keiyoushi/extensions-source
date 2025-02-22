package eu.kanade.tachiyomi.extension.en.taadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.parser.Parser
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Taadd : HttpSource() {

    override val name = "Taadd"

    override val baseUrl = "https://www.taadd.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()

            if (!url.startsWith(baseUrl) || request.url.fragment.isNullOrBlank()) {
                return@addNetworkInterceptor chain.proceed(request)
            }

            val version = request.url.fragment!!

            val cookieList = request.header("Cookie")
                ?.split("; ") ?: emptyList()

            val newCookie = buildList(cookieList.size + 1) {
                cookieList.filterNotTo(this) { existing ->
                    existing.startsWith("dm72_desktop=")
                }
                add("dm72_desktop=$version")
            }.joinToString("; ")

            val newRequest = request.newBuilder()
                .header("Cookie", newCookie)
                .build()

            chain.proceed(newRequest)
        }.build()

    private val json by injectLazy<Json>()

    override fun headersBuilder() = super.headersBuilder()
        .set("referer", "$baseUrl/")

    private val ajaxHeaders = headersBuilder()
        .set("X-Requested-With", "XMLHttpRequest")
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val body = FormBody.Builder()
            .add("page", page.toString())
            .build()

        return POST("$baseUrl/ajax/hot#mobile", ajaxHeaders, body)
    }

    @Serializable
    class BrowseManga(
        @SerialName("manga_url") val url: String,
        val name: String,
        val cover: String,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<List<BrowseManga>>()

        return MangasPage(
            mangas = data.map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.url)
                    title = Parser.unescapeEntities(it.name, false)
                    thumbnail_url = it.cover
                }
            },
            hasNextPage = data.size == 20,
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val body = FormBody.Builder()
            .add("page", page.toString())
            .build()

        return POST("$baseUrl/ajax/more#mobile", ajaxHeaders, body)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
            addQueryParameter("name_sel", filters.get<NameMatchFilter>().selected)
            addQueryParameter("wd", query.trim())
            filters.get<AuthorFilter>().let {
                addQueryParameter("author_sel", it.select)
                addQueryParameter("author", it.text)
            }
            filters.get<ArtistFilter>().let {
                addQueryParameter("artist_sel", it.select)
                addQueryParameter("artist", it.text)
            }
            filters.get<GenreFilter>().let {
                addQueryParameter("category_id", it.included.joinToString(","))
                addQueryParameter("out_category_id", it.excluded.joinToString(","))
            }
            addQueryParameter("completed_series", filters.get<CompletedSeriesFilter>().selected)
            addQueryParameter("released", filters.get<ReleaseYearFilter>().selected)
            addQueryParameter("page", page.toString())
            fragment("desktop")
        }.build()

        return GET(url, headers)
    }

    override fun getFilterList() = getFilters()

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        return MangasPage(
            mangas = document.select(".clistChr li:not(.dot-line1):not(.dot-line0)").map {
                SManga.create().apply {
                    with(it.selectFirst("h2 > a")!!) {
                        setUrlWithoutDomain(absUrl("href"))
                        title = text()
                    }
                    thumbnail_url = it.selectFirst(".cover img")?.absUrl("src")
                }
            },
            hasNextPage = document.selectFirst(".pagetor a:contains(>>)") != null,
        )
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}#mobile", headers)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url}"
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()

        thumbnail_url = document.selectFirst("img.detail-cover")?.absUrl("src")
        description = buildString {
            document.selectFirst(".manga-summary")?.text()?.let {
                if (it.trim() != "N/A") {
                    append(it)
                }
            }

            document.selectFirst(".detail-info > p:contains(Alternative)")?.text()?.let {
                if (isNotBlank()) {
                    append("\n\n")
                }
                append(it)
            }
        }
        genre = document.select(".manga-genres a").eachText().joinToString()
        status = when (document.selectFirst(".detail-info > p:contains(status) > a")?.text()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        author = document.select(".detail-info > p:contains(author) > a").text()
        artist = author
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}?waring=1#desktop", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val mangaTitle = document.selectFirst("meta[property=og:title]")!!
            .attr("content")
            .simplify()

        return document.select(".chapter_list tr").drop(1).map {
            SChapter.create().apply {
                with(it.selectFirst("td > a")!!) {
                    setUrlWithoutDomain(absUrl("href"))
                    name = run {
                        val rawTitle = text()
                        val simplified = rawTitle.simplify()

                        if (simplified.startsWith(mangaTitle)) {
                            var idx = mangaTitle.length
                            while (idx < rawTitle.length) {
                                // vol.x ch.y || season x chap y
                                if (
                                    rawTitle[idx].equals('v', true) ||
                                    rawTitle[idx].equals('s', true)
                                ) {
                                    val _idx = rawTitle.indexOf('c', idx, true)
                                    if (_idx != -1) {
                                        idx = _idx
                                    } else {
                                        idx++
                                    }
                                    // actual chapter number
                                } else if (!rawTitle[idx].isDigit()) {
                                    idx++
                                } else {
                                    // remove leading zeros -> 005
                                    while (
                                        rawTitle[idx] == '0' &&
                                        rawTitle.getOrNull(idx + 1)?.isDigit() == true
                                    ) {
                                        idx++
                                    }
                                    break
                                }
                            }

                            if (idx != rawTitle.length) {
                                val cleanedTitle = rawTitle.substring(idx, rawTitle.length)

                                "Chapter $cleanedTitle"
                            } else {
                                rawTitle.substring(mangaTitle.length, rawTitle.length).trim()
                            }
                        } else {
                            rawTitle
                        }
                    }
                }
                date_upload = try {
                    dateFormat.parse(
                        it.select("td > a").last()!!.text(),
                    )!!.time
                } catch (_: Exception) {
                    0L
                }
            }
        }
    }

    private fun String.simplify(): String {
        return lowercase()
            .replace(specialChar) {
                " ".repeat(it.value.length)
            }
    }

    private val specialChar = Regex("""[^a-z0-9]+""")
    private val dateFormat = SimpleDateFormat("EEEE, MMMM dd, yyyy", Locale.ENGLISH)

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$baseUrl${chapter.url}#desktop", headers)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    override fun pageListParse(response: Response): List<Page> {
        var document = response.asJsoup()
        val serverUrl = document.selectFirst("section.section div.post-content-body > a")
            ?.attr("href")

        if (serverUrl != null) {
            val headers = headersBuilder()
                .set("referer", document.baseUri())
                .build()
            document = client.newCall(GET(serverUrl, headers)).execute().asJsoup()
        }

        val finalUrl = document.selectFirst("script:containsData(window.location.href)")?.data()
            ?.substringAfter("\"")
            ?.substringBefore("\"")?.let {
                "https://" + document.baseUri().toHttpUrl().host + it
            }

        if (finalUrl != null) {
            val headers = headersBuilder()
                .set("referer", document.baseUri())
                .build()
            document = client.newCall(GET(finalUrl, headers)).execute().asJsoup()
        }

        val script = document.select("script:containsData(all_imgs_url)").html()

        val images = imgRegex.find(script)?.groupValues?.get(1)
            ?.let { json.decodeFromString<List<String>>("[$it]") }
            ?: throw Exception("Unable to find images")

        return images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    private val imgRegex = Regex("""all_imgs_url\s*:\s*\[\s*([^]]*)\s*,\s*]""")

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromStream(body.byteStream())

    private inline fun <reified T : Filter<*>> FilterList.get(): T {
        return filterIsInstance<T>().first()
    }
}
