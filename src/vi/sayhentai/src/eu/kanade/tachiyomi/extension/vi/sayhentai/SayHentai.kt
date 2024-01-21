package eu.kanade.tachiyomi.extension.vi.sayhentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar

// This is basically Madara CSS without the actual Madara bits, grrr
class SayHentai : ParsedHttpSource() {

    override val name = "SayHentai"

    override val lang = "vi"

    override val baseUrl = "https://sayhentai.fun"

    override val supportsLatest = false

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/?page=$page")

    override fun popularMangaSelector() = "div.page-item-detail"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val a = element.selectFirst("a")!!

        setUrlWithoutDomain(a.attr("abs:href"))
        title = a.attr("title")
        thumbnail_url = element.selectFirst("img")?.imageFromElement()
    }

    override fun popularMangaNextPageSelector() = "ul.pager a[rel=next]"

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("s", query)
            } else {
                (if (filters.isEmpty()) getFilterList() else filters).forEach {
                    when (it) {
                        is GenreList -> addPathSegments(it.values[it.state].path)
                        else -> {}
                    }
                }
            }

            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("div.post-title h1")!!.text()
        author = document.selectFirst("div.summary-heading:contains(Tác giả) + div.summary-content")?.text()
        description = document.selectFirst("div.summary__content")?.text()
        genre = document.select("div.genres-content a[rel=tag]").joinToString { it.text() }
        status = when (document.selectFirst("div.summary-heading:contains(Trạng thái) + div.summary-content")?.text()) {
            "Đang Ra" -> SManga.ONGOING
            "Hoàn Thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("div.summary_image img")?.imageFromElement()
    }

    override fun chapterListSelector() = "li.wp-manga-chapter"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val a = element.selectFirst("a")!!
        val date = element.selectFirst("span.chapter-release-date")?.text()

        setUrlWithoutDomain(a.attr("abs:href"))
        name = a.text()

        if (date != null) {
            date_upload = parseRelativeDate(date)
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.page-break img").mapIndexed { i, it ->
            Page(i, imageUrl = it.imageFromElement())
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Không dùng chung với tìm kiếm bằng từ khoá."),
        GenreList(getGenreList()),
    )

    private fun Element.imageFromElement(): String? {
        return when {
            hasAttr("data-src") -> attr("abs:data-src")
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("srcset") -> attr("abs:srcset").substringBefore(" ")
            else -> attr("abs:src")
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val (valueString, unit) = date.substringBefore(" trước").split(" ")
        val value = valueString.toInt()

        val calendar = Calendar.getInstance().apply {
            when (unit) {
                "giây" -> add(Calendar.SECOND, -value)
                "phút" -> add(Calendar.MINUTE, -value)
                "giờ" -> add(Calendar.HOUR_OF_DAY, -value)
                "ngày" -> add(Calendar.DAY_OF_MONTH, -value)
                "tuần" -> add(Calendar.WEEK_OF_MONTH, -value)
                "tháng" -> add(Calendar.MONTH, -value)
                "năm" -> add(Calendar.YEAR, -value)
            }
        }

        return calendar.timeInMillis
    }

    // document.querySelectorAll("span.number-story").forEach((e) => e.remove())
    // copy([...document.querySelectorAll(".page-category ul li a")].map((e) => `Genre("${e.textContent.trim()}", "${e.href.replace("https://sayhentai.fun/", "")}"),`).join("\n"))
    //
    // There are 2 pseudo-genres: Tất cả (All), and Hoàn thành (Completed), mostly for convenience.
    private fun getGenreList() = arrayOf(
        Genre("Tất cả", ""),
        Genre("18+", "genre/18"),
        Genre("3D", "genre/3d"),
        Genre("Action", "genre/action"),
        Genre("Adult", "genre/adult"),
        Genre("Bạo Dâm", "genre/bao-dam"),
        Genre("Chơi Hai Lỗ", "genre/choi-hai-lo"),
        Genre("Comedy", "genre/comedy"),
        Genre("Detective", "genre/detective"),
        Genre("Doujinshi", "genre/doujinshi"),
        Genre("Drama", "genre/drama"),
        Genre("Ecchi", "genre/ecchi"),
        Genre("Fantasy", "genre/fantasy"),
        Genre("Gangbang", "genre/gangbang"),
        Genre("Gender Bender", "genre/gender-bender"),
        Genre("Giáo Viên", "genre/giao-vien"),
        Genre("Group", "genre/group"),
        Genre("Hãm Hiếp", "genre/ham-hiep"),
        Genre("Harem", "genre/harem"),
        Genre("Hậu Môn", "genre/hau-mon"),
        Genre("Historical", "genre/historical"),
        Genre("Hoàn thành", "completed"),
        Genre("Horror", "genre/horror"),
        Genre("Housewife", "genre/housewife"),
        Genre("Josei", "genre/josei"),
        Genre("Không Che", "genre/khong-che"),
        Genre("Kinh Dị", "genre/kinh-di"),
        Genre("Lão Già Dâm", "genre/lao-gia-dam"),
        Genre("Loạn Luân", "genre/loan-luan"),
        Genre("Loli", "genre/loli"),
        Genre("Manga", "genre/manga"),
        Genre("Manhua", "genre/manhua"),
        Genre("Manhwa", "genre/manhwa"),
        Genre("Martial Arts", "genre/martial-arts"),
        Genre("Mature", "genre/mature"),
        Genre("Milf", "genre/milf"),
        Genre("Mind Break", "genre/mind-break"),
        Genre("Mystery", "genre/mystery"),
        Genre("Ngực Lớn", "genre/nguc-lon"),
        Genre("Ngực Nhỏ", "genre/nguc-nho"),
        Genre("Nô Lệ", "genre/no-le"),
        Genre("NTR", "genre/ntr"),
        Genre("Nữ Sinh", "genre/nu-sinh"),
        Genre("Old Man", "genre/old-man"),
        Genre("One shot", "genre/one-shot"),
        Genre("Oneshot", "genre/oneshot"),
        Genre("Psychological", "genre/psychological"),
        Genre("Rape", "genre/rape"),
        Genre("Romance", "genre/romance"),
        Genre("School Life", "genre/school-life"),
        Genre("Sci-fi", "genre/sci-fi"),
        Genre("Seinen", "genre/seinen"),
        Genre("Series", "genre/series"),
        Genre("Shoujo", "genre/shoujo"),
        Genre("Shoujo Ai", "genre/shoujo-ai"),
        Genre("Shounen", "genre/shounen"),
        Genre("Slice of Life", "genre/slice-of-life"),
        Genre("Smut", "genre/smut"),
        Genre("Sports", "genre/sports"),
        Genre("Supernatural", "genre/supernatural"),
        Genre("Tragedy", "genre/tragedy"),
        Genre("Virgin", "genre/virgin"),
        Genre("Webtoon", "genre/webtoon"),
        Genre("Y Tá", "genre/y-ta"),
        Genre("Yaoi", "genre/yaoi"),
        Genre("Yuri", "genre/yuri"),
    )

    private class Genre(val name: String, val path: String) {
        override fun toString() = name
    }

    private class GenreList(genres: Array<Genre>) : Filter.Select<Genre>("Thể loại", genres)
}
