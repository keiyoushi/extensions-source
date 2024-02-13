package eu.kanade.tachiyomi.multisrc.makaru

import android.annotation.SuppressLint
import android.os.Build
import eu.kanade.tachiyomi.multisrc.makaru.MakaruUtils.imgAttr
import eu.kanade.tachiyomi.multisrc.makaru.MakaruUtils.textWithNewlines
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

// multisrc SDK is 29 but extension SDK is 21.
@SuppressLint("ObsoleteSdkInt")
abstract class Makaru(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = false

    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrlBuilder("Series", page, MAX_MANGA_RESULTS).build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<MakaruDto>()
        val manga = data.feed.entry.map { entry ->
            val content = Jsoup.parseBodyFragment(entry.content.t, baseUrl)

            SManga.create().apply {
                setUrlWithoutDomain(entry.link.first { it.rel == "alternate" }.href)
                title = entry.title.t
                thumbnail_url = content.selectFirst("img")?.imgAttr()
            }
        }
        val hasNextPage = (data.feed.startIndex.t.toInt() + data.feed.itemsPerPage.t.toInt()) <= data.feed.totalResults.t.toInt()

        return MangasPage(manga, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }
        val searchQuery = buildString(13) {
            append("label:Series")

            filterList.filterIsInstance<LabelFilter>().forEach {
                it.state
                    .filter { f -> f.state }
                    .forEach { f ->
                        append(" label:\"")
                        append(f.name)
                        append("\"")
                    }
            }

            if (query.isNotEmpty()) {
                append(" ")
                append(query)
            }
        }
        val url = apiUrlBuilder("Series", page, MAX_MANGA_RESULTS).apply {
            addQueryParameter("q", searchQuery)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        val (_, data) = getMangaFeed(document)
        val mangaContent = data.feed.entry.first { it.category.all { c -> c.term != "Chapter" } }
        val mangaInfo = Jsoup.parseBodyFragment(mangaContent.content.t, baseUrl)

        title = document.selectFirst("h1[itemprop=headline]")!!.text()
        genre = document.select("div.genres a").joinToString { it.text() }
        thumbnail_url = document.selectFirst("div.thumbnail img")?.imgAttr()
        author = mangaInfo.select("span.komikus").joinToString { it.text() }
        status = when (document.selectFirst("div.info-single-list ul li:contains(Status)")?.ownText()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        val descriptionBuilder = StringBuilder()
        val descriptionElement = mangaInfo.selectFirst("div.sinopsis")

        for (entry in mangaInfo.select("li.info_x")) {
            when (entry.selectFirst("strong")!!.text().removeSuffix(":")) {
                "Artist" -> artist = entry.ownText()
                else -> descriptionBuilder.appendLine(entry.text())
            }
        }

        if (descriptionElement != null) {
            descriptionBuilder.appendLine()
            descriptionBuilder.append(descriptionElement.textWithNewlines())
        }

        description = descriptionBuilder.toString().trim()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val (feed, data) = getMangaFeed(document)

        return data.feed.entry
            .filter { it.category.any { c -> c.term == "Chapter" } }
            .map { entry ->
                SChapter.create().apply {
                    setUrlWithoutDomain(entry.link.first { it.rel == "alternate" }.href)
                    name = entry.title.t.replace(feed, "").trim()
                    date_upload = try {
                        dateFormat.parse(entry.published.t)!!.time
                    } catch (e: ParseException) {
                        0L
                    }
                }
            }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val script = document.selectFirst("script:containsData(data_content)")
            ?: throw Exception("Chapter image script not found")
        val dataContent = script.data()
            .substringAfter("let data_content = `")
            .substringBefore("`;")
            .replace(hexEscapeRegex) {
                it.groupValues[1].toInt(16).toChar().toString()
            }

        return Jsoup.parseBodyFragment(dataContent, baseUrl).select("img").mapIndexed { i, it ->
            Page(i, imageUrl = it.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        LabelFilter("Status", getStatusList()),
        LabelFilter("Type", getTypeList()),
        LabelFilter("Genre", getGenreList()),
    )

    private val hexEscapeRegex = Regex("""\\x([0-9A-Za-z]{2})""")

    private val dateFormat by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.getDefault())
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
    }

    private fun getMangaFeed(document: Document): Pair<String, MakaruDto> {
        val script = document.selectFirst("script:containsData(clwd.run)")
            ?: throw Exception("Cannot find manga feed name")
        val feed = script.data().substringAfter("clwd.run('").substringBefore("');")
        return feed to client.newCall(
            GET(
                apiUrlBuilder(feed, 1, MAX_CHAPTER_RESULTS).build(),
                headers,
            ),
        )
            .execute()
            .parseAs<MakaruDto>()
    }

    private fun apiUrlBuilder(feed: String, page: Int, maxResults: Int) = baseUrl.toHttpUrl().newBuilder().apply {
        // Blogger indices start from 1
        val startIndex = maxResults * (page - 1) + 1

        addPathSegments("feeds/posts/default/-/")
        addPathSegment(feed)
        addQueryParameter("alt", "json")
        addQueryParameter("max-results", maxResults.toString())
        addQueryParameter("start-index", startIndex.toString())
    }

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())

    companion object {
        private const val MAX_MANGA_RESULTS = 20
        private const val MAX_CHAPTER_RESULTS = 999999
    }
}
