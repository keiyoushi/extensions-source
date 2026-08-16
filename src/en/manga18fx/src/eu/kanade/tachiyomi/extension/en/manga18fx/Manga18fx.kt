package eu.kanade.tachiyomi.extension.en.manga18fx

import android.util.Base64
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
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
import kotlin.text.isNotBlank

private const val PAGE_SIZE = 25

// Similar to Madara, but not really
@Source
abstract class Manga18fx : KeiSource() {
    private val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val block = document.selectFirst(Evaluator.Class("trending-block"))!!
        val mangas = block.select(Evaluator.Tag("a")).map(::mangaFromElement)
        return MangasPage(mangas, false)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        url = element.attr("href")
        title = element.attr("title")
        thumbnail_url = element.selectFirst(Evaluator.Tag("img"))!!.attr("data-src")
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseLatest(client.get("$baseUrl/page/$page").asJsoup())

    private fun parseLatest(document: Document): MangasPage {
        val mangas = document.select(Evaluator.Class("bsx-item")).map {
            mangaFromElement(it.selectFirst(Evaluator.Tag("a"))!!)
        }
        val nextButton = document.selectFirst(Evaluator.Class("next"))
        val hasNextPage = nextButton != null && nextButton.hasClass("disabled").not()
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isEmpty()) {
            filters.forEach { filter ->
                if (filter is GenreFilter) {
                    return parseLatest(client.get(filter.vals[filter.state].url).asJsoup())
                }
            }
            return getLatestUpdates(page)
        }

        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()

