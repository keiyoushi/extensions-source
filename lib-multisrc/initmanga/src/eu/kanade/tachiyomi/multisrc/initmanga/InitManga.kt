package eu.kanade.tachiyomi.multisrc.initmanga

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.decodeHex
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.io.IOException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.time.Duration.Companion.milliseconds

abstract class InitManga :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    protected open val mangaUrlDirectory = "seri"

    protected open val popularUrlSlug = mangaUrlDirectory

    protected open val latestUrlSlug = "son-guncellemeler"

    protected open val authorLabel = "Yazar"

    protected open val artistLabel = "İllüstratör"

    protected open val chapterPrefix = "Bölüm"

    protected open val genreFilterName = "Kategoriler"

    protected open val allGenresLabel = "Tümü"

    protected open val altTitleLabel = "Alternatif Başlık"

    protected open val siteZone: ZoneId = ZoneId.of("Europe/Istanbul")

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::decryptImage)

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(baseUrl.toHttpUrl().newBuilder().addPathSegment(popularUrlSlug), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(baseUrl.toHttpUrl().newBuilder().addPathSegment(latestUrlSlug), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/wp-json/initlise/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", query)
                .addQueryParameter("page", page.toString())
                .build()
            val results = client.get(url, Fetch.XHR).parseAs<List<SearchDto>>()
            // Search also returns other post types (stickers); the endpoint has no type filter
            val mangas = results.filter { it.postType == "manga" }.map { it.toSManga() }
            return MangasPage(mangas, results.size == SEARCH_PAGE_SIZE)
        }

        val genrePath = filters.firstInstanceOrNull<GenreFilter>()?.path
        val url = if (genrePath != null) {
            baseUrl.toHttpUrl().resolve(genrePath)!!.newBuilder()
        } else {
            baseUrl.toHttpUrl().newBuilder().addPathSegment(mangaUrlDirectory)
        }
        return getMangaList(url, page)
    }

    private suspend fun getMangaList(url: HttpUrl.Builder, page: Int): MangasPage {
        if (page > 1) url.addPathSegment("page").addPathSegment(page.toString())
        val document = client.get(url.addPathSegment("").build(), Fetch.DOCUMENT).asJsoup()

        val mangas = document.select("div.manga-card, div.manga-item-grid > div.uk-panel").map { card ->
            SManga.create().apply {
                val link = card.selectFirst("h2 a, h3 a")!!
                setUrlWithoutDomain(link.absUrl("href"))
                title = link.text()
                thumbnail_url = card.selectFirst("img")?.imgAttr()
            }
        }
        val hasNextPage = document.selectFirst("ul.uk-pagination li:not(.uk-disabled) a:has([uk-pagination-next])") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] != mangaUrlDirectory) return null

        val manga = SManga.create().apply {
            this.url = "/$mangaUrlDirectory/${segments[1]}/"
        }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = getMangaUrl(manga)
        val document = client.get(mangaUrl, Fetch.DOCUMENT).asJsoup()

        manga.apply {
            title = document.selectFirst("h1")!!.text()
            description = buildString {
                document.selectFirst("div#manga-description")?.let { element ->
                    element.select("p, h1, h2, h3, h4, h5, h6, li").ifEmpty { listOf(element) }.mapNotNull { it.textOrNull() }.joinTo(this, "\n\n")
                }
                document.selectFirst("span#comic-othername")?.textOrNull()?.let { append("\n\n$altTitleLabel: $it") }
            }.trim().ifEmpty { null }
            author = document.infoValue(authorLabel)
            artist = document.infoValue(artistLabel)
            genre = document.select("div#genre-tags a:not(.uk-disabled)").joinToString { it.text() }.ifEmpty { null }
            status = parseStatus(document)
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attrOrNull("content")
                ?: document.selectFirst("a.story-cover img")?.imgAttr()
        }

        val chapterList = if (fetchChapters) getChapterList(document, mangaUrl) else chapters
        return SMangaUpdate(manga, chapterList)
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select("div.manga-item-slider")
        .map { card ->
            SManga.create().apply {
                setUrlWithoutDomain(card.selectFirst("a[href]")!!.absUrl("href"))
                title = card.selectFirst("h3")!!.text()
                thumbnail_url = card.selectFirst("img")?.imgAttr()
            }
        }

    private fun Document.infoValue(label: String): String? = selectFirst("div.manga-info-details")
        ?.textNodes()
        ?.firstOrNull { it.text().trim() == "$label:" }
        ?.nextElementSibling()
        ?.textOrNull()

    // Ribbon class key, or the label on sites without a ribbon
    private fun parseStatus(document: Document): Int {
        val key = document.selectFirst("div.story-cover-wrap span.manga-status-ribbon")
            ?.classNames()?.firstOrNull { it.startsWith("ribbon-") }?.removePrefix("ribbon-")
            ?: document.selectFirst("#manga-status")?.text()?.lowercase()?.replace(' ', '_')
        return when (key) {
            "ongoing", "caught_up" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "season_end", "source_hiatus" -> SManga.ON_HIATUS
            "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    private suspend fun getChapterList(document: Document, mangaUrl: String): List<SChapter> {
        val mangaId = POST_ID_REGEX.find(document.body().className())!!.groupValues[1]
        val mangaPath = mangaUrl.toHttpUrl().encodedPath.trimEnd('/')
        val hideLocked = preferences.getBoolean(PREF_HIDE_LOCKED_KEY, PREF_HIDE_LOCKED_DEFAULT)

        val firstPage = getChapterPage(mangaId, 1)
        val otherPages = coroutineScope {
            (2..firstPage.totalPages).map { async { getChapterPage(mangaId, it) } }.awaitAll()
        }

        return (listOf(firstPage) + otherPages).flatMap { it.items }.mapNotNull { chapter ->
            val isLocked = chapter.lockType != "none" && !chapter.isPurchased
            if (hideLocked && isLocked) return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain("$mangaPath/${chapter.slug.trim('/')}/")
                chapter_number = chapter.number
                val title = chapter.title?.trim().orEmpty()
                name = buildString {
                    if (isLocked) append("🔒 ")
                    val lowercaseTitle = title.lowercase()
                    if (lowercaseTitle.startsWith("bölüm") || lowercaseTitle.startsWith("chapter")) {
                        append(title)
                    } else {
                        append("$chapterPrefix ${chapter.number.toString().removeSuffix(".0")}")
                        if (title.isNotEmpty()) append(" - $title")
                    }
                }
                date_upload = chapterDateFormat.tryParseDateTime(chapter.createdAt, siteZone)
            }
        }
    }

    private suspend fun getChapterPage(mangaId: String, page: Int): ChapterListDto {
        val url = "$baseUrl/wp-json/initmanga/v1/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", mangaId)
            .addQueryParameter("per_page", "50")
            .addQueryParameter("paged", page.toString())
            .build()
        return client.get(url, Fetch.XHR).parseAs()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl, Fetch.DOCUMENT).asJsoup()
        if (document.selectFirst("div.lock-card") != null) return emptyList()

        val scripts = document.scriptContents()
        document.selectFirst("div.imip-protected-gallery")?.let { return getProtectedPages(it, scripts, chapterUrl) }

        val payload = scripts.firstOrNull { "\"ciphertext\"" in it }?.objectLiteral()?.parseAs<EncryptedPayloadDto>()
            ?: return document.select("div#chapter-content > img").toPages()
        val initMangaData = scripts.first { "\"decryption_key\"" in it }.objectLiteral().parseAs<InitMangaDataDto>()
        val key = if (payload.salt != null) {
            passphraseKey(initMangaData.decryptionKey, payload.salt)
        } else {
            fetchChapterKey(payload, initMangaData.nonce, chapterUrl)
        }
        val content = Base64.decode(payload.ciphertext, Base64.DEFAULT)
            .aesDecrypt(aesCbc(key, payload.iv.decodeHex()))
            .decodeToString()

        return Jsoup.parseBodyFragment(content, baseUrl).select("img, canvas[data-enc]").toPages()
    }

    private suspend fun getProtectedPages(gallery: Element, scripts: List<String>, chapterUrl: String): List<Page> {
        val vault = scripts.first { "admin-ajax" in it && "\"nonce\"" in it }.objectLiteral().parseAs<ImageVaultDto>()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("action", "imip_gate_unlock")
            .addFormDataPart("cid", gallery.attr("data-cid"))
            .addFormDataPart("nonce", vault.nonce)
            .build()
        val key = client.post(vault.url, Fetch.XHR.from(chapterUrl), body).parseAs<ImageKeyResponseDto>().data.k

        return gallery.select("div.imip-page[data-bin]").mapIndexed { i, element ->
            val imageUrl = element.absUrl("data-bin").toHttpUrl().newBuilder().fragment("$IMAGE_KEY_FRAGMENT$key").build().toString()
            Page(i, imageUrl = imageUrl)
        }
    }

    private fun decryptImage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = request.url.fragment?.takeIf { it.startsWith(IMAGE_KEY_FRAGMENT) }?.removePrefix(IMAGE_KEY_FRAGMENT)
            ?: return chain.proceed(request)
        val response = chain.proceed(request)
        if (!response.isSuccessful) return response

        // The real image header sits at the end of the file, so the whole body is needed
        val data = response.body.use { body -> Buffer().apply { writeAll(body.source()) } }
        val image = ImageProtector.decrypt(data, key) ?: throw IOException("Failed to decrypt image")
        return response.newBuilder().body(image.asResponseBody(response.body.contentType(), image.size)).build()
    }

    // Reader data is in inline scripts or base64 data: scripts
    private fun Document.scriptContents(): List<String> = select("script").map { script ->
        script.attr("src").substringAfter("base64,", "").takeIf { it.isNotEmpty() }
            ?.let { Base64.decode(it, Base64.DEFAULT).decodeToString() }
            ?: script.data()
    }

    private fun String.objectLiteral(): String = substring(indexOf('{'), lastIndexOf('}') + 1)

    private fun passphraseKey(decryptionKey: String, salt: String): ByteArray {
        val passphrase = Base64.decode(decryptionKey, Base64.DEFAULT).decodeToString()
        val spec = PBEKeySpec(passphrase.toCharArray(), salt.decodeHex(), 999, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
    }

    private suspend fun fetchChapterKey(payload: EncryptedPayloadDto, nonce: String?, chapterUrl: String): ByteArray {
        var response = postChapterKey(payload, nonce, chapterUrl)
        // Site reader resends with the token after its "Load chapter" button, which takes clicks 300 ms after it shows
        val token = response.data?.token
        if (response.code == "imc_challenge_required" && token != null) {
            delay(500.milliseconds)
            response = postChapterKey(payload, nonce, chapterUrl, token)
        }
        val key = response.key ?: throw Exception(
            when (response.code) {
                "imc_rate_limited" -> "Loading too quickly, wait a bit and retry"
                else -> "Open the chapter in WebView" + response.code?.let { " ($it)" }.orEmpty()
            },
        )
        return key.decodeHex()
    }

    private suspend fun postChapterKey(
        payload: EncryptedPayloadDto,
        nonce: String?,
        chapterUrl: String,
        challengeToken: String = "",
    ): ChapterKeyResponseDto {
        val keyHeaders = headers.newBuilder().apply {
            if (!nonce.isNullOrEmpty()) set("X-WP-Nonce", nonce)
        }.build()
        val body = ChapterKeyRequestDto(
            chapterId = payload.cid!!,
            epoch = payload.e!!,
            grant = payload.g!!,
            // Site's headless check report for a clean browser
            verdict = "pass",
            score = 0,
            codes = emptyList(),
            challengeToken = challengeToken,
        ).toJsonRequestBody()
        val response = client.post(
            "$baseUrl/wp-json/initmanga/v1/chapter-key",
            keyHeaders,
            Fetch.XHR.from(chapterUrl),
            body,
            ensureSuccess = false,
        )
        if (response.body.contentType()?.subtype != "json") {
            response.close()
            throw HttpException(response.code)
        }
        val result = response.parseAs<ChapterKeyResponseDto>()
        // Site reader retries without the nonce when it has expired
        if (nonce.isNullOrEmpty() || result.code != "rest_cookie_invalid_nonce") return result
        return postChapterKey(payload, nonce = null, chapterUrl, challengeToken)
    }

    private fun Elements.toPages(): List<Page> = mapNotNull { element ->
        when (element.tagName()) {
            // Site's initMangaResolveImgSrc
            "canvas" -> Base64.decode(element.attr("data-enc"), Base64.DEFAULT).reversedArray().decodeToString()
            else -> element.imgAttr()
        }
    }.mapIndexed { i, imageUrl -> Page(i, imageUrl = imageUrl) }

    private fun Element.imgAttr(): String? = sequenceOf("data-original-src", "data-src", "src")
        .map { absUrl(it) }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("data:") }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment(mangaUrlDirectory).addPathSegment("").build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return document.select("ul.uk-list.uk-text-small a")
            .map { GenreData(it.ownText(), it.absUrl("href").toHttpUrl().encodedPath) }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreData>>().orEmpty()
        if (genres.isEmpty()) return FilterList()
        val options = listOf(allGenresLabel to null) + genres.map { it.name to it.path }
        return FilterList(GenreFilter(genreFilterName, options))
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED_KEY
            title = "Hide locked chapters"
            summary = "Hide chapters that need coins or a login to read"
            setDefaultValue(PREF_HIDE_LOCKED_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_HIDE_LOCKED_KEY = "pref_hide_locked_chapters"
        private const val PREF_HIDE_LOCKED_DEFAULT = false
        private const val SEARCH_PAGE_SIZE = 10
        private const val IMAGE_KEY_FRAGMENT = "imip="
        private val POST_ID_REGEX = Regex("""postid-(\d+)""")
        private val chapterDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
    }
}
