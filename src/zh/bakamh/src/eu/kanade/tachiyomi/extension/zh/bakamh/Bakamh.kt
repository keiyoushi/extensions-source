package eu.kanade.tachiyomi.extension.zh.bakamh

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Bakamh : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日", Locale.CHINESE)

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2) // Rate limit added to prevent 429 errors during library updates
    }

    override fun Headers.Builder.configureHeaders() = apply {
        add("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")
    }

    override val mangaDetailsSelectorStatus = ".post-content_item:contains(状态) .summary-content"

    // Site keeps renaming the chapter <li> class, so match the list structure instead
    override fun chapterListSelector() = "ul.version-chap > li"

    override val chapterDateSelector = "span.chapter-release-date i"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ) = super.fetchMangaUpdate(
        manga.apply {
            url = url.lowercase()
        },
        chapters,
        fetchDetails,
        fetchChapters,
    )

    @Volatile
    private var readerScript: ReaderScript? = null

    override suspend fun fetchChapterDocument(chapterUrl: String) = super.fetchChapterDocument(chapterUrl).also { document ->
        if (document.selectFirst(READER_CONFIG_SELECTOR) == null) return@also
        sendReaderBeacon(chapterUrl)
        val url = document.selectFirst("script#mkjp-reader-window-js")!!.absUrl("src")
        if (readerScript?.url != url) readerScript = ReaderScript(url, client.get(url, Fetch.SCRIPT).body.string())
    }

    // The reader's mkjp_rv beacon; chapters read without it end in the site's third-party tool warning
    private suspend fun sendReaderBeacon(chapterUrl: String) {
        val headers = headersBuilder().set("Referer", chapterUrl).build()
        val body = FormBody.Builder().add("action", "mkjp_rv").build()
        client.post("$baseUrl/wp-admin/admin-ajax.php", headers, Fetch.XHR, body, ensureSuccess = false).close()
    }

    override fun parsePages(document: Document): List<Page> {
        document.selectFirst(".mkjp-ea-lock__title")?.let { title ->
            val needsLogin = document.selectFirst(".mkjp-ea-lock [data-target='#form-login']") != null
            error(if (needsLogin) "需在 WebView 中登录，账号有封禁风险" else title.text())
        }
        document.selectFirst(".mkjp-tool-warning")?.let { error(it.text()) }
        val config = document.selectFirst(READER_CONFIG_SELECTOR)!!.data()
        val slots = document.select(READER_SLOT_SELECTOR).size
        return readerScript!!.pageUrls(config, slots).mapIndexed { index, url -> Page(index, document.location(), url) }
    }
}