        return parseLatest(client.get(url).asJsoup())
    }

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = client.get(baseUrl.toHttpUrl().resolve(manga.url)!!).asJsoup()
        return SMangaUpdate(
            if (fetchDetails) parseDetails(document, "", manga.url) else manga,
            if (fetchChapters) fetchChapters(manga.url, document) else chapters,
        )
    }

    private val mangaDetailsSelectorDescription = ".dsct"

    private fun chapterListSelector() = ".row-content-chapter > *"

    private val chapterDateSelector = "span.chapter-time"

    class GenreFilter(val vals: List<Genre>) : Filter.Select<String>("Genre", vals.map { it.name }.toTypedArray())

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl).asJsoup()
        return document.select(".header-bottom li a").map {
            val href = it.attr("href")
            val url = if (href.startsWith("http")) href else "$baseUrl/$href"

            Genre(it.text(), url)
        }.toJsonElement()
    }

    @Serializable
    class Genre(val name: String, val url: String)

    private var hardCodedTypes: List<Genre> = listOf(
        Genre("Manhwa", "$baseUrl/manga-genre/manhwa"),
        Genre("Manhua", "$baseUrl/manga-genre/manhua"),
        Genre("Raw", "$baseUrl/manga-genre/raw"),
    )

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Genre>>()

        val filters = buildList(2) {
            add(Filter.Header("Filters are ignored for text search!"))

            if (!genres.isNullOrEmpty()) {
                add(
                    GenreFilter(hardCodedTypes + genres),
                )
            } else {
                add(
                    Filter.Header("Wait for mangas to load then tap Reset"),
                )
            }
        }

        return FilterList(filters)
    }

    // Lifted from Madara:

    private val xhrHeaders: Headers
        get() = headersBuilder().set("X-Requested-With", "XMLHttpRequest").build()

    override val supportsFilterFetching get() = true
    override val supportsRelatedMangas get() = true

    private fun archiveSelector() = "div.page-item-detail, .manga__item, .c-tabs-item__content"
    private val archiveUrlSelector = ".post-title a"
    private val mangaDetailsSelectorTitle = "div.post-title h3, div.post-title h1, #manga-title > h1"
    private val mangaDetailsSelectorAuthor = "div.author-content > a, div.manga-authors > a"
    private val mangaDetailsSelectorArtist = "div.artist-content > a"
    private val mangaDetailsSelectorStatus = "div.summary-content, div.summary-heading:contains(Status) + div"
    private val mangaDetailsSelectorThumbnail = "div.summary_image img"
    private val mangaDetailsSelectorGenre = "div.genres-content a"
    private val mangaDetailsSelectorTag = "div.tags-content a"
    private val seriesTypeSelector = ".post-content_item:contains(Type) .summary-content"
    private val altNameSelector = ".post-content_item:contains(Alt) .summary-content"
    private val updatingRegex = "Updating|Atualizando".toRegex(RegexOption.IGNORE_CASE)
    private val chapterUrlSelector = "a"
    private val pageListParseSelector = "div.page-break, li.blocks-gallery-item, .reading-content .text-left:not(:has(.blocks-gallery-item)) img"

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

    private fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = SManga.create().apply {
        url = preserveUrl?.takeIf { !it.all(Char::isDigit) } ?: id
        title = document.selectFirst(mangaDetailsSelectorTitle)!!.text()
        author = document.select(mangaDetailsSelectorAuthor).eachText().filterNot(::isUpdating).joinToString().ifBlank { null }
        artist = document.select(mangaDetailsSelectorArtist).eachText().filterNot(::isUpdating).joinToString().ifBlank { null }
        description = document.selectFirst(mangaDetailsSelectorDescription)?.let { element ->
            element.select("p").takeIf(List<Element>::isNotEmpty)?.joinToString("\n\n") { it.text() } ?: element.text()
        }
        document.selectFirst(altNameSelector)?.ownText()?.takeIf { it.isNotEmpty() && !isUpdating(it) }?.let { alternative ->
            description = listOfNotNull(description, "Alternative Names: $alternative").joinToString("\n\n")
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

    private fun fetchChapters(mangaPath: String, mangaPage: Document): List<SChapter> = parseChapterList(mangaPage, mangaPath)

    private fun parseArchive(document: Document): List<SManga> = document.select(archiveSelector()).mapNotNull { element ->
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

    private fun parseChapterList(document: Document, mangaPath: String): List<SChapter> = document.select(chapterListSelector()).mapNotNull { chapterFromElement(it, mangaPath) }

    private fun chapterFromElement(element: Element, mangaPath: String): SChapter? {
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
        return parsePages(document)
    }

    private suspend fun fetchChapterDocument(chapterUrl: String): Document = client.get(chapterUrl).asJsoup()

    private fun parsePages(document: Document): List<Page> {
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

    private fun imageFromElement(element: Element): String? = when {
        element.hasAttr("data-src") -> element.attr("abs:data-src")
        element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
        element.hasAttr("data-cfsrc") -> element.attr("abs:data-cfsrc")
        element.hasAttr("data-manga-src") -> element.attr("abs:data-manga-src")
        element.hasAttr("srcset") -> element.attr("abs:srcset").getSrcSetImage()
        else -> element.attr("abs:src")
    }

    private fun processThumbnail(url: String?, fromSearch: Boolean = false): String? = url

    private fun String.getSrcSetImage(): String? {
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

    private suspend fun fetchRelatedMangaList(id: String, genres: List<GenreRoute>): List<SManga> {
        val body = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", "0")
            add("template", "madara-core/content/content-archive")
            add("vars[posts_per_page]", PAGE_SIZE.toString())
            add("vars[template]", "archive")
            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[orderby]", "rand")
            add("vars[sidebar]", "right")
            add("vars[manga_archives_item_layout]", "big_thumbnail")
            add("vars[post__not_in][0]", id)
            add("vars[tax_query][0][taxonomy]", "wp-manga-genre")
            add("vars[tax_query][0][field]", "slug")
            genres.forEachIndexed { i, genre -> add("vars[tax_query][0][terms][$i]", genre.slug) }
        }.build()
        return parseArchive(client.post("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, body).asJsoup())
    }

    private fun memoGenres(manga: SManga): List<GenreRoute> = manga.memo["genres"].genreRoutes()

    private fun relatedGenres(manga: SManga): List<GenreRoute> {
        val genres = memoGenres(manga)
        val specificGenres = genres.filterNot { it.slug.lowercase() in GENERIC_GENRES }
        return specificGenres.ifEmpty { genres }.take(3)
    }

    private fun mangaId(manga: SManga): String? = manga.url.takeIf { it.all(Char::isDigit) }
        ?: manga.memo["id"]?.jsonPrimitive?.content

    private fun memoPath(manga: SManga): String? = manga.memo["path"]?.jsonPrimitive?.content

    private fun mangaMemo(path: String, genres: List<GenreRoute>, legacyId: String? = null): JsonObject = buildJsonObject {
        put("path", path)
        if (genres.isNotEmpty()) put("genres", genres.toGenreJson())
        legacyId?.let { put("id", it) }
    }

    private fun Document.mangaId(): String? = selectFirst("[id^=manga-chapters-holder]")?.attr("data-id")?.takeIf(String::isNotBlank)
        ?: selectFirst("input.rating-post-id")?.attr("value")?.takeIf(String::isNotBlank)
        ?: selectFirst("a[data-post]")?.attr("data-post")?.takeIf(String::isNotBlank)
        ?: selectFirst("link[rel=shortlink]")?.attr("href")?.takeIf { it.contains("?p=") }
            ?.substringAfter("?p=")?.substringBefore('&')?.takeIf(String::isNotBlank)

    private val completedStatus = arrayOf(
        "completed", "completo", "completado", "concluído", "concluido", "finalizado",
        "achevé", "terminé", "hoàn thành", "مكتملة", "مكتمل", "已完结", "tamamlandı",
        "đã hoàn thành", "завершено", "tamamlanan", "complété",
    )
    private val ongoingStatus = arrayOf(
        "ongoing", "on going", "updating", "продолжается", "em lançamento", "em andamento",
        "en cours", "ativo", "lançando", "đang tiến hành", "còn nữa", "devam ediyor",
        "in corso", "in arrivo", "مستمرة", "مستمر", "en curso", "emision", "curso",
        "en marcha", "publicandose", "publicándose", "en emision", "连载中", "đang làm",
        "em postagem", "devam eden", "em progresso", "atualizações semanais",
    )
    private val hiatusStatus = arrayOf(
        "on hold", "hiatus", "pausado", "en espera", "durduruldu", "beklemede",
        "đang chờ", "متوقف", "en pause", "заморожено", "en attente",
    )
    private val cancelledStatus = arrayOf(
        "canceled", "cancelled", "cancelado", "iptal edildi", "đã hủy", "ملغي",
        "abandonné", "заброшено", "annulé",
    )

    private fun String.toStatus(): Int = when {
        completedStatus.any { contains(it, true) } -> SManga.COMPLETED
        ongoingStatus.any { contains(it, true) } -> SManga.ONGOING
        hiatusStatus.any { contains(it, true) } -> SManga.ON_HIATUS
        cancelledStatus.any { contains(it, true) } -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun parseChapterDate(date: String?): Long {
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

    private fun isUpdating(value: String) = updatingRegex.containsMatchIn(value)

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
