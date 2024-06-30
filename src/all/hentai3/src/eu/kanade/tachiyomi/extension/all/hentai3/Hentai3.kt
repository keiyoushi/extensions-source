package eu.kanade.tachiyomi.extension.all.hentai3

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Hentai3(
    override val lang: String = "all",
    private val searchLang: String = "",
) : HttpSource() {

    override val name = "3Hentai"

    override val baseUrl = "https://3hentai.net"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("referer", "$baseUrl/")
        .set("origin", baseUrl)

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/${if (searchLang.isNotEmpty()) "language/$searchLang/${if (page > 1) page else ""}?" else "search?q=pages%3A>0&pages=$page&"}sort=popular", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()

        val mangas = doc.select("a[href*=/d/]").map(::popularMangaFromElement)
        val hasNextPage = doc.selectFirst("a[rel=next]") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.selectFirst("div")!!.ownText()
            setUrlWithoutDomain(element.absUrl("href"))
            thumbnail_url = element.selectFirst("img:not([class])")!!.absUrl("src")
        }
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/${if (searchLang.isNotEmpty()) "language/$searchLang/$page" else "search?q=pages%3A>0&pages=$page"}", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tags = mutableListOf<String>()
        var singleTag: Pair<String, String>? = null
        var sort = ""

        if (searchLang.isNotEmpty()) tags.add("language:$searchLang")
        filters.forEach {
            when (it) {
                is SelectFilter -> sort = it.getValue()

                is TextFilter -> {
                    if (it.state.isNotEmpty()) {
                        val splitted = it.state.split(",").filter(String::isNotBlank)
                        if (splitted.size < 2 && it.type != "tags") {
                            singleTag = it.type to it.state.replace(" ", "-")
                        } else {
                            splitted.map { tag ->
                                val trimmed = tag.trim().lowercase()
                                tags.add(
                                    buildString {
                                        if (trimmed.startsWith('-')) append("-")
                                        append(it.type, ":'")
                                        append(trimmed.removePrefix("-"), if (it.specific.isNotEmpty()) " (${it.specific})'" else "'")
                                    },
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (singleTag != null) {
                addPathSegment(singleTag!!.first)
                addPathSegment(singleTag!!.second)
                if (page > 1) addPathSegment(page.toString())
            } else {
                addPathSegment("search")
                addQueryParameter(
                    "q",
                    when {
                        tags.isNotEmpty() -> tags.joinToString()
                        query.isNotEmpty() -> query
                        else -> "page:>0"
                    },
                )
                if (page > 1) addQueryParameter("page", page.toString())
            }
            addQueryParameter("sort", sort)
        }.build()

        return GET(url, headers)
    }
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        fun String.capitalizeEach() = this.split(" ").joinToString(" ") { s ->
            s.replaceFirstChar { sr ->
                if (sr.isLowerCase()) sr.titlecase(Locale.getDefault()) else sr.toString()
            }
        }
        return SManga.create().apply {
            val authors = document.select("a[href*=/groups/]").eachText().joinToString()
            val artists = document.select("a[href*=/artists/]").eachText().joinToString()
            initialized = true
            title = document.select("h1 > span").text()
            author = authors.ifEmpty { artists }
            artist = artists.ifEmpty { authors }
            genre = document.select("a[href*=/tags/]").eachText().joinToString {
                val capitalized = it.capitalizeEach()
                if (capitalized.contains("male")) {
                    capitalized.replace("(female)", "♀").replace("(male)", "♂")
                } else {
                    "$capitalized ◊"
                }
            }

            description = buildString {
                document.select("a[href*=/characters/]").eachText().joinToString().ifEmpty { null }?.let {
                    append("Characters: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/series/]").eachText().joinToString().ifEmpty { null }?.let {
                    append("Series: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/groups/]").eachText().joinToString().ifEmpty { null }?.let {
                    append("Groups: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/language/]").eachText().joinToString().ifEmpty { null }?.let {
                    append("Languages: ", it.capitalizeEach(), "\n\n")
                }

                append(document.select("div.tag-container:contains(pages:)").text(), "\n")
            }
            thumbnail_url = document.selectFirst("img[src*=thumbnail].w-96")?.absUrl("src")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                setUrlWithoutDomain(response.request.url.toString())
                date_upload = try {
                    dateFormat.parse(doc.select("time").text())!!.time
                } catch (_: ParseException) {
                    0L
                }
            },
        )
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val images = response.asJsoup().select("img:not([class], [src*=thumb], [src*=cover])")
        return images.mapIndexed { index, image ->
            val imageUrl = image.absUrl("src")
            Page(index, imageUrl = imageUrl.replace(Regex("t(?=\\.)"), ""))
        }
    }

    override fun getFilterList() = getFilters()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
