package eu.kanade.tachiyomi.extension.ko.wolfdotcom

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParseDate
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.net.URLEncoder

// W툰's template: the series, episodes and images of 늑대닷컴, on routes of its own
internal class Wtoon(private val source: Wolf) {

    // Lists and search results come whole on one page: the last one is kept for the next pages
    @Volatile
    private var lastEntries: Pair<String, List<SManga>>? = null

    suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val text = query.replace(SPECIAL_CHARS, "").trim()
            if (text.length < 2) {
                throw Exception("두 글자 이상 입력 해주세요.")
            }
            // The site's own search form, which posts EUC-KR
            return entriesPage(page, "search:$text") {
                val body = FormBody.Builder().addEncoded("search_txt", URLEncoder.encode(text, "EUC-KR")).build()
                source.client.post("${source.baseUrl}/sh", source.headers, Fetch.DOCUMENT, body).asJsoup().parseEntries()
            }
        }
        val path = when {
            source.isPhoto -> "w3"
            filters.firstInstanceOrNull<StatusFilter>()?.state == 1 -> "w2"
            else -> "w1"
        }
        val url = "${source.baseUrl}/$path".toHttpUrl().newBuilder().apply {
            filters.filterIsInstance<UrlPartFilter>().forEach { it.addToUrl(this) }
        }.build().toString()
        return entriesPage(page, url) {
            source.client.get(url, source.headers, Fetch.DOCUMENT).asJsoup().parseEntries()
        }
    }

    private suspend fun entriesPage(page: Int, key: String, fetch: suspend () -> List<SManga>): MangasPage {
        val entries = lastEntries?.takeIf { page > 1 && it.first == key }?.second
            ?: fetch().also { lastEntries = key to it }
        return MangasPage(entries.drop((page - 1) * PAGE_SIZE).take(PAGE_SIZE), entries.size > page * PAGE_SIZE)
    }

    private fun Document.parseEntries(): List<SManga> = select(".wbody ul.gallery > li > a[href*=/v1?]").map { link ->
        SManga.create().apply {
            url = link.absUrl("href").toHttpUrl().queryParameter("toon")!!
            title = link.selectFirst(".txt > .subject")!!.ownText()
            thumbnail_url = link.selectFirst(".img > img")?.absUrl("data-original")
        }
    }

    fun getMangaUrl(manga: SManga): String = source.baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(SERIES_PATH)
        .addQueryParameter("toon", manga.url)
        .toString()

    suspend fun fetchMangaUpdate(manga: SManga): SMangaUpdate {
        val document = source.client.get(getMangaUrl(manga), source.headers, Fetch.DOCUMENT).asJsoup()
        val updatedManga = manga.apply {
            title = document.selectFirst(".text-box h1")!!.text()
            thumbnail_url = document.selectFirst(".img-box img")?.absUrl("src")
            description = MarkdownDescription.Builder()
                .description(document.selectFirst(".text-box .txt"))
                .build()
            author = document.info("작가")
            genre = document.info("장르")
        }
        val chapters = document.select(".wbody ul.list > li > a[href*=/v2?]").map { link ->
            val chapUrl = link.absUrl("href").toHttpUrl()
            SChapter.create().apply {
                url = Wolf.ChapterUrl(
                    chapUrl.queryParameter("toon")!!,
                    chapUrl.queryParameter("num")!!,
                ).toJsonString()
                name = link.selectFirst(".subject")!!.ownText()
                date_upload = dateFormat.tryParseDate(link.selectFirst(".date")?.text())
            }
        }
        return SMangaUpdate(updatedManga, chapters)
    }

    // "-" when the series has none
    private fun Document.info(label: String): String? = selectFirst(".text-box .sub:has(> strong:contains($label))")
        ?.ownText()
        ?.split('/')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && it != "-" }
        ?.joinToString()
        ?.ifEmpty { null }

    fun getChapterUrl(chapter: SChapter): String {
        val chapUrl = chapter.url.parseAs<Wolf.ChapterUrl>()
        return source.baseUrl.toHttpUrl().newBuilder()
            .addPathSegment(READER_PATH)
            .addQueryParameter("toon", chapUrl.toon)
            .addQueryParameter("num", chapUrl.num)
            .toString()
    }

    suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = source.client.get(getChapterUrl(chapter), source.headers, Fetch.DOCUMENT).asJsoup()
        return document.select(".wbody img.lazyload[data-original]").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("data-original"))
        }
    }

    companion object {
        private const val PAGE_SIZE = 36
        private const val SERIES_PATH = "v1"
        private const val READER_PATH = "v2"

        // The site's search form refuses any other character
        private val SPECIAL_CHARS = Regex("""[^\p{InHangul_Syllables}0-9a-z ]""", RegexOption.IGNORE_CASE)
    }
}
