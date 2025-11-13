package eu.kanade.tachiyomi.extension.en.manhwaread

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
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaRead : Madara("ManhwaRead", "https://manhwaread.com", "en", dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)) {

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

    override val mangaSubString = "manhwa"
    override fun popularMangaNextPageSelector(): String? = "a[rel=next]"
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            initialized = true

            author = document.select("a[href*=/author/] span:first-of-type").eachText().joinToString().ifBlank {
                document.select(".author-artist span").eachText().joinToString()
            }
            artist = document.select("a[href*=/artist/] span:first-of-type").eachText().joinToString().ifBlank { author }

            run {
                val tokens = document.select("a[rel=tag]")
                    .mapNotNull { it.selectFirst("span")?.text() }
                genre = tokens.map(String::trim).filter(String::isNotBlank).distinctBy(String::lowercase).joinToString()
            }

            val desc = document.select("#mangaDesc .manga-desc__content").text().ifBlank {
                document.select("meta[name=description], meta[property=og:description]").attr("content")
            }
            description = buildString {
                if (desc.isNotBlank()) append(desc)
                document.selectFirst(".manga-titles h2")?.text()?.ifBlank { null }?.let {
                    val titles = it.split("|").joinToString("\n") { t -> "- ${t.trim()}" }
                    if (isNotEmpty()) append("\n\n")
                    append("Alternative Titles:\n", titles)
                }
            }

            val statusAttr = document.selectFirst(".manga-status")?.attr("data-status")?.lowercase()
            status = when (statusAttr) {
                "completed" -> SManga.COMPLETED
                "ongoing" -> SManga.ONGOING
                "canceled" -> SManga.CANCELLED
                "on-hold" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }
    override fun getFilterList(): FilterList = getFilters()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/top-manhwa/${searchPage(page)}?sortby=weekly_top", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manhwa/${searchPage(page)}?sortby=new", headers)
    override fun popularMangaSelector() = ".manga-item"
    override val popularMangaUrlSelector = "a.manga-item__link"

    private fun getTagId(tag: String, type: String): Int? {
        val taxonomy = when (type) {
            "artist" -> "manga_artist"
            "author" -> "manga_author"
            else -> type
        }
        val ajax = "$baseUrl/wp-admin/admin-ajax.php?action=search_manga_terms&search=$tag&taxonomy=$taxonomy"
        val res = client.newCall(GET(ajax, headers)).execute()
        val items = json.decodeFromString<Results>(res.body.string())
        val item = items.results.filter { it.text.lowercase() == tag.lowercase() }
        if (item.isNotEmpty()) {
            return item[0].id
        }
        return null
    }
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/${searchPage(page)}".toHttpUrl().newBuilder().apply {
            addQueryParameter("post_type", "wp-manga")

            // When user clicks Author/Artist from details page, it passes the name in query.
            // Try to resolve it to taxonomy IDs if explicit filters are not already set.
            val hasAuthorFilter = filters.any { it is TextFilter && it.type == "author" && it.state.isNotBlank() }
            val hasArtistFilter = filters.any { it is TextFilter && it.type == "artist" && it.state.isNotBlank() }
            val hasTagFilter = filters.any { it is TextFilter && it.type == "manga_tag" && it.state.isNotBlank() }
            var taxonomyMatched = false
            if (query.isNotBlank() && !hasAuthorFilter && !hasArtistFilter) {
                val authorId = getTagId(query.trim(), "author")
                if (authorId != null) {
                    addQueryParameter("authors[]", authorId.toString())
                    taxonomyMatched = true
                } else {
                    val artistId = getTagId(query.trim(), "artist")
                    if (artistId != null) {
                        addQueryParameter("artists[]", artistId.toString())
                        taxonomyMatched = true
                    } else if (!hasTagFilter) {
                        val tagId = getTagId(query.trim(), "manga_tag")
                        if (tagId != null) {
                            addQueryParameter("including[]", tagId.toString())
                            taxonomyMatched = true
                        }
                    }
                }
            }

            val sValue = if (query.isNotBlank() && !hasAuthorFilter && !hasArtistFilter && !taxonomyMatched) query else ""
            addQueryParameter("s", sValue)
            if (sValue.isNotEmpty()) addQueryParameter("title-type", "contains")

            filters.forEach {
                when (it) {
                    is GenresFilter -> {
                        val (activeFilter, _) = it.state.partition { stIt -> stIt.state }
                        activeFilter.forEach { fil -> addQueryParameter("genres[]", fil.value) }
                    }

                    is StatusSelectFilter -> {
                        val slug = it.selectedValue()
                        if (!it.isDefault() && slug.isNotEmpty()) addQueryParameter("status", slug)
                    }

                    is PageFilter -> {
                        if (it.state.isNotBlank()) {
                            val (min, max) = parsePageRange(it.state)
                            addQueryParameter("chapter_range", "$min-$max")
                        }
                    }

                    is UploadedFilter -> {
                        if (it.state.isNotBlank()) {
                            val (min, max) = parsePageRange(it.state, 1, 9999)
                            addQueryParameter("year_range", "$min-$max")
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
    private val chapterExtraDataRegex = Regex("""chapterData = (\{[^;]+)""")

    // From ManhwaHentai - modified
    override fun pageListParse(document: Document): List<Page> {
        launchIO { countViews(document) }
        val extraData = document.selectFirst("[id=single-chapter-js-extra]")?.data()
        val pageBaseUrl = extraData
            ?.let { chapterExtraDataRegex.find(it)?.groups }?.get(1)?.value
            ?.let { json.decodeFromString<ImageBaseUrlDto>(it).base }

        val decodedPages = extraData
            ?.let { chapterExtraDataRegex.find(it)?.groups }?.get(1)?.value
            ?.let { json.decodeFromString<Map<String, String>>(it)["data"] }
            ?.let { String(Base64.decode(it, Base64.DEFAULT)) }
            ?.let { json.decodeFromString<List<PagesDto.Data.Chapter.Image>>(it) }

        if (decodedPages != null && pageBaseUrl != null) {
            return decodedPages.mapIndexed { idx, page ->
                Page(idx, document.location(), "$pageBaseUrl/${page.src}")
            }
        }

        // Fallback: parse images directly from DOM when script payload is absent
        val imgs = document.select(".reading-content img, article img, .entry-content img, img")
            .filter { el -> imageFromElement(el)?.isNotBlank() == true }

        if (imgs.isEmpty()) {
            throw Exception("Failed to find page images for this chapter.")
        }

        return imgs.mapIndexed { idx, el ->
            val imgUrl = imageFromElement(el)!!
            Page(idx, document.location(), imgUrl)
        }
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val url = if (manga.url.startsWith("http")) manga.url else baseUrl + manga.url
        val res = client.newCall(GET(url, headers)).execute()
        val doc = Jsoup.parse(res.body.string(), baseUrl)

        val anchors = doc.select("#chaptersList a.chapter-item").ifEmpty { doc.select("a[href*=/chapter-]") }
        if (anchors.isEmpty()) {
            return Observable.just(
                listOf(
                    SChapter.create().apply {
                        name = "Chapter"
                        this.url = manga.url
                    },
                ),
            )
        }

        val chapters = anchors.asReversed().map { a ->
            val name = a.selectFirst(".chapter-item__name")?.text()?.ifBlank { null } ?: a.text()
            val href = a.attr("href")
            val absHref = if (href.startsWith("http")) href else baseUrl + href
            val dateText = a.selectFirst(".chapter-item__date")?.text()?.ifBlank { null }
                ?: a.selectFirst("time[datetime]")?.attr("datetime")
                ?: a.selectFirst(".chapter-release-date, span.chapter-release-date")?.text()
            SChapter.create().apply {
                this.name = name
                this.url = absHref
                this.date_upload = parseChapterDate(dateText)
            }
        }

        return Observable.just(chapters)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = if (chapter.url.startsWith("http")) chapter.url else baseUrl + chapter.url
        return GET(url, headers)
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
}
