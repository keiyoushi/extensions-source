package eu.kanade.tachiyomi.multisrc.zingtheme

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.DEFAULT_CACHE_CONTROL
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration.Companion.seconds

/**
 * WordPress sites built on the "zing" theme family. Their browse listings differ per site (some
 * paginate in HTML, others through `admin-ajax.php`), but chapter images are always handed out by
 * the `z_do_ajax`/`decode_images` action in server-decoded batches, gated by a nonce inlined into
 * every page as a `zing = {...}` object.
 */
abstract class ZingTheme : KeiSource() {

    /** First path segment of a manga page, e.g. `manga` in `/manga/some-title/`. */
    protected open val mangaPathSegment = "manga"

    /** POST field that carries the chapter id in the `decode_images` call. */
    protected open val chapterIdParam = "reading_chapter"

    protected open val mangaListSelector = "div.entry-tag"
    protected open val mangaLinkSelector = ".name a"
    protected open val mangaTitleSelector = "h1.font-bold"
    protected open val thumbnailSelector = "img.thumb"
    protected open val descriptionSelector = "div.content-text"
    protected open val genreSelector = "div.genres-wrap a"

    /** Element holding one chapter. It may be the link itself - see [chapterLinkSelector]. */
    protected open val chapterListSelector = "div.chapters-list a"

    /** Link inside a chapter row, or `null` when the row element is already the `<a>`. */
    protected open val chapterLinkSelector: String? = null

    /** Element holding the chapter name, or `null` to use the link's own text. */
    protected open val chapterNameSelector: String? = "span"

    /** Element holding the chapter's upload date, or `null` when the site shows none. */
    protected open val chapterDateSelector: String? = null

    /** Matches only when a next page exists; `null` for listings that are never paginated. */
    protected open val nextPageSelector: String? = null

