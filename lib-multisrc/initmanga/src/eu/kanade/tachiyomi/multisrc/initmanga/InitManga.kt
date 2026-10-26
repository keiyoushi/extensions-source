package eu.kanade.tachiyomi.multisrc.initmanga

import android.content.SharedPreferences
import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.asJsoup
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
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

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

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(baseUrl.toHttpUrl().newBuilder().addPathSegment(popularUrlSlug), page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList(baseUrl.toHttpUrl().newBuilder().addPathSegment(latestUrlSlug), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/wp-json/initlise/v1/search".toHttpUrl().newBuilder()
                .addQueryParameter("term", query)
                .addQueryParameter("page", page.toString())
                .build()
            val results = client.get(url).parseAs<List<SearchDto>>()
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
        val document = client.get(url.addPathSegment("").build()).asJsoup()

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
        val document = client.get(mangaUrl).asJsoup()

        manga.apply {
            title = document.selectFirst("h1")!!.text()
            description = buildString {
                document.selectFirst("div#manga-description")?.text()?.let(::append)
                document.selectFirst("span#comic-othername")?.textOrNull()?.let { append("\n\nAlternatif Başlık: $it") }
            }.trim().ifEmpty { null }
            author = document.infoValue(authorLabel)
            artist = document.infoValue(artistLabel)
            genre = document.select("div#genre-tags a:not(.uk-disabled)").joinToString { it.text() }.ifEmpty { null }
            status = parseStatus(document.selectFirst("div.story-cover-wrap span.manga-status-ribbon"))
            thumbnail_url = document.selectFirst("a.story-cover img")?.imgAttr()
        }

        val chapterList = if (fetchChapters) getChapterList(document, mangaUrl) else chapters
        return SMangaUpdate(manga, chapterList)
    }

    private fun Document.infoValue(label: String): String? = selectFirst("div.manga-info-details")
        ?.textNodes()
        ?.firstOrNull { it.text().trim() == "$label:" }
        ?.nextElementSibling()
        ?.textOrNull()

    private fun parseStatus(ribbon: Element?): Int = when (ribbon?.classNames()?.firstOrNull { it.startsWith("ribbon-") }) {
        "ribbon-ongoing", "ribbon-caught_up" -> SManga.ONGOING
        "ribbon-completed" -> SManga.COMPLETED
        "ribbon-season_end", "ribbon-source_hiatus" -> SManga.ON_HIATUS
        "ribbon-dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
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
                date_upload = chapterDateFormat.tryParseDateTime(chapter.createdAt, istanbulZone)
            }
        }
    }

    private suspend fun getChapterPage(mangaId: String, page: Int): ChapterListDto {
        val url = "$baseUrl/wp-json/initmanga/v1/chapters".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", mangaId)
            .addQueryParameter("per_page", "50")
            .addQueryParameter("paged", page.toString())
            .build()
        return client.get(url).parseAs()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val document = client.get(chapterUrl).asJsoup()
        if (document.selectFirst("div.lock-card") != null) return emptyList()

        val scripts = document.scriptContents()
        val payload = scripts.firstNotNullOfOrNull { ENCRYPTED_CHAPTER_REGEX.find(it) }
            ?.groupValues?.get(1)?.parseAs<EncryptedPayloadDto>()
            ?: return document.select("div#chapter-content > img").toPages()
        val initMangaData = scripts.firstNotNullOf { INIT_MANGA_DATA_REGEX.find(it) }
            .groupValues[1].parseAs<InitMangaDataDto>()

        val key = if (payload.salt != null) {
            passphraseKey(initMangaData.decryptionKey!!, payload.salt)
        } else {
            fetchChapterKey(payload, initMangaData, chapterUrl)
        }
        val content = Base64.decode(payload.ciphertext, Base64.DEFAULT)
            .aesDecrypt(aesCbc(key, payload.iv.decodeHex()))
            .decodeToString()

        return Jsoup.parseBodyFragment(content, baseUrl).select("img, canvas[data-enc]").toPages()
    }

    // Reader data is in inline scripts or base64 data: scripts
    private fun Document.scriptContents(): List<String> = select("script").map { script ->
        script.attr("src").substringAfter("base64,", "").takeIf { it.isNotEmpty() }
            ?.let { Base64.decode(it, Base64.DEFAULT).decodeToString() }
            ?: script.data()
    }

    private fun passphraseKey(decryptionKey: String, salt: String): ByteArray {
        val passphrase = Base64.decode(decryptionKey, Base64.DEFAULT).decodeToString()
        val spec = PBEKeySpec(passphrase.toCharArray(), salt.decodeHex(), 999, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512").generateSecret(spec).encoded
    }

    private suspend fun fetchChapterKey(payload: EncryptedPayloadDto, initMangaData: InitMangaDataDto, chapterUrl: String): ByteArray {
        val body = ChapterKeyRequestDto(payload.cid!!, payload.e!!, payload.g!!).toJsonRequestBody()
        val keyUrl = "${initMangaData.restUrl}/chapter-key"
        fun keyHeaders(nonce: String?) = headers.newBuilder()
            .set("Referer", chapterUrl)
            .apply { if (nonce != null) set("X-WP-Nonce", nonce) }
            .build()

        // The endpoint also answers without a nonce
        val nonce = initMangaData.nonce?.takeIf { it.isNotEmpty() }
        if (nonce != null) {
            client.post(keyUrl, keyHeaders(nonce), body, ensureSuccess = false).use { response ->
                if (response.isSuccessful) return response.parseAs<ChapterKeyResponseDto>().key.decodeHex()
            }
        }
        return client.post(keyUrl, keyHeaders(null), body).parseAs<ChapterKeyResponseDto>().key.decodeHex()
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
        val document = client.get(url).asJsoup()
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
        private val ENCRYPTED_CHAPTER_REGEX = Regex("""InitMangaEncryptedChapter\s*=\s*(\{.*?\})\s*(?:;|$)""", RegexOption.DOT_MATCHES_ALL)
        private val INIT_MANGA_DATA_REGEX = Regex("""InitMangaData\s*=\s*(\{.*?\})\s*(?:;|$)""", RegexOption.DOT_MATCHES_ALL)
        private val POST_ID_REGEX = Regex("""postid-(\d+)""")
        private val chapterDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
        private val istanbulZone = ZoneId.of("Europe/Istanbul")
    }
}
