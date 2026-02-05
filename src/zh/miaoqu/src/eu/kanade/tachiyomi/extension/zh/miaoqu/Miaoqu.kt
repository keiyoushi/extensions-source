package eu.kanade.tachiyomi.extension.zh.miaoqu

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSWeb
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import kotlin.experimental.xor

// This site shares the same database with 6Manhua (SixMH), but uses manga slug as URL.
class Miaoqu : MCCMSWeb("喵趣漫画", "https://www.miaoqumh.org") {
    override fun parseListing(document: Document): MangasPage {
        // There's no genre list to parse, so we fetch genres from mobile page in getFilterList()
        val entries = document.selectFirst("#mangawrap")!!.children().map { element ->
            SManga.create().apply {
                val img = element.child(0)
                thumbnail_url = img.attr("style").substringBetween("background: url(", ')')
                url = img.attr("href")
                title = element.selectFirst(".manga-name")!!.text()
                author = element.selectFirst(".manga-author")?.text()
            }
        }
        val hasNextPage = run {
            val button = document.selectFirst("#next") ?: return@run false
            button.attr("href").substringAfterLast('/') != document.location().substringAfterLast('/')
        }
        return MangasPage(entries, hasNextPage)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = client.newCall(searchMangaRequest(page, query, filters)).asObservable().map { response ->
        if (response.code == 404) {
            response.close()
            throw Exception("服务器错误，无法搜索")
        }
        searchMangaParse(response)
    }

    // Use mobile page
    override fun mangaDetailsRequest(manga: SManga) = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        description = document.selectFirst(".text")!!.text()

        val infobox = document.selectFirst(".infobox")!!
        title = infobox.selectFirst(".title")!!.text()
        thumbnail_url = infobox.selectFirst("img")!!.attr("src")

        for (element in infobox.select(".tage")) {
            val text = element.text()
            when (text.substring(0, 3)) {
                "作者：" -> author = text.substring(3).trimStart()
                "类型：" -> genre = element.select("a").joinToString { it.text() }
                "更新于" -> description = "$text\n\n$description"
            }
        }
    }

    override fun chapterListRequest(manga: SManga) = GET(getMangaUrl(manga), headers)

    override fun chapterListSelector() = "ul.list > li"

    // Might return HTTP 500 with page data
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(pageListRequest(chapter)).asObservable().map(::pageListParse)

    override fun pageListParse(response: Response): List<Page> {
        val cid = response.request.url.pathSegments.last().removeSuffix(".html").toInt()
        val key = when (cid % 10) {
            0 -> "8-bXd9iN"
            1 -> "8-RXyjry"
            2 -> "8-oYvwVy"
            3 -> "8-4ZY57U"
            4 -> "8-mbJpU7"
            5 -> "8-6MM2Ei"
            6 -> "8-54TiQr"
            7 -> "8-Ph5xx9"
            8 -> "8-bYgePR"
            9 -> "8-Z9A3bW"
            else -> throw Exception("Illegal cid: $cid")
        }.encodeToByteArray()
        check(key.size == 8)
        val data = response.body.string().substringBetween("var DATA='", '\'')
        val bytes = Base64.decode(data, Base64.DEFAULT)
        for (i in bytes.indices) {
            bytes[i] = bytes[i] xor key[i and 7]
        }
        val decrypted = String(Base64.decode(bytes, Base64.DEFAULT))
        return decrypted.parseAs<List<Image>>().mapIndexed { i, image -> Page(i, imageUrl = image.url) }
    }

    @Serializable
    private class Image(val url: String)

    override fun getFilterList(): FilterList {
        config.genreData.fetchGenres(this)
        return super.getFilterList()
    }
}

private fun String.substringBetween(left: String, right: Char): String {
    val index = indexOf(left)
    check(index != -1) { "string doesn't match $left[...]$right" }
    val startIndex = index + left.length
    val endIndex = indexOf(right, startIndex)
    check(endIndex != -1) { "string doesn't match $left[...]$right" }
    return substring(startIndex, endIndex)
}
