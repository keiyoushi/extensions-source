package eu.kanade.tachiyomi.extension.zh.miaoqu

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSWeb
import eu.kanade.tachiyomi.multisrc.mccms.mobileUrl
import eu.kanade.tachiyomi.multisrc.mccms.pcHeaders
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.experimental.xor

// This site shares the same database with 6Manhua (SixMH), but uses manga slug as URL.
@Source
abstract class Miaoqu : MCCMSWeb() {
    override fun parseListing(document: Document): MangasPage {
        // There's no genre list to parse, so we fetch genres from mobile page in fetchGenresPage()
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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = try {
        super.getSearchMangaList(page, query, filters)
    } catch (e: HttpException) {
        if (e.code == 404) throw Exception("服务器错误，无法搜索")
        throw e
    }

    // Use mobile page
    override suspend fun fetchMangaPage(manga: SManga): Document = client.get(getMangaUrl(manga)).asJsoup()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
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

    override fun chapterListSelector() = "ul.list > li"

    // Might return HTTP 500 with page data
    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(baseUrl + chapter.url, pcHeaders, ensureSuccess = false))

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

    override suspend fun fetchGenresPage(): Document = client.get("${baseUrl.mobileUrl()}/category/").asJsoup()
}

private fun String.substringBetween(left: String, right: Char): String {
    val index = indexOf(left)
    check(index != -1) { "string doesn't match $left[...]$right" }
    val startIndex = index + left.length
    val endIndex = indexOf(right, startIndex)
    check(endIndex != -1) { "string doesn't match $left[...]$right" }
    return substring(startIndex, endIndex)
}