    /** Backlink from a chapter page to its manga, used to resolve pasted chapter URLs. */
    protected open val mangaBacklinkSelector = "div.manga-name h2 a"

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2, 1.seconds) { it.host == baseUrlHost }
        addInterceptor(::imageContentTypeInterceptor)
        completeCdnCertificateChain()
    }

    /**
     * The image CDN sends only its leaf certificate and omits the intermediate that signed it.
     * Desktop stacks paper over this by fetching the missing certificate from the leaf's AIA
     * extension, but Android's does not, so the handshake fails with "Trust anchor for
     * certification path not found" - intermittently, since a chain seen on one connection gets
     * cached and briefly rescues later ones.
     *
     * The intermediate is bundled and spliced into the chain the server does send. Validation
     * itself is unchanged: the completed chain still has to verify up to the GlobalSign root in
     * Android's own trust store, and anything that fails for a different reason still fails.
     */
    private fun OkHttpClient.Builder.completeCdnCertificateChain() {
        val platformTrustManager = TrustManagerFactory
            .getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()

        val trustManager = ChainCompletingTrustManager(platformTrustManager, missingIntermediate)
        val sslContext = SSLContext.getInstance("TLS")
            .apply { init(null, arrayOf<TrustManager>(trustManager), null) }
        sslSocketFactory(sslContext.socketFactory, trustManager)
    }

    protected fun parseMangaList(document: Document) = MangasPage(
        document.select(mangaListSelector).map(::mangaFromElement),
        nextPageSelector?.let { document.selectFirst(it) != null } == true,
    )

    protected open fun mangaFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst(mangaLinkSelector)!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = cleanTitle(link.text())
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    /**
     * Genres these sites do not actually serve, subtracted from [GENRE_GROUPS]. Every entry in
     * that list was probed against the live site; the ones that came back empty are named here
     * rather than being left in the picker to return nothing.
     */
    protected open val unsupportedGenres: Set<String> = emptySet()

    private val genreGroups by lazy {
        GENRE_GROUPS
            .mapValues { (_, names) -> names.filterNot { it in unsupportedGenres } }
            .filterValues { it.isNotEmpty() }
    }

    /** The genre list is baked in, so nothing has to be fetched and [data] is always null. */
    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(Filter.Header("テキスト検索中はジャンルが無視されます"))
            add(Filter.Header("複数選ぶと「いずれかを含む」で検索します"))
            add(Filter.Header("一度に選べるのは最大 $MAX_GENRES ジャンルです"))
            genreGroups.forEach { (title, names) -> add(GenreGroup(title, names)) }
        },
    )

    /**
     * Neither site can filter on more than one genre in a single request, so a multi-genre
     * search is N requests fanned out and merged. Results are interleaved rather than
     * concatenated so that one genre cannot crowd the others out of the first page, and
     * deduplicated because a title tagged with two picked genres comes back from both.
     */
    protected suspend fun mergeGenrePages(
        genres: List<String>,
        page: Int,
        fetch: suspend (String, Int) -> MangasPage,
    ): MangasPage = mergePages(genres, page, fetch)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(
            mangaDetailsParse(document),
            document.select(chapterListSelector).map(::chapterFromElement),
        )
    }

    protected open fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = cleanTitle(document.selectFirst(mangaTitleSelector)!!.text())
        thumbnail_url = document.selectFirst(thumbnailSelector)?.absUrl("src")
        description = document.selectFirst(descriptionSelector)?.wholeText()?.trim()
        genre = document.select(genreSelector).joinToString { it.text() }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost) return null
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        if (url.pathSegments.firstOrNull() == mangaPathSegment) {
            return mangaDetailsParse(document).apply { setUrlWithoutDomain(url.toString()) }
        }
        // Chapters are plain posts at the site root, so a pasted chapter link is resolved through
        // the backlink the reader page renders below the images.
        val mangaUrl = document.selectFirst(mangaBacklinkSelector)?.absUrl("href") ?: return null
        return mangaDetailsParse(client.get(mangaUrl, Fetch.DOCUMENT).asJsoup())
            .apply { setUrlWithoutDomain(mangaUrl) }
    }

    protected open fun chapterFromElement(element: Element) = SChapter.create().apply {
        val link = chapterLinkSelector?.let { element.selectFirst(it)!! } ?: element
        setUrlWithoutDomain(link.absUrl("href"))
        name = chapterNameSelector?.let { element.selectFirst(it)?.text() } ?: link.text()
        chapter_number = CHAPTER_NUMBER_REGEX.find(name)?.value?.toFloatOrNull() ?: -1f
        date_upload = chapterDateSelector
            ?.let { element.selectFirst(it)?.text() }
            ?.let(::parseChapterDate)
            ?: 0L
    }

    /** Returning 0 makes the app keep whatever date it already has instead of overwriting it. */
    protected open fun parseChapterDate(date: String): Long = 0L

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(baseUrl + chapter.url, Fetch.DOCUMENT)
        val chapterUrl = response.request.url
        val origin = chapterUrl.resolve("/")!!.toString().removeSuffix("/")
        val html = response.use { it.body.string() }
        val chapterId = chapterIdRegex.find(html)?.groupValues?.get(1)
            ?: throw Exception("Could not find the chapter id")

        val imageUrls = mutableListOf<String>()
        var imgIndex = 0
        while (true) {
            val result = callZing(
                ACTION_IMAGES,
                chapterIdParam to chapterId,
                "img_index" to imgIndex.toString(),
                "content" to "",
                origin = origin,
                from = chapterUrl.toString(),
            )
            result.mes.asFragment(result.origin).select("img").mapTo(imageUrls) { it.absUrl("src") }

            // Images are decoded server-side in batches, and img_index is the cursor into that
            // queue. A cursor that stops moving means there is nothing left to hand out, which
            // guards against the `going` flag alone keeping us in this loop forever.
            if (!result.hasMore || result.imgIndex <= imgIndex) break
            imgIndex = result.imgIndex
            delay(result.nextTimeout.coerceIn(0L, MAX_BATCH_DELAY_MS))
        }
        return imageUrls.mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    @Serializable
    protected class ZingResponse(
        val mes: String,
        // `going` is "yes"/"no" on the listing actions but 1/0 on the image one, and the top-list
        // action omits it entirely, so it can't be typed as a Boolean or an Int.
        private val going: JsonPrimitive = JsonPrimitive(0),
        @SerialName("img_index") val imgIndex: Int = 0,
        @SerialName("next_timeout") val nextTimeout: Long = 0,
    ) {
        val hasMore: Boolean get() = going.content == "1" || going.content == "yes"

        @Transient
        var origin: String = ""
            internal set
    }

    private val configs = OriginCache<ApiConfig> { baseUrl }

    private suspend fun zingConfig(origin: String, refresh: Boolean): ApiConfig = configs.get(origin, "config", refresh) {
        client.get(origin, Fetch.DOCUMENT, cacheControl = if (refresh) CacheControl.FORCE_NETWORK else DEFAULT_CACHE_CONTROL)
            .apiConfig()
    }

    protected suspend fun callZing(
        action: String,
        vararg params: Pair<String, String>,
        origin: String = baseUrl,
        from: String = "/",
    ): ZingResponse = retryZing(action, { refresh -> zingConfig(origin, refresh) }, { configs.invalidate(origin, "config") }) { config ->
        val ajaxHeaders = config.headers(headers, from)
        client.post(config.ajaxUrl, ajaxHeaders, Fetch.XHR.from(config.pageUrl(from)), formBody(action, config.nonce, params))
            .parseAs<ZingResponse>().apply { this.origin = config.origin.toString().removeSuffix("/") }
    }

    private fun formBody(action: String, nonce: String, params: Array<out Pair<String, String>>): RequestBody = FormBody.Builder().apply {
        add("action", "z_do_ajax")
        add("_action", action)
        add("nonce", nonce)
        params.forEach { (key, value) -> add(key, value) }
    }.build()

    /** The CDN labels every image `application/octet-stream`, which the reader cannot decode. */
    private fun imageContentTypeInterceptor(chain: Interceptor.Chain): Response = imageContentType(chain.proceed(chain.request()), chain.request().url)

    protected fun String.asFragment(origin: String = baseUrl): Document = Jsoup.parseBodyFragment(this, origin)

    /** Every title on these sites is suffixed with a "(Raw - Free)" style SEO tag. */
    protected open fun cleanTitle(title: String) = title.replace(TITLE_NOISE_REGEX, "").trim()

    private val chapterIdRegex by lazy { Regex("""\s$chapterIdParam:\s*(\d+),""") }

    companion object {
        /** Picking more than a handful means one request each; the merge stops being useful. */
        const val MAX_GENRES = 4

        private const val ACTION_IMAGES = "decode_images"
        private const val MAX_BATCH_DELAY_MS = 10_000L

        private val CHAPTER_NUMBER_REGEX = Regex("""\d+(?:\.\d+)?""")
        private val TITLE_NOISE_REGEX = Regex("""\(?\s*Raw\s*[-–—]?\s*Free\s*\)?""", RegexOption.IGNORE_CASE)

        /** Groups are display-only; the site has no notion of them. */
        private val GENRE_GROUPS: Map<String, List<String>> = linkedMapOf(
            "主要ジャンル" to listOf(
                "アクション",
                "アクション・アドベンチャー",
                "アドベンチャー",
                "バトル",
                "バトル・アクション",
                "SF・ファンタジー",
                "ファンタジー",
                "SF",
                "異世界",
                "転生",
                "異世界・転生",
                "ギャグ・コメディ",
                "コメディ",
                "ギャグ",
                "学園コメディ",
                "ラブコメ",
                "ロマンス",
                "恋愛",
                "純愛",
                "ラブストーリー",
                "愛憎劇",
                "ホラー",
                "ミステリー",
                "ミステリー・サスペンス",
                "サスペンス",
                "心理戦",
                "心理的",
                "バイオレンス",
                "犯罪",
                "復讐",
                "スポーツ",
                "グルメ",
                "料理",
                "音楽",
                "芸術",
                "ヒューマンドラマ",
                "ほのぼの・癒し",
                "日常",
                "くらし・生活",
                "人情",
                "感動",
                "Ecchi",
                "ハーレム",
                "百合",
                "耽美",
                "萌え",
            ),
            "設定・世界観" to listOf(
                "学園",
                "高校生",
                "大学生",
                "先生",
                "先輩・後輩",
                "幼馴染・同級生",
                "超自然的",
                "超能力",
                "魔法",
                "勇者",
                "異種族",
                "幽霊・妖怪・モンスター",
                "吸血鬼",
                "天使・悪魔",
                "擬人化",
                "ロボット",
                "アンドロイド",
                "モンスター",
                "歴史",
                "歴史・時代劇",
                "時代モノ",
                "江戸時代",
                "戦国時代",
                "幕末・明治維新",
                "大正時代",
                "戦争",
                "ミリタリー",
                "タイムスリップ",
                "神話",
                "動物・ペット",
                "長編",
                "4コマ",
            ),
            "人物・関係" to listOf(
                "お嬢様",
                "貴族・大富豪",
                "悪役令嬢",
                "シンデレラストーリー",
                "三角関係",
                "年の差",
                "結婚",
                "社内恋愛",
                "不倫・浮気",
                "家族",
                "大家族",
                "姉妹",
                "子育て",
                "ギャル",
                "ヤンキー",
                "ツンデレ",
                "お姉さん",
                "OL",
                "サラリーマン",
                "アイドル・芸能人",
                "俳優・女優",
                "モデル",
                "男の娘・女装",
            ),
            "職業・題材" to listOf(
                "ビジネス",
                "職業・ビジネス",
                "職業",
                "政治・ビジネス",
                "医療",
                "病院系",
                "警察・特殊部隊",
                "刑事・探偵",
                "裏稼業",
                "極道・刑事",
                "借金",
                "ギャンブル",
                "格闘技",
                "剣道・剣術",
                "柔道",
                "空手",
                "弓道",
                "相撲",
                "野球",
                "サッカー",
                "バスケットボール",
                "アスリート",
                "スケート",
                "麻雀",
                "競輪・自転車",
                "競馬･競輪･競艇",
                "錬金術",
                "宗教",
                "哲学",
                "IT・コンピューター",
                "旅行・観光",
                "ファッション",
                "風俗",
                "成人向け",
            ),
            "読者層" to listOf(
                "少年",
                "少年マンガ",
                "少女",
                "少女マンガ",
                "青年",
                "青年マンガ",
                "女性マンガ",
                "セイネン",
                "女子校生",
                "ロリ",
                "ショタコン",
                "少年愛",
            ),
            "メディア展開" to listOf(
                "アニメ化",
                "映画化",
                "ドラマ化",
                "メディア化",
                "コミカライズ(小説・ゲーム)",
                "電子特典付き",
                "オムニバス",
            ),
        )

        /**
         * `GlobalSign GCC R6 AlphaSSL CA 2025`, the intermediate the image CDN fails to send.
         * Fetched from the CA Issuers URI in the leaf's Authority Information Access extension
         * (`secure.globalsign.com/cacert/gsgccr6alphasslca2025.crt`); its subject matches the
         * leaf's issuer and it is itself signed by `GlobalSign Root CA - R6`.
         *
         * SHA-256: A8:83:55:92:31:F8:38:8D:AF:35:CE:41:C8:10:10:40:
         *          AE:8F:D9:B6:56:43:42:47:B9:47:5A:F5:92:CC:08:CA
         *
         * Expires 2027-05-21. Once it does - or once the CDN switches issuers - the chain will be
         * incomplete again and this needs replacing.
         */
        internal val missingIntermediate: X509Certificate by lazy {
            val der = Base64.getDecoder().decode(INTERMEDIATE_BASE64)
            CertificateFactory.getInstance("X.509")
                .generateCertificate(der.inputStream()) as X509Certificate
        }

        private const val INTERMEDIATE_BASE64 =
            "MIIFjTCCA3WgAwIBAgIRAIN9TriekS/nLK07x2kt3CAwDQYJKoZIhvcNAQELBQAwTDEgMB4GA1UE" +
                "CxMXR2xvYmFsU2lnbiBSb290IENBIC0gUjYxEzARBgNVBAoTCkdsb2JhbFNpZ24xEzARBgNVBAMT" +
                "Ckdsb2JhbFNpZ24wHhcNMjUwNTIxMDIzNjUyWhcNMjcwNTIxMDAwMDAwWjBVMQswCQYDVQQGEwJC" +
                "RTEZMBcGA1UEChMQR2xvYmFsU2lnbiBudi1zYTErMCkGA1UEAxMiR2xvYmFsU2lnbiBHQ0MgUjYg" +
                "QWxwaGFTU0wgQ0EgMjAyNTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAJ/oiu0Bviq5" +
                "2UUEADbFWmgu3rC7KDSMoorLN1Wd03McG3Z1aP71DlPCE33838r72Dfuj5M9LXfiQLJpAu6MwNEx" +
                "mKOzothw4x0zGf5oBYyrCMGm3fBpLPafwYQ3MchBOWMTbf83rKUPLH48KCJ0MnU8GUl8oA/J81wI" +
                "vbbKPuNrFf6hvJDccjzc4NyxLz3A89zjV2g5whCg5O0u9YX4Zxk9JHuc/LvllOJO4waAYLjbWBJk" +
                "z3rV3ts1SmSYnJqmyRTIjXwQgRvhEYqtDbRskt0W7M6cPwCze3GTBN2UHNpHkMs3YmVxku68I0aO" +
                "Qn5+uz//fDROP3z1Z/7IAPteRtECAwEAAaOCAV8wggFbMA4GA1UdDwEB/wQEAwIBhjAdBgNVHSUE" +
                "FjAUBggrBgEFBQcDAQYIKwYBBQUHAwIwEgYDVR0TAQH/BAgwBgEB/wIBADAdBgNVHQ4EFgQUxbST" +
                "j28r3B5Iv7cQMIXO0bK7SC0wHwYDVR0jBBgwFoAUrmwFo5MT4qLn4tcc1sfwf8hnU6AwewYIKwYB" +
                "BQUHAQEEbzBtMC4GCCsGAQUFBzABhiJodHRwOi8vb2NzcDIuZ2xvYmFsc2lnbi5jb20vcm9vdHI2" +
                "MDsGCCsGAQUFBzAChi9odHRwOi8vc2VjdXJlLmdsb2JhbHNpZ24uY29tL2NhY2VydC9yb290LXI2" +
                "LmNydDA2BgNVHR8ELzAtMCugKaAnhiVodHRwOi8vY3JsLmdsb2JhbHNpZ24uY29tL3Jvb3QtcjYu" +
                "Y3JsMCEGA1UdIAQaMBgwCAYGZ4EMAQIBMAwGCisGAQQBoDIKAQMwDQYJKoZIhvcNAQELBQADggIB" +
                "AB/uvBuZf4CiuSahwiXn4geF52roAH+6jxsEPTXTfb7bbeMDXsYgRRsOTNA70ruZTnz5DfFMuBhN" +
                "oFhIFb0qR1izdy6VkdKOqFPNF2dOFI1EcnY9l2ory9mrzHqVbrL4vzUd17FLUVyjTVU7PAv4nxyh" +
                "nO1GTeT83YlrdRF31NyR6bvZVTEERHmpbWSgeveJLRtaMzlGWiLZ8IwkH7o6GH3jp/KPtDW4Npu8" +
                "w64HrRZdN2pqQhi7+YKwfHM7H+2UdM1BGN0sjOWMVbMSB9MtCsleS2Mb7TRZEbOHxECJLLIluQyp" +
                "Zr7Pol3+hAqrhyKIk+6y+Da0NeDuWxW59Ku4NvClqW1UFX1SpfNGhzVfp/CH+vPM1tySomx2jE0E" +
                "nYZuGwVucXPBsp5nUWqUV9+143glVuS7GTg9hFPjNBInn17HbCoIIQIOzj5Vd9bK3A9UGxXNpwen" +
                "DHEalCsD/4eQYDHPhFE7sNe0D/OXu+FAM02VZkARx37Jp4bDdujvgL9PvZPR3wThvDN1CTU8Bc3x" +
                "ea3yKFAraKcPZLkhReQUAm2VpR+HSJRPlUpYizlF9WkLh3KcAVCBJWvnOkVwxyU5QJMcnwW95JlO" +
                "tx+9100GL99jHE5rs3gXp7F4bg8H01QT9jVOhBBmQ7nQoXuwI0tqal2QUqZz3eeu62CU7xBwtfYR"
    }
}

/** One collapsible section of genre checkboxes. The grouping is ours; the site has no such notion. */
class GenreGroup(title: String, genres: List<String>) : Filter.Group<Filter.CheckBox>(title, genres.map(::GenreCheckBox)) {

    val selected get() = state.filter { it.state }.map { it.name }
}

private class GenreCheckBox(name: String) : Filter.CheckBox(name)

/**
 * Retries validation with [intermediate] appended when the chain the server sent is too short to
 * build a path. Delegates every decision to [delegate], so a chain that is untrusted for any other
 * reason is still rejected. OkHttp checks hostnames separately; revocation policy is platform-specific.
 */
internal class ChainCompletingTrustManager(
    private val delegate: X509TrustManager,
    private val intermediate: X509Certificate,
) : X509TrustManager {

    override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = delegate.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            delegate.checkServerTrusted(chain, authType)
        } catch (e: CertificateException) {
            if (chain.any { it.subjectX500Principal == intermediate.subjectX500Principal }) throw e
            delegate.checkServerTrusted(chain + intermediate, authType)
        }
    }
}
