package eu.kanade.tachiyomi.multisrc.zing

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import javax.net.ssl.SSLContext

/** Sites on the Zing WordPress themes: the reader's decode_images ajax, the image CDN and the rotating domains */
abstract class ZingBase : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = apply {
        val trustManager = AiaTrustManager(network.client)
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }
        sslSocketFactory(sslContext.socketFactory, trustManager)
        rateLimit(2) { it.host == baseUrl.toHttpUrl().host && it.pathSegments.firstOrNull() != "wp-content" }
    }

    // Old domains redirect to the current one; its pages name it in the theme's config
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> {
        val homeUrl = client.get(baseUrl, Fetch.DOCUMENT).asJsoup().zingConfig()?.homeUrl?.toHttpUrlOrNull()
        return if (homeUrl != null && isSiteHost(homeUrl)) listOf("https://${homeUrl.host}") else emptyList()
    }

    protected val ajaxUrl get() = baseUrl.toHttpUrl().newBuilder().encodedPath(AJAX_PATH).build()

    // The site's current domain, or an old one: same name, another TLD
    protected fun isSiteHost(url: HttpUrl): Boolean {
        val host = baseUrl.toHttpUrl().host
        if (url.host == host) return true
        val labels = url.host.split('.')
        return labels.size == 2 && labels[0] == host.substringBefore('.')
    }

    protected fun Document.zingConfig() = selectFirst("script:containsData(zing =)")
        ?.let { ZING_CONFIG_REGEX.find(it.data()) }
        ?.groupValues?.get(1)
        ?.parseAs<ZingConfigDto>()

    protected fun String.removeTitleSuffix() = replace(TITLE_SUFFIX_REGEX, "")

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(document), parseChapters(document))
    }

    /** The series at [mangaUrl], for url search */
    protected suspend fun fetchSeries(mangaUrl: HttpUrl): SManga {
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()
        return parseDetails(document).apply {
            setUrlWithoutDomain(mangaUrl.toString())
            initialized = true
        }
    }

    protected abstract fun parseDetails(document: Document): SManga

    protected abstract fun parseChapters(document: Document): List<SChapter>

    /** The reader's decode_images form fields that name the chapter, or null when the page has no reader */
    protected abstract fun readerFields(document: Document): List<Pair<String, String>>?

    /** Reader fields of the themes that name the chapter by its post id, with the ajax nonce */
    protected fun postIdReaderFields(document: Document): List<Pair<String, String>>? {
        val reader = document.selectFirst("script:containsData(decode_images)")?.data() ?: return null
        return listOf("p" to POST_ID_REGEX.find(reader)!!.groupValues[1], "nonce" to document.zingConfig()!!.nonce)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        val fields = readerFields(document) ?: throw Exception("画像が見つかりません。WebViewで開いてください")
        val fetch = Fetch.XHR.from(chapterUrl)

        var imgIndex = 0
        // Images so far, posted back like the site's reader: the site keeps them as its copy of the chapter
        var content = ""
        val images = buildList {
            do {
                val body = FormBody.Builder()
                    .add("action", "z_do_ajax")
                    .add("_action", "decode_images")
                    .apply { fields.forEach { (name, value) -> add(name, value) } }
                    .add("img_index", imgIndex.toString())
                    .add("content", content)
                    .build()
                val result = client.post(ajaxUrl, fetch, body).parseAs<DecodeImagesDto>()
                content += result.html
                val batch = Jsoup.parseBodyFragment(result.html, baseUrl).select("img").map { it.absUrl("src") }
                addAll(batch)
                val advanced = result.imgIndex > imgIndex
                imgIndex = result.imgIndex
                // Past the last page the site keeps answering going = 1 with no images; a cursor that doesn't move would never end
            } while (result.going == 1 && batch.isNotEmpty() && advanced)
        }

        return images.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    companion object {
        private const val AJAX_PATH = "/wp-admin/admin-ajax.php"
        private val ZING_CONFIG_REGEX = Regex("""zing\s*=\s*(\{.*?\})""")
        private val POST_ID_REGEX = Regex("""\bp:\s*(\d+)""")

        // Lists write the suffix with an en dash, some series pages with a hyphen
        private val TITLE_SUFFIX_REGEX = Regex("""\s*\(Raw\s*[-–]\s*Free\)""")
    }
}
