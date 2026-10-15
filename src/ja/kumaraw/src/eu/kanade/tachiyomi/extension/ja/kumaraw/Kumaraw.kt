package eu.kanade.tachiyomi.extension.ja.kumaraw

import android.util.Base64
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Kumaraw : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ROOT)

    private val json: Json by lazy {
        Json {
            allowTrailingComma = true
        }
    }

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()

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
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val pageStr = if (page > 1) "/latest/$page" else ""
        val document = client.get(baseUrl + pageStr).asJsoup()
        val mangas = document
            .select("div.recoment_box div.story_item")
            .map(::searchMangaFromElement)

        val hasNextPage = document.selectFirst(".pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.getOrNull(1) ?: return null

        // Rewrite to strip suffixes after slug
        return mangaDetailsParse(client.get("$baseUrl/manga/$slug").asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/mangas".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .build()

        val document = client.get(url).asJsoup()
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
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            mangaDetailsParse(document),
            document.select("div.chapter_box div.item").map(::chapterFromElement),
        )
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(document.location())
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

    // Chapters
    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        val a = element.selectFirst("a.chapter_num")!!

        // 1st column is name
        // 2nd column is views, unused
        // 3rd column is date

        setUrlWithoutDomain(a.absUrl("href"))
        name = a.text().removePrefix("#").trimStart()
        element.selectFirst("p.chapter_info:nth-of-type(2)")?.text()
            ?.also { date_upload = dateFormat.tryParseDate(it, ZoneId.of("Asia/Tokyo")) } // Implied
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
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
