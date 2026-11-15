package eu.kanade.tachiyomi.extension.all.dlsite

import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.XorInterceptor
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.decodeHex
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getArray
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import keiyoushi.zip.Entry
import keiyoushi.zip.zipDirectory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.nio.charset.Charset
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Source
abstract class DLsite : KeiSource() {

    private val language get() = LANGUAGES.getValue(lang)

    private val sessionMutex = Mutex()
    private var viewerSession: ViewerSession? = null
    private var playSession: PlaySession? = null

    // The account's works, kept a few minutes so a library update doesn't ask once per work
    private var owned: OwnedWorks? = null

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrl.toHttpUrl().host || it.host == PLAY_HOST }
        .addInterceptor(ArchiveInterceptor(setOf(TRIAL_HOST, DOWNLOAD_HOST)))
        .addInterceptor(PlayInterceptor(PLAY_CDN_HOST))
        // Book sample pages: XOR with the work's key, carried in the fragment
        .addInterceptor(
            XorInterceptor(SAMPLE_MEDIA_TYPE) { request ->
                request.url.fragment?.takeIf { request.url.host == PLAY_CDN_HOST && "viewer" in request.url.pathSegments }?.decodeHex()
            },
        )

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList())

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", SortFilter.LATEST)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val section = filters.firstInstanceOrNull<SectionFilter>()?.value ?: "maniax"
        if (section == LIBRARY_SECTION) return library(query)
        val types = filters.firstInstanceOrNull<TypeFilter>()?.selected.orEmpty().ifEmpty { listOf("comic", "illust") }
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.selected.orEmpty()
        val url = "$baseUrl/$section/fsr/ajax/=".toHttpUrl().newBuilder().apply {
            fun param(name: String, value: String) = addPathSegment(name).addPathSegment(value)
            param("language", "jp")
            // Like the site, spaces are written "+" in the keyword: it answers "%20" with a 403
            if (query.isNotBlank()) addPathSegment("keyword").addEncodedPathSegment(URLEncoder.encode(query.trim(), "UTF-8"))
            types.forEachIndexed { index, type -> param("work_type_category[$index]", type) }
            if (language.options.size > 1) param("options_and_or", "or")
            language.options.forEachIndexed { index, option -> param("options[$index]", option) }
            if (filters.firstInstanceOrNull<NoAiFilter>()?.state == true) {
                AI_OPTIONS.forEachIndexed { index, option -> param("options_not[$index]", option) }
            }
            if (genres.isNotEmpty()) param("genre_and_or", "and")
            genres.forEachIndexed { index, genre -> param("genre[$index]", genre) }
            param("order[0]", filters.firstInstanceOrNull<SortFilter>()?.value ?: "trend")
            param("per_page", PAGE_SIZE.toString())
            param("page", page.toString())
            param("show_type", "1")
            addQueryParameter("locale", language.locale)
        }.build()

        // The results come as HTML, in a table for the Japanese locale and in a grid for the others
        val result = client.get(url, Fetch.XHR).parseAs<SearchDto>()
        val mangas = Jsoup.parseBodyFragment(result.searchResult, baseUrl).select("[data-list_item_product_id]").map { item ->
            toSManga(
                item.attr("data-list_item_product_id"),
                item.selectFirst(".work_name a[href]")!!,
                item.selectFirst("thumb-with-ng-filter-block")?.attr(":thumb-candidates"),
            ).apply {
                author = item.select(".author a").joinToString { it.text() }.ifEmpty { item.selectFirst(".maker_name a")?.textOrNull() }
            }
        }
        return MangasPage(mangas, result.pageInfo.lastIndice < result.pageInfo.count)
    }

    // The works the user's DLsite account owns; DLsite Play lists them all at once
    private suspend fun library(query: String): MangasPage {
        val worknos = ownedWorknos()
        val mangas = libraryWorks(worknos)
            .filter { it.workType in READABLE_TYPES && it.title(language.locale).contains(query.trim(), ignoreCase = true) }
            .map { it.toSManga(language.locale) }
        return MangasPage(mangas, false)
    }

    private val isLoggedIn get() = client.cookieJar.loadForRequest(baseUrl.toHttpUrl()).any { it.name == LOGIN_COOKIE }

    // The works the logged-in account owns
    private suspend fun ownedWorknos(): List<String> {
        if (!isLoggedIn) throw Exception(LOGIN_MESSAGE)
        owned?.takeIf { Clock.System.now() - it.fetchedAt < OWNED_CACHE }?.let { return it.worknos }
        val worknos = sales() ?: authorize().let { sales() } ?: throw Exception(LOGIN_MESSAGE)
        owned = OwnedWorks(worknos, Clock.System.now())
        return worknos
    }

    private class OwnedWorks(val worknos: List<String>, val fetchedAt: Instant)

    // Null while DLsite Play has no session for the account
    private suspend fun sales(): List<String>? = client.get("$PLAY_URL/api/v4/content/sales?last=0", Fetch.XHR.from(PLAY_URL), CacheControl.FORCE_NETWORK, ensureSuccess = false).use { response ->
        when {
            response.code == 401 -> null
            !response.isSuccessful -> throw HttpException(response.code)
            else -> response.parseAs<List<SaleDto>>().map { it.workno }
        }
    }

    // DLsite Play's session, from the store login; an empty array means the store login wasn't accepted
    private suspend fun authorize() {
        val account = client.get("$PLAY_URL/api/authorize", Fetch.XHR.from(PLAY_URL), CacheControl.FORCE_NETWORK).parseAs<JsonElement>()
        if (account is JsonArray) throw Exception(LOGIN_MESSAGE)
    }

    private suspend fun libraryWorks(worknos: List<String>): List<LibraryWorkDto> = worknos.chunked(LIBRARY_BATCH).flatMap { batch ->
        client.post("$PLAY_URL/api/v4/content/works", Fetch.XHR.from(PLAY_URL), WorknosDto(batch).toJsonRequestBody()).parseAs<LibraryDto>().works
    }

    // A work in the site's lists, with its section in memo for the work page
    private fun toSManga(workno: String, link: Element, coverCandidates: String?) = SManga.create().apply {
        url = workno
        title = link.attrOrNull("title") ?: link.text()
        thumbnail_url = coverCandidates?.let(::coverUrl)
        memo = buildJsonObject { put("site", link.absUrl("href").toHttpUrl().pathSegments.first()) }
    }

    // The list's thumbnail candidates, a WebP and a JPEG, can be resized copies of the main image: it has the same path
    // under /modpub/, without the size
    private fun coverUrl(candidates: String): String? {
        val urls = CANDIDATE_REGEX.findAll(candidates).map { it.groupValues[1] }.toList()
        val url = urls.firstOrNull { it.endsWith(".jpg") } ?: urls.firstOrNull() ?: return null
        return baseUrl.toHttpUrl().resolve(url.replace("/resize/", "/modpub/").replace(SIZE_SUFFIX_REGEX, ""))?.toString()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host && url.host != PLAY_HOST) return null
        val workno = PRODUCT_PATH_REGEX.matchEntire(url.encodedPath)?.groupValues?.get(1) ?: return null
        val product = fetchProduct(workno)?.takeIf { it.workType in READABLE_TYPES } ?: return null
        return product.toSManga(baseUrl) { workUrl(it, product.siteId) }
    }

    override fun getMangaUrl(manga: SManga) = workUrl(manga.url, manga.memo["site"]?.stringOrNull)

    private fun workUrl(workno: String, site: String?) = "$baseUrl/${site ?: "maniax"}/work/=/product_id/$workno.html".toHttpUrl().newBuilder()
        .addQueryParameter("locale", language.locale)
        .build()
        .toString()

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val page = if (fetchDetails) async { client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup() } else null
        val product = fetchProduct(manga.url) ?: throw Exception("This work isn't on sale anymore")
        val editionUrl = { workno: String -> workUrl(workno, product.siteId) }
        val updatedChapters = if (fetchChapters) {
            val owned = async { ownedChapters(product) }
            val trialFiles = product.trialFiles
            val trials = trialFiles.mapIndexed { index, trial ->
                async { trialChapters(product, trial, if (trialFiles.size > 1) "Trial ${index + 1}" else "Trial") }
            }
            owned.await() + listOfNotNull(product.toFreeChapter()) + trials.awaitAll().flatten() + listOfNotNull(product.toSampleChapter(baseUrl))
        } else {
            chapters
        }
        val details = if (page != null) {
            product.toSManga(baseUrl, editionUrl).apply { description = product.description(page.await().selectFirst("[itemprop=description]"), editionUrl) }
        } else {
            manga
        }
        SMangaUpdate(details, updatedChapters)
    }

    private suspend fun fetchProduct(workno: String): ProductDto? {
        val url = "$baseUrl/maniax/api/=/product.json".toHttpUrl().newBuilder()
            .addQueryParameter("workno", workno)
            .addQueryParameter("locale", language.locale)
            .build()
        return client.get(url, Fetch.XHR).parseAs<List<ProductDto>>().firstOrNull()
    }

    // The whole work, when the account owns it: the purchase archive's originals, or DLsite Play's pages for works it only
    // serves there and downloads that aren't a single archive of images
    private suspend fun ownedChapters(product: ProductDto): List<SChapter> {
        if (!isLoggedIn || product.workno !in ownedWorknos()) return emptyList()
        val work = libraryWorks(listOf(product.workno)).single()
        // DLsite Play's DRM viewer, not readable here yet
        if (work.playEncodeType?.startsWith("link_u") == true) return emptyList()
        val archive = if (work.downloadable) downloadArchive(product.workno, product.siteId) else null
        val archiveFolders = archive?.let { archiveFolders(it.url, it.referer).keys }.orEmpty()
        val (type, folders) = if (archiveFolders.isNotEmpty()) ARCHIVE to archiveFolders else PLAY to playFolders(product.workno).keys
        return folderChapters(folders, "/owned/${product.workno}", "Full", product) { put("type", type) }
    }

    private class DownloadArchive(val url: String, val referer: String)

    // The archive the store's download button redirects to, which wants the download page as Referer; null when the page
    // offers something else
    private suspend fun downloadArchive(workno: String, site: String): DownloadArchive? {
        val page = "$baseUrl/$site/download/=/product_id/$workno.html"
        val response = client.newBuilder().followRedirects(false).build()
            .get(page, Fetch.DOCUMENT.from(workUrl(workno, site)), CacheControl.FORCE_NETWORK, ensureSuccess = false)
        response.close()
        val archive = response.header("Location")?.takeIf { response.isRedirect } ?: return null
        return DownloadArchive(archive, page)
    }

    private suspend fun playFolders(workno: String): Map<String, List<OptimizedDto>> = client.get("${playSession(workno).sign.url}ziptree.json", Fetch.XHR.from(PLAY_URL))
        .parseAs<ZipTreeDto>()
        .folders()

    // Signed CDN cookies for one work's DLsite Play folder, set by the sign request itself
    private suspend fun playSession(workno: String): PlaySession = sessionMutex.withLock {
        val current = playSession
        if (current != null && current.workno == workno && current.sign.expiresAt > Clock.System.now() + SESSION_MARGIN) {
            return@withLock current
        }
        val url = "https://$PLAY_CDN_HOST/api/v3/download/sign/cookie".toHttpUrl().newBuilder().addQueryParameter("workno", workno).build()
        val sign = client.get(url, Fetch.XHR.from("$PLAY_URL/work/$workno"), CacheControl.FORCE_NETWORK).parseAs<PlaySignDto>()
        PlaySession(workno, sign).also { playSession = it }
    }

    private class PlaySession(val workno: String, val sign: PlaySignDto)

    private suspend fun trialChapters(product: ProductDto, trial: TrialFileDto, label: String) = folderChapters(archiveFolders(trial.zipUrl).keys, "/${trial.relativeUrl}", label, product) {
        put("type", TRIAL)
        put("zip", trial.zipUrl)
    }

    // One chapter per folder of images: some archives hold the pages once per language
    private fun folderChapters(folders: Set<String>, url: String, label: String, product: ProductDto, memo: JsonObjectBuilder.() -> Unit) = folders.map { folder ->
        SChapter.create().apply {
            this.url = if (folders.size > 1) "$url/$folder" else url
            name = if (folders.size > 1) "$label - ${folder.substringAfterLast('/').ifEmpty { "/" }}" else label
            date_upload = product.date
            this.memo = buildJsonObject {
                memo()
                put("folder", folder)
                put("workno", product.workno)
                put("site", product.siteId)
            }
        }
    }

    // The image folders in archive order, each in page order. Archives made by Japanese tools store names in Shift-JIS
    private suspend fun archiveFolders(zipUrl: String, referer: String? = null): Map<String, List<Entry>> = client.zipDirectory(
        zipUrl,
        if (referer == null) headers else headers.newBuilder().set("Referer", referer).build(),
        charset = SHIFT_JIS,
    ).entries
        .filter { IMAGE_REGEX.containsMatchIn(it.name) }
        .sortedBy { it.localHeaderOffset }
        .groupBy { it.name.substringBeforeLast('/', "") }
        .mapValues { (_, entries) -> entries.sortedBy { entry -> NUMBER_REGEX.replace(entry.name.lowercase()) { it.value.padStart(10, '0') } } }

    override fun getChapterUrl(chapter: SChapter): String = when (chapter.memo.getString("type")) {
        ARCHIVE, PLAY -> "$PLAY_URL/work/${chapter.memo.getString("workno")}"
        TRIAL -> workUrl(chapter.memo.getString("workno"), chapter.memo.getString("site"))
        else -> "$PLAY_URL${chapter.url}"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val workno = chapter.memo["workno"]?.stringOrNull
        val folder = chapter.memo["folder"]?.stringOrNull
        return when (chapter.memo.getString("type")) {
            ARCHIVE -> {
                val archive = downloadArchive(workno!!, chapter.memo.getString("site")) ?: throw Exception(REFRESH_MESSAGE)
                archivePages(archive.url, archiveFolders(archive.url, archive.referer)[folder] ?: throw Exception(REFRESH_MESSAGE), archive.referer)
            }
            PLAY -> (playFolders(workno!!)[folder] ?: throw Exception(REFRESH_MESSAGE)).mapIndexed { index, page ->
                // Pages the site marks as scrambled carry their size for the unscrambling
                val size = if (page.crypt) ":${page.width}x${page.height}" else ""
                Page(index, url = "$PLAY_URL/work/$workno#${page.name}$size")
            }
            TRIAL -> {
                val zipUrl = chapter.memo.getString("zip")
                archivePages(zipUrl, archiveFolders(zipUrl)[folder] ?: throw Exception(REFRESH_MESSAGE))
            }
            FREE, BOOK_SAMPLE -> viewerPages(chapter.memo.getString("type"), workno!!)
            else -> chapter.memo.getArray("images").mapIndexed { index, image -> Page(index, imageUrl = image.string) }
        }
    }

    private fun archivePages(zipUrl: String, entries: List<Entry>, referer: String? = null) = entries.mapIndexed { index, entry ->
        val data = ArchiveEntry(entry.name, entry.localHeaderOffset, entry.compressedSize, entry.method, entry.localHeaderAllowance, referer).toJsonString()
        Page(index, imageUrl = "$zipUrl#$data")
    }

    // A book sample or a free-to-read work, in DLsite Play's viewer: pages XORed with a key per session
    private suspend fun viewerPages(kind: String, workno: String): List<Page> {
        val session = viewerSession(kind, workno)
        return client.get(session.url("viewer-meta.json"), Fetch.XHR.from(viewerPageUrl(kind, workno)))
            .parseAs<ViewerMetaDto>()
            .pages
            .filter { it.type == "image" }
            .mapIndexed { index, page -> Page(index, url = "${viewerPageUrl(kind, workno)}#${page.src}") }
    }

    // Signed again when the session the page list was made with has expired
    override suspend fun getImageUrl(page: Page): String {
        val url = page.url.toHttpUrl()
        val workno = url.pathSegments.last()
        if (url.pathSegments.first() == "work") {
            val name = url.fragment!!.substringBefore(':')
            val size = url.fragment!!.substringAfter(':', "")
            return "${playSession(workno).sign.url}optimized/$name" + if (size.isEmpty()) "" else "#$size"
        }
        val session = viewerSession(if (url.pathSegments.first() == FREE) FREE else BOOK_SAMPLE, workno)
        return session.url(url.fragment!!).newBuilder().fragment(session.key).build().toString()
    }

    private suspend fun viewerSession(kind: String, workno: String): ViewerSession = sessionMutex.withLock {
        val current = viewerSession
        if (current != null && current.kind == kind && current.workno == workno && current.token.expiresAt > Clock.System.now() + SESSION_MARGIN) {
            return@withLock current
        }
        val from = Fetch.XHR.from(viewerPageUrl(kind, workno))
        val session = if (kind == BOOK_SAMPLE) {
            val token = client.post("$PLAY_URL/api/sample/viewer/token/$workno", from, RequestBody.EMPTY).parseAs<ViewerTokenDto>()
            ViewerSession(kind, workno, token, client.get(token.url("decryption.key"), from).body.string().trim(), "")
        } else {
            // The work's file tree gives the revision and the file to ask a key for, wrapped for a key pair of ours
            val content = client.get("$PLAY_URL/api/free/token/$workno", from).parseAs<ViewerTokenDto>()
            val tree = client.get(content.url("ziptree.json"), from).parseAs<FreeZipTreeDto>()
            val (file, playFile) = tree.playfile.entries.single()
            val keys = ViewerKeys()
            val request = FreeViewerTokenRequest(playFile.type, tree.revision, keys.publicKey).toJsonRequestBody()
            val token = client.post("$PLAY_URL/api/free/viewer/token/$workno", from, request).parseAs<ViewerTokenDto>()
            ViewerSession(kind, workno, token, keys.unwrap(token.key!!), file)
        }
        session.also { viewerSession = it }
    }

    private class ViewerSession(val kind: String, val workno: String, val token: ViewerTokenDto, val key: String, private val folder: String) {
        fun url(file: String) = token.url(if (folder.isEmpty()) file else "$folder/$file")
    }

    private fun viewerPageUrl(kind: String, workno: String) = if (kind == FREE) "$PLAY_URL/free/$workno" else "$PLAY_URL/viewer/sample/$workno"

    override val supportsRelatedMangas = true

    // The work page's same-series, circle and label lists
    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = coroutineScope {
        client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
            .select(RELATED_LISTS.joinToString { "[data-href*=/type/$it/]" })
            .map { list -> async { client.get(list.absUrl("data-href"), Fetch.XHR).asJsoup() } }
            .awaitAll()
            .flatMap { it.select("li[data-prod]") }
            .filter { it.selectFirst("[data-work_type]")?.attr("data-work_type") in READABLE_TYPES }
            .map { item ->
                toSManga(
                    item.attr("data-prod"),
                    item.selectFirst("a[href*=/product_id/]:not(.work_thumb)")!!,
                    item.selectFirst("img-with-fallback")?.attr(":candidates"),
                )
            }
            .distinctBy { it.url }
            .filter { it.url != manga.url }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = "$baseUrl/maniax/genre/list".toHttpUrl().newBuilder().addQueryParameter("locale", language.locale).build()
        return client.get(url, Fetch.DOCUMENT).asJsoup()
            .select("a[href*=/works/genre/=/genre/]")
            .map { GenreOption(it.text().replace(COUNT_REGEX, ""), it.absUrl("href").toHttpUrl().pathSegments.last()) }
            .distinctBy { it.id }
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreOption>>().orEmpty()
        return FilterList(
            buildList {
                add(SectionFilter())
                add(TypeFilter())
                add(SortFilter())
                add(NoAiFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
            },
        )
    }

    private class Language(val locale: String, val options: List<String>)

    companion object {
        private const val PAGE_SIZE = 30
        private const val PLAY_HOST = "play.dlsite.com"
        private const val TRIAL_HOST = "trial.dlsite.com"
        private const val PLAY_URL = "https://$PLAY_HOST"
        private const val PLAY_CDN_HOST = "play.dl.dlsite.com"
        private const val DOWNLOAD_HOST = "download.dlsite.com"
        const val LIBRARY_SECTION = "library"

        // Chapter kinds, kept in their memo
        const val TRIAL = "trial"
        const val FREE = "free"
        const val BOOK_SAMPLE = "book"
        const val IMAGE_SAMPLE = "images"
        private const val ARCHIVE = "archive"
        private const val PLAY = "play"
        private const val LIBRARY_BATCH = 1000
        private const val LOGIN_COOKIE = "dlloginjp"
        private const val LOGIN_MESSAGE = "Log in to DLsite in WebView to see your library"
        private const val REFRESH_MESSAGE = "Refresh the chapter list"
        private val OWNED_CACHE = 5.minutes
        private val SAMPLE_MEDIA_TYPE = "image/webp".toMediaType()
        private val SESSION_MARGIN = 10.minutes

        // The site's locale for each source, and the language options of the works it lists
        private val LANGUAGES = mapOf(
            // Japanese works, and the ones that need no language
            "ja" to Language("ja_JP", listOf("JPN", "NM")),
            "en" to Language("en_US", listOf("ENG")),
            "zh-Hans" to Language("zh_CN", listOf("CHI_HANS")),
            "zh-Hant" to Language("zh_TW", listOf("CHI_HANT")),
            "ko" to Language("ko_KR", listOf("KO_KR")),
            "es" to Language("es_ES", listOf("SPA")),
            "fr" to Language("fr_FR", listOf("FRE")),
            "id" to Language("id_ID", listOf("IND")),
            "th" to Language("th_TH", listOf("THA")),
            "vi" to Language("vi_VN", listOf("VIE")),
            "pt-BR" to Language("pt_BR", listOf("POR")),
        )

        // Manga, CG and illustrations, webtoons
        private val READABLE_TYPES = setOf("MNG", "ICG", "WBT")

        // AI-generated and AI-assisted works
        val AI_OPTIONS = listOf("AIG", "AIP")

        private val RELATED_LISTS = listOf("same_series", "maker_works", "label_works")

        private val PRODUCT_PATH_REGEX = Regex("""/(?:[a-z-]+/(?:work|announce)/=/product_id/|(?:viewer/)?sample/)([A-Z]{2}\d+)(?:\.html)?""")
        private val CANDIDATE_REGEX = Regex("""'([^']+)'""")
        private val SIZE_SUFFIX_REGEX = Regex("""_\d+x\d+(?=\.\w+$)""")
        private val COUNT_REGEX = Regex("""\s*\([\d,.]+\)$""")
        private val IMAGE_REGEX = Regex("""\.(?:jpe?g|png|webp|gif)$""", RegexOption.IGNORE_CASE)
        private val NUMBER_REGEX = Regex("""\d+""")
        private val SHIFT_JIS = Charset.forName("MS932")
    }
}
