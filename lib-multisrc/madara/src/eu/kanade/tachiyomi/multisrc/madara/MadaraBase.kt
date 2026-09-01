package eu.kanade.tachiyomi.multisrc.madara

import android.util.Base64
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val PAGE_SIZE = 25

abstract class MadaraBase : KeiSource() {

    enum class ChapterMode {
        MangaPage,
        AdminAjax,
        MangaAjax,
        MangaAjaxPaginated,
        MangaAjaxQuery,
    }

    protected open val chapterMode get() = ChapterMode.MangaPage
    protected open val mangaSubString get() = "manga"
    protected open val genreDirectory get() = "manga-genre"
    protected open val filterNonMangaItems get() = true
    protected open val sendViewCount get() = true

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "pt-BR", "es"),
        classLoader = this::class.java.classLoader!!,
    )

    protected open val statusFilterOptions = listOf(
        intl["status_filter_completed"] to "end",
        intl["status_filter_ongoing"] to "on-going",
        intl["status_filter_canceled"] to "canceled",
        intl["status_filter_on_hold"] to "on-hold",
    )

    protected open val orderByFilterOptions = listOf(
        intl["order_by_filter_relevance"] to "",
        intl["order_by_filter_latest"] to "latest",
        intl["order_by_filter_az"] to "alphabet",
        intl["order_by_filter_rating"] to "rating",
        intl["order_by_filter_trending"] to "trending",
        intl["order_by_filter_views"] to "views",
        intl["order_by_filter_new"] to "new-manga",
    )

    protected open val genreConditionFilterOptions = listOf(
        intl["genre_condition_filter_or"] to "",
        intl["genre_condition_filter_and"] to "1",
    )

    protected open val adultFilterOptions = listOf(
        intl["adult_content_filter_all"] to "",
        intl["adult_content_filter_none"] to "0",
        intl["adult_content_filter_only"] to "1",
    )

    protected val xhrHeaders: Headers
        get() = headersBuilder().set("X-Requested-With", "XMLHttpRequest").build()

    override val supportsFilterFetching get() = true
    override val supportsRelatedMangas get() = true

    protected open fun archiveSelector() = "div.page-item-detail, .manga__item, .c-tabs-item__content"
    protected open fun searchCardSelector() = ".c-tabs-item__content"
    protected open val archiveUrlSelector = ".post-title a"
    protected open val mangaDetailsSelectorTitle = "div.post-title h3, div.post-title h1, #manga-title > h1"
    protected open val mangaDetailsSelectorAuthor = "div.author-content > a, div.manga-authors > a"
    protected open val mangaDetailsSelectorArtist = "div.artist-content > a"
    protected open val mangaDetailsSelectorStatus = "div.summary-content, div.summary-heading:contains(Status) + div"
    protected open val mangaDetailsSelectorDescription = "div.description-summary div.summary__content, div.summary_content div.post-content_item > h5 + div, div.summary_content div.manga-excerpt"
    protected open val mangaDetailsSelectorThumbnail = "div.summary_image img"
    protected open val mangaDetailsSelectorGenre = "div.genres-content a"
    protected open val mangaDetailsSelectorTag = "div.tags-content a"
    protected open val seriesTypeSelector = ".post-content_item:contains(Type) .summary-content"
    protected open val altNameSelector = ".post-content_item:contains(Alt) .summary-content"
    protected open val updatingRegex = "Updating|Atualizando".toRegex(RegexOption.IGNORE_CASE)
    protected open fun chapterListSelector() = "li.wp-manga-chapter"
    protected open val chapterUrlSelector = "a"
    protected open val chapterDateSelector = "span.chapter-release-date"
    protected open val pageListParseSelector = "div.page-break, li.blocks-gallery-item, .reading-content .text-left:not(:has(.blocks-gallery-item)) img"

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/$mangaSubString/").asJsoup()
        return document.select("div.genres a[href*='/$genreDirectory/']").mapNotNull { element ->
            val href = element.attr("abs:href").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val path = href.toHttpUrl().encodedPath
            val name = element.text().takeIf(String::isNotBlank) ?: return@mapNotNull null
            val slug = path.trimEnd('/').substringAfterLast('/').takeIf(String::isNotEmpty) ?: return@mapNotNull null
            GenreRoute(name, slug, path)
        }.distinctBy(GenreRoute::slug).toGenreJson()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val document = client.get(url).asJsoup()
        val id = document.mangaId() ?: return null
        return parseDetails(document, id, preserveUrl = null).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = memoPath(manga)
        ?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
        ?: manga.url.takeIf { !it.all(Char::isDigit) }?.let { baseUrl.toHttpUrl().resolve(it)?.toString() }
        ?: "$baseUrl/?p=${mangaId(manga) ?: manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val id = mangaId(manga)
        val path = memoPath(manga)
        val endpointCanUseMemo = chapterMode != ChapterMode.MangaPage && id != null && path != null
        if (fetchChapters && endpointCanUseMemo && !fetchDetails) {
            return SMangaUpdate(manga, fetchChapters(path, id))
        }
        if (fetchChapters && endpointCanUseMemo && fetchDetails) {
            return coroutineScope {
                val details = async { getDetailsDocument(manga) }
                val chapterList = async { fetchChapters(path, id) }
                val document = details.await()
                SMangaUpdate(parseDetails(document, id, manga.url), chapterList.await())
            }
        }

        val document = getDetailsDocument(manga)
        val resolvedId = id ?: document.mangaId() ?: error("Missing Madara post ID")
        val resolvedPath = document.location().toHttpUrl().encodedPath
        val updated = parseDetails(document, resolvedId, manga.url)
        val updatedChapters = if (fetchChapters) fetchChapters(resolvedPath, resolvedId, document) else chapters
        return SMangaUpdate(updated, updatedChapters)
    }

    private suspend fun getDetailsDocument(manga: SManga): Document = getDetailsResponse(manga).asJsoup().also(::enqueueViewCount)

    private suspend fun getDetailsResponse(manga: SManga): Response {
        var id = mangaId(manga)
        val path = memoPath(manga) ?: manga.url.takeIf { !it.all(Char::isDigit) }
        if (path != null) {
            val requestedUrl = baseUrl.toHttpUrl().resolve(path) ?: error("Invalid manga path")
            val response = client.get(requestedUrl, ensureSuccess = false)
            if (response.isSuccessful && response.request.url == requestedUrl) return response
            val redirectedId = response.use { fallback ->
                if (id == null) fallback.asJsoup().mangaId() else null
            }
            id = id ?: redirectedId
        }
        return client.get("$baseUrl/?p=${id ?: error("Missing Madara post ID")}")
    }

    protected open fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = SManga.create().apply {
        url = preserveUrl?.takeIf { !it.all(Char::isDigit) } ?: id
        title = document.selectFirst(mangaDetailsSelectorTitle)!!.ownText()
        author = document.select(mangaDetailsSelectorAuthor).eachText().filterNot(::isUpdating).joinToString().ifBlank { null }
        artist = document.select(mangaDetailsSelectorArtist).eachText().filterNot(::isUpdating).joinToString().ifBlank { null }
        description = document.selectFirst(mangaDetailsSelectorDescription)?.let { element ->
            element.select("p").takeIf(List<Element>::isNotEmpty)?.joinToString("\n\n") { it.text() } ?: element.text()
        }
        document.selectFirst(altNameSelector)?.ownText()?.takeIf { it.isNotEmpty() && !isUpdating(it) }?.let { alternative ->
            description = listOfNotNull(description, "${intl["alt_names_heading"]} $alternative").joinToString("\n\n")
        }
        thumbnail_url = document.selectFirst(mangaDetailsSelectorThumbnail)?.let { processThumbnail(imageFromElement(it)) }
        status = document.select(mangaDetailsSelectorStatus).lastOrNull()?.text()?.toStatus() ?: SManga.UNKNOWN
        val genres = document.select(mangaDetailsSelectorGenre).mapNotNull { element ->
            val href = element.attr("abs:href").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val path = href.toHttpUrl().encodedPath
            val slug = path.trimEnd('/').substringAfterLast('/').takeIf(String::isNotEmpty) ?: return@mapNotNull null
            GenreRoute(element.text(), slug, path)
        }
        genre = buildList {
            addAll(genres.map(GenreRoute::name))
            addAll(document.select(mangaDetailsSelectorTag).eachText())
            document.selectFirst(seriesTypeSelector)?.text()?.takeIf(String::isNotEmpty)?.let(::add)
        }.distinctBy(String::lowercase).joinToString().ifBlank { null }
        memo = mangaMemo(
            path = document.location().toHttpUrl().encodedPath,
            genres = genres,
            legacyId = id.takeIf { preserveUrl?.all(Char::isDigit) == false },
        )
    }

    protected open suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document? = null): List<SChapter> = when (chapterMode) {
        ChapterMode.MangaPage -> fetchMangaPageChapters(mangaPath, mangaPage)
        ChapterMode.AdminAjax -> fetchAdminAjaxChapters(mangaPath, id)
        ChapterMode.MangaAjax -> fetchMangaAjaxChapters(mangaPath)
        ChapterMode.MangaAjaxPaginated -> fetchPaginatedChapters(mangaPath)
        ChapterMode.MangaAjaxQuery -> fetchQueryChapters(mangaPath)
    }

    private fun fetchMangaPageChapters(mangaPath: String, mangaPage: Document?): List<SChapter> = parseChapterList(mangaPage ?: error("Manga page is required for this chapter mode"), mangaPath)

    private suspend fun fetchAdminAjaxChapters(mangaPath: String, id: String): List<SChapter> {
        val body = FormBody.Builder().add("action", "manga_get_chapters").add("manga", id).build()
        return parseChapterList(client.post("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, body).asJsoup(), mangaPath)
    }

    private suspend fun fetchMangaAjaxChapters(mangaPath: String): List<SChapter> = parseChapterList(client.post(chapterAjaxUrl(mangaPath), xhrHeaders, FormBody.Builder().build()).asJsoup(), mangaPath)

    private suspend fun fetchPaginatedChapters(mangaPath: String): List<SChapter> {
        val result = mutableListOf<SChapter>()
        var lastUrl: String? = null
        var page = 1
        while (true) {
            val response = client.post(
                "${chapterAjaxUrl(mangaPath)}?t=$page",
                xhrHeaders,
                FormBody.Builder().build(),
                ensureSuccess = false,
            )
            if (response.code == 404) {
                response.close()
                return result
            }
            if (!response.isSuccessful) {
                val code = response.code
                response.close()
                throw HttpException(code)
            }
            val chapters = parseChapterList(response.asJsoup(), mangaPath)
            if (chapters.isEmpty() || chapters.last().url == lastUrl) break
            result += chapters
            lastUrl = chapters.last().url
            page++
        }
        return result
    }

    private suspend fun fetchQueryChapters(mangaPath: String): List<SChapter> {
        val slug = mangaPath.trimEnd('/').substringAfterLast('/')
        val body = FormBody.Builder()
            .add("manga-core", slug)
            .add("manga_ajax", "1")
            .add("maction", "get_chapters")
            .build()
        return parseChapterList(client.post("$baseUrl/index.php", xhrHeaders, body).asJsoup(), mangaPath)
    }

    private fun chapterAjaxUrl(mangaPath: String) = "$baseUrl${mangaPath.trimEnd('/')}/ajax/chapters/"

    protected open fun parseArchive(document: Document): List<SManga> = document.select(archiveSelector()).mapNotNull { element ->
        val id = element.attr("data-post-id").takeIf(String::isNotBlank)
            ?: element.selectFirst("[data-post-id]")?.attr("data-post-id")?.takeIf(String::isNotBlank)
            ?: return@mapNotNull null
        archiveManga(element, id)
    }

    protected open fun archiveManga(element: Element, id: String): SManga? {
        val link = element.selectFirst(archiveUrlSelector) ?: return null
        val href = link.attr("abs:href").takeIf(String::isNotBlank) ?: return null
        return SManga.create().apply {
            url = id
            title = link.text()
            thumbnail_url = element.selectFirst("img")?.let { processThumbnail(imageFromElement(it), true) }
            memo = mangaMemo(href.toHttpUrl().encodedPath, emptyList())
        }
    }

    protected open fun parseSearchCards(document: Document): List<SearchCard> = document.select(searchCardSelector()).mapNotNull { element ->
        val link = element.selectFirst(archiveUrlSelector) ?: return@mapNotNull null
        val href = link.attr("abs:href").takeIf(String::isNotBlank) ?: return@mapNotNull null
        SearchCard(link.text(), href.toHttpUrl().encodedPath, element.selectFirst("img")?.let { processThumbnail(imageFromElement(it), true) })
    }

    protected open fun parseChapterList(document: Document, mangaPath: String): List<SChapter> = document.select(chapterListSelector()).mapNotNull { chapterFromElement(it, mangaPath) }

    protected open fun chapterFromElement(element: Element, mangaPath: String): SChapter? {
        val link = element.selectFirst(chapterUrlSelector) ?: return null
        val url = link.attr("abs:href").takeIf(String::isNotBlank) ?: return null
        val slug = url.toHttpUrl().encodedPath.trimEnd('/').substringAfterLast('/').takeIf(String::isNotEmpty) ?: return null
        return SChapter.create().apply {
            this.url = slug
            name = link.text()
            date_upload = parseChapterDate(
                element.selectFirst("img:not(.thumb)")?.attr("alt")?.takeIf(String::isNotBlank)
                    ?: element.selectFirst("span a")?.attr("title")?.takeIf(String::isNotBlank)
                    ?: element.selectFirst(chapterDateSelector)?.text(),
            )
            memo = buildJsonObject { put("mangaPath", mangaPath) }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        if (chapter.url.contains('/')) error("Refresh the chapter list.")
        val mangaPath = chapter.memo["mangaPath"]?.jsonPrimitive?.content
            ?: error("Refresh the chapter list.")
        return "$baseUrl${mangaPath.trimEnd('/')}/${chapter.url}/"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val first = fetchChapterDocument(chapterUrl)
        val document = if (first.selectFirst("#single-pager") != null) {
            client.get(chapterUrl.toHttpUrl().newBuilder().addQueryParameter("style", "list").build()).asJsoup()
        } else {
            first
        }
        enqueueViewCount(document)
        return parsePages(document)
    }

    protected open suspend fun fetchChapterDocument(chapterUrl: String): Document = client.get(chapterUrl).asJsoup()

    private fun enqueueViewCount(document: Document) {
        if (!sendViewCount) return
        val data = document.selectFirst("script#wp-manga-js-extra")
            ?.data()
            ?.substringAfter("var manga = ", "")
            ?.substringBeforeLast(';')
            ?.takeIf(String::isNotBlank)
            ?.runCatching { parseAs<JsonObject>() }
            ?.getOrNull()
            ?: return
        if (data["enable_manga_view"]?.jsonPrimitive?.content != "1") return
        val mangaId = data["manga_id"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank) ?: return
        val chapterSlug = data["chapter_slug"]?.jsonPrimitive?.contentOrNull
        val body = FormBody.Builder()
            .add("action", "manga_views")
            .add("manga", mangaId)
            .apply { chapterSlug?.let { add("chapter", it) } }
            .build()
        val request = Request.Builder()
            .url("$baseUrl/wp-admin/admin-ajax.php")
            .headers(headersBuilder().set("Referer", document.location()).build())
            .post(body)
            .build()
        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) = Unit
                override fun onResponse(call: Call, response: Response) = response.close()
            },
        )
    }

    protected open fun parsePages(document: Document): List<Page> {
        val protector = document.selectFirst("#chapter-protector-data")
        if (protector == null) {
            return document.select(pageListParseSelector).mapIndexedNotNull { index, element ->
                element.selectFirst("img")?.let {
                    Page(index, document.location(), imageFromElement(it) ?: return@mapIndexedNotNull null)
                }
            }
        }
        val script = protector.attr("src").takeIf { it.startsWith("data:text/javascript;base64,") }
            ?.substringAfter(',')?.let { Base64.decode(it, Base64.DEFAULT).toString(Charsets.UTF_8) } ?: protector.html()
        val password = script.substringAfter("wpmangaprotectornonce='").substringBefore("';")
        val encrypted = script.substringAfter("chapter_data='").substringBefore("';").replace("\\/", "/")
        val objectData = encrypted.parseAs<ChapterProtectorData>()
        val raw = decryptChapterData(objectData, password)
        return raw.parseAs<String>().parseAs<List<String>>().mapIndexed { index, image ->
            Page(index, document.location(), image)
        }
    }

    private fun decryptChapterData(data: ChapterProtectorData, password: String): String {
        val salt = data.salt.hexBytes()
        val passwordBytes = password.toByteArray()
        val digest = MessageDigest.getInstance("MD5")
        val keyAndIv = buildList {
            var previous = ByteArray(0)
            while (sumOf(ByteArray::size) < 48) {
                previous = digest.digest(previous + passwordBytes + salt)
                add(previous)
            }
        }.reduce(ByteArray::plus)
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyAndIv.copyOfRange(0, 32), "AES"),
            IvParameterSpec(keyAndIv.copyOfRange(32, 48)),
        )
        return cipher.doFinal(Base64.decode(data.ciphertext, Base64.DEFAULT)).toString(Charsets.UTF_8)
    }

    override fun imageRequest(page: Page) = super.imageRequest(page).newBuilder().header("Referer", page.url).build()

    protected open fun imageFromElement(element: Element): String? = when {
        element.hasAttr("data-src") -> element.attr("abs:data-src")
        element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
        element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
        element.hasAttr("data-manga-src") -> element.attr("abs:data-manga-src")
        element.hasAttr("srcset") -> element.attr("abs:srcset").getSrcSetImage()
        else -> element.attr("abs:src")
    }

    protected open fun processThumbnail(url: String?, fromSearch: Boolean = false): String? = url

    protected fun String.getSrcSetImage(): String? {
        val images = split(',')
            .map { it.trim().split(WHITESPACE_REGEX, limit = 2) }
            .filter { it.isNotEmpty() && URL_REGEX.matches(it[0]) }
        val imagesWithDescriptor = images.mapNotNull { candidate ->
            candidate.getOrNull(1)?.let(IMAGE_DESCRIPTOR_REGEX::matchEntire)?.groupValues?.get(1)?.toFloatOrNull()?.let {
                candidate[0] to it
            }
        }
        return imagesWithDescriptor.maxByOrNull { it.second }?.first
            ?: images.maxOfOrNull { it.first() }
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val id = mangaId(manga) ?: return emptyList()
        val genres = relatedGenres(manga)
        if (genres.isEmpty()) return emptyList()
        return fetchRelatedMangaList(id, genres)
    }

    protected abstract suspend fun fetchRelatedMangaList(id: String, genres: List<GenreRoute>): List<SManga>

    protected fun memoGenres(manga: SManga): List<GenreRoute> = manga.memo["genres"].genreRoutes()

    protected fun relatedGenres(manga: SManga): List<GenreRoute> {
        val genres = memoGenres(manga)
        val specificGenres = genres.filterNot { it.slug.lowercase() in GENERIC_GENRES }
        return specificGenres.ifEmpty { genres }.take(3)
    }

    protected fun mangaId(manga: SManga): String? = manga.url.takeIf { it.all(Char::isDigit) }
        ?: manga.memo["id"]?.jsonPrimitive?.content

    protected fun memoPath(manga: SManga): String? = manga.memo["path"]?.jsonPrimitive?.content

    protected fun mangaMemo(path: String, genres: List<GenreRoute>, legacyId: String? = null): JsonObject = buildJsonObject {
        put("path", path)
        if (genres.isNotEmpty()) put("genres", genres.toGenreJson())
        legacyId?.let { put("id", it) }
    }

    protected fun Document.mangaId(): String? = selectFirst("[id^=manga-chapters-holder]")?.attr("data-id")?.takeIf(String::isNotBlank)
        ?: selectFirst("input.rating-post-id")?.attr("value")?.takeIf(String::isNotBlank)
        ?: selectFirst("a[data-post]")?.attr("data-post")?.takeIf(String::isNotBlank)
        ?: selectFirst("link[rel=shortlink]")?.attr("href")?.takeIf { it.contains("?p=") }
            ?.substringAfter("?p=")?.substringBefore('&')?.takeIf(String::isNotBlank)

    protected open val completedStatus = arrayOf(
        "completed", "completo", "completado", "concluído", "concluido", "finalizado",
        "achevé", "terminé", "hoàn thành", "مكتملة", "مكتمل", "已完结", "tamamlandı",
        "đã hoàn thành", "завершено", "tamamlanan", "complété",
    )
    protected open val ongoingStatus = arrayOf(
        "ongoing", "on going", "updating", "продолжается", "em lançamento", "em andamento",
        "en cours", "ativo", "lançando", "đang tiến hành", "còn nữa", "devam ediyor",
        "in corso", "in arrivo", "مستمرة", "مستمر", "en curso", "emision", "curso",
        "en marcha", "publicandose", "publicándose", "en emision", "连载中", "đang làm",
        "em postagem", "devam eden", "em progresso", "atualizações semanais",
    )
    protected open val hiatusStatus = arrayOf(
        "on hold", "hiatus", "pausado", "en espera", "durduruldu", "beklemede",
        "đang chờ", "متوقف", "en pause", "заморожено", "en attente",
    )
    protected open val cancelledStatus = arrayOf(
        "canceled", "cancelled", "cancelado", "iptal edildi", "đã hủy", "ملغي",
        "abandonné", "заброшено", "annulé",
    )

    protected fun String.toStatus(): Int = when {
        completedStatus.any { contains(it, true) } -> SManga.COMPLETED
        ongoingStatus.any { contains(it, true) } -> SManga.ONGOING
        hiatusStatus.any { contains(it, true) } -> SManga.ON_HIATUS
        cancelledStatus.any { contains(it, true) } -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    protected open val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.ENGLISH)

    protected open fun parseChapterDate(date: String?): Long {
        val value = date ?: return 0
        val normalized = value.lowercase(Locale.ROOT)
        val today = LocalDate.now(ZoneOffset.UTC)
        if (arrayOf("today", "hoje", "hoy").any(normalized::startsWith)) {
            return today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        if (arrayOf("yesterday", "ontem", "ayer", "يوم واحد").any(normalized::startsWith)) {
            return today.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        val amount = NUMBER_REGEX.find(normalized)?.value?.toLongOrNull()
        if (amount != null) {
            val unit = when {
                YEAR_WORDS.any { it in normalized } -> ChronoUnit.YEARS
                MONTH_WORDS.any { it in normalized } -> ChronoUnit.MONTHS
                WEEK_WORDS.any { it in normalized } -> ChronoUnit.WEEKS
                DAY_WORDS.any { it in normalized } -> ChronoUnit.DAYS
                HOUR_WORDS.any { it in normalized } -> ChronoUnit.HOURS
                MINUTE_WORDS.any { it in normalized } -> ChronoUnit.MINUTES
                SECOND_WORDS.any { it in normalized } -> ChronoUnit.SECONDS
                else -> null
            }
            if (unit != null) return ZonedDateTime.now(ZoneOffset.UTC).minus(amount, unit).toInstant().toEpochMilli()
        }
        return runCatching { LocalDate.parse(value, chapterDateFormat).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0)
    }

    protected fun isUpdating(value: String) = updatingRegex.containsMatchIn(value)

    protected class SearchCard(val title: String, val path: String, val thumbnail: String?)

    companion object {
        private val GENERIC_GENRES = setOf("manga", "manhwa", "manhua", "webtoon")
        private val URL_REGEX = Regex("""^https?://[^\s/$.?#].[^\s]*$""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
        private val IMAGE_DESCRIPTOR_REGEX = Regex("""^(\d+|\d+\.\d+)[wx]$""")
        private val NUMBER_REGEX = Regex("\\d+")
        private val YEAR_WORDS = arrayOf("year", "año", "ano", "năm", "yıl", "سنة", "سنوات")
        private val MONTH_WORDS = arrayOf("month", "mes", "tháng", "ay", "شهر", "أشهر", "شهور")
        private val WEEK_WORDS = arrayOf("week", "semana", "tuần", "hafta", "أسبوع", "أسابيع")
        private val DAY_WORDS = arrayOf("day", "día", "dia", "jour", "hari", "gün", "ngày", "giorni", "أيام", "天")
        private val HOUR_WORDS = arrayOf("hour", "hora", "heure", "jam", "saat", "giờ", "ore", "ساعة", "ساعات", "小时")
        private val MINUTE_WORDS = arrayOf("minute", "minuto", "min", "menit", "dakika", "phút", "دقيقة", "دقائق")
        private val SECOND_WORDS = arrayOf("second", "segundo", "sec", "detik", "giây", "ثانية", "ثوان")
        private fun String.hexBytes() = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
