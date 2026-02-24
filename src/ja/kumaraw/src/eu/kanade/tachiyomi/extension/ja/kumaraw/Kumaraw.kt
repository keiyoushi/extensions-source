package eu.kanade.tachiyomi.extension.ja.kumaraw

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class Kumaraw : HttpSource() {

    override val name = "Kumaraw"
    override val baseUrl = "https://kumaraw.com"
    override val lang = "ja"
    override val supportsLatest = true

    override val client = network.cloudflareClient
    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Tokyo") // Implied
    }

    private val json: Json by lazy {
        Json {
            allowTrailingComma = true
        }
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangasTopDay = document.select("div#top_day div.story_item")
        val mangasTopMonth = document.select("div#top_month div.story_item")
        val mangasTopAll = document.select("div#top_all div.story_item")

        // Omitted carousel as its just top all
        val mangas = (mangasTopDay + mangasTopMonth + mangasTopAll)
            .map(::searchMangaFromElement)
            .distinctBy { it.url }

        return MangasPage(mangas, false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page > 1) "/latest/$page" else ""
        return GET(baseUrl + pageStr, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document
            .select("div.recoment_box div.story_item")
            .map(::searchMangaFromElement)

        val hasNextPage = document.selectFirst(".pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val slug = query.toHttpUrlOrNull()
                ?.pathSegments
                ?.getOrNull(1)
                ?: throw Exception("無効なURL") // TODO: check MTL

            // Rewrite to strip suffixes after slug
            val newUrl = "$baseUrl/manga/$slug"
            return fetchMangaDetails(SManga.create().apply { setUrlWithoutDomain(newUrl) })
                .map { manga -> MangasPage(listOf(manga), hasNextPage = false) }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document
            .select("div.recoment_box div.story_item")
            .map(::searchMangaFromElement)
        return MangasPage(mangas, false)
    }

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val a = element.selectFirst(".mg_name a")!!

        setUrlWithoutDomain(a.absUrl("href"))
        title = a.text()

        // Normally downsampled
        element.selectFirst("img")
            ?.absUrl("src")
            ?.toHttpUrlOrNull()
            ?.newBuilder()
            ?.query(null)
            ?.build()
            ?.also { newUrl ->
                thumbnail_url = newUrl.toString()
            }
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            title = document.selectFirst("h1")!!.text()
            author = document.selectFirst(".detail_listInfo > .item > .info_label:contains(著者) + .info_value")?.text()
                ?.takeIf { it != "Updating" }

            genre = document.select(".detail_listInfo a[href*='/genres/']").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".detail_avatar img")?.absUrl("src")

            description = buildString {
                // Rating
                document.selectFirst(".detail_rate p span:nth-child(1)")?.text()?.substringBefore("/")?.also { rating ->
                    document.selectFirst(".detail_rate p span:nth-child(2)")?.text()?.also { ratingCount ->
                        val ratingString = getRatingString(rating, ratingCount.toIntOrNull() ?: 0)
                        if (ratingString.isNotEmpty()) {
                            if (isNotEmpty()) append("\n")
                            append("評価：", ratingString)
                        }
                    }
                }

                // Views
                document.selectFirst(".detail_listInfo > .item > .info_label:contains(ビュー) + .info_value")
                    ?.text()
                    ?.takeIf(String::isNotEmpty)
                    ?.also {
                        if (isNotEmpty()) append("\n")
                        append("ビュー：", it)
                    }

                // Subscribers / Bookmarks / Readers
                // Original wording: X ユーザーが購読に追加
                document.selectFirst(".detail_groupButton p > span")?.text()
                    ?.takeIf(String::isNotEmpty)
                    ?.takeIf { it != "0" }
                    ?.also {
                        if (isNotEmpty()) append("\n")
                        append("購読者数：", it) // TODO: check MTL
                    }

                // Magazine?
                // In rare cases this includes multiple entries separated by `, `
                document.selectFirst(".detail_listInfo > .item > .info_label:contains(雑誌) + .info_value")
                    ?.text()
                    ?.takeIf(String::isNotEmpty)
                    ?.takeIf { it != "-" }
                    ?.also {
                        if (isNotEmpty()) append("\n")
                        append("雑誌：", it)
                    }

                // Summary
                document.selectFirst(".detail_reviewContent")?.text()
                    ?.takeIf(String::isNotEmpty)
                    ?.takeIf { it != "Updating" }
                    ?.also {
                        if (isNotEmpty()) append("\n\n")
                        append(it)
                    }

                // Alternative names
                document.selectFirst(".detail_listInfo > .item > .info_label:contains(ほかの名前) + .info_value")
                    ?.text()
                    ?.takeIf(String::isNotEmpty)
                    ?.split(",")
                    ?.map(String::trim)
                    ?.distinct()
                    ?.filter { it != title }
                    ?.filter { it != "Updating" }
                    ?.takeIf(List<String>::isNotEmpty)
                    ?.joinToString("\n") { "- $it" }
                    ?.also { altTitles ->
                        if (isNotEmpty()) append("\n\n")
                        appendLine("ほかの名前：")
                        append(altTitles)
                    }
            }
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document
            .select("div.chapter_box div.item")
            .map(::chapterFromElement)
    }

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        val a = element.selectFirst("a.chapter_num")!!

        // 1st column is name
        // 2nd column is views, unused
        // 3rd column is date

        setUrlWithoutDomain(a.absUrl("href"))
        name = a.text().removePrefix("#").trimStart()
        element.selectFirst("p.chapter_info:nth-of-type(2)")?.text()
            ?.also { date_upload = dateFormat.tryParse(it) }
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val script = document.selectFirst("script:containsData(slides_p_path)")?.data()
            ?: throw Exception("スクリプトからの画像URL抽出に失敗しました") // TODO: check MTL

        val encodedImageUrls = script
            .substringAfter("slides_p_path")
            .substringAfter("=")
            .substringBefore(";")
            .parseAs<List<String>>(json)

        return encodedImageUrls.mapIndexed { i, encodedImageUrl ->
            val imageUrl = Base64.decode(encodedImageUrl, Base64.DEFAULT).toString(Charsets.UTF_8)
            Page(i, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Other
    private fun getRatingString(rate: String, rateCount: Int): String {
        val ratingValue = rate.toDoubleOrNull() ?: 0.0
        val ratingStar = when {
            ratingValue >= 4.75 -> "★★★★★"
            ratingValue >= 4.25 -> "★★★★✬"
            ratingValue >= 3.75 -> "★★★★☆"
            ratingValue >= 3.25 -> "★★★✬☆"
            ratingValue >= 2.75 -> "★★★☆☆"
            ratingValue >= 2.25 -> "★★✬☆☆"
            ratingValue >= 1.75 -> "★★☆☆☆"
            ratingValue >= 1.25 -> "★✬☆☆☆"
            ratingValue >= 0.75 -> "★☆☆☆☆"
            ratingValue >= 0.25 -> "✬☆☆☆☆"
            else -> "☆☆☆☆☆"
        }
        return if (ratingValue > 0.0) {
            buildString {
                append(ratingStar, " ", rate)
                if (rateCount > 0) {
                    append(" (", rateCount, ")")
                }
            }
        } else {
            ""
        }
    }
}
