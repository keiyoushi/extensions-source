package eu.kanade.tachiyomi.extension.all.toonlook

import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

private val CHAPTER_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT)

@Source
abstract class ToonLook : KeiSource() {

    private var nonce: String? = null
    private var nonceExpiresAt = 0L

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(::nonceInterceptor)
        .addInterceptor(ScrambleInterceptor())

    private fun nonceInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestWithNonce = if (request.url.encodedPath.endsWith("/wp-admin/admin-ajax.php")) {
            val body = request.body as? FormBody
            if (body != null) {
                request.newBuilder().post(body.withNonce(getNonce())).build()
            } else {
                request
            }
        } else {
            request
        }

        return chain.proceed(requestWithNonce).also(::updateNonce)
    }

    @Synchronized
    private fun getNonce(): String {
        val currentNonce = nonce
        if (currentNonce != null && System.nanoTime() < nonceExpiresAt) {
            return currentNonce
        }

        val request = Request.Builder()
            .url("$baseUrl/top-manhwa/?sortby=daily_top")
            .headers(headers)
            .get()
            .build()
        client.newCall(request).execute().use(::updateNonce)

        return nonce ?: throw IOException("Could not find nonce")
    }

    @Synchronized
    private fun updateNonce(response: Response) {
        if (response.request.url.host != baseUrl.toHttpUrl().host) return
        if (response.body.contentType()?.let { it.type == "text" && it.subtype == "html" } != true) return

        val document = response.peekBody(Int.MAX_VALUE.toLong()).string().asJsoup(baseUrl)
        val updatedNonce = findNonce(document) ?: return
        nonce = updatedNonce
        nonceExpiresAt = System.nanoTime() + NONCE_TIMEOUT_NANOS
    }

    private fun FormBody.withNonce(value: String): FormBody = FormBody.Builder().apply {
        var hasNonce = false
        for (index in 0 until size) {
            if (name(index) == NONCE_FIELD) {
                add(NONCE_FIELD, value)
                hasNonce = true
            } else {
                add(name(index), value(index))
            }
        }
        if (!hasNonce) add(NONCE_FIELD, value)
    }.build()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val pageUrl = "$baseUrl/top-manhwa/"
        val document = if (page == 1) {
            client.get("$pageUrl?sortby=daily_top").asJsoup()
        } else {
            val body = FormBody.Builder()
                .add("action", "mangora_get_ranking")
                .add("nonce", "")
                .add("period", "daily_top")
                .add("paged", page.toString())
                .add("page_url", pageUrl)
                .build()
            val requestHeaders = headers.newBuilder()
                .set("Referer", "$pageUrl?sortby=daily_top&paged=$page")
                .build()
            val response = client.post("$baseUrl/wp-admin/admin-ajax.php", requestHeaders, body)
                .parseAs<RankingResponse>()

            if (!response.success) throw Exception("Ranking request failed")
            response.data.asJsoup(baseUrl)
        }

        return parseMangasPage(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toSlug()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            when {
                query.isNotBlank() -> addQueryParameter("s", query)
                genre != null -> addPathSegments("genre/$genre/")
                else -> addPathSegments("manhwa/")
            }
            if (page > 1) {
                addPathSegments("page/$page/")
            }
        }.build()

        val document = client.get(url).asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] != "manhwa") {
            return null
        }
        return fetchMangaDetails(segments[1]).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manhwa/${manga.url}/"

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterUrl = "$baseUrl/${chapter.url}".toHttpUrl()
        val chapterLang = if (lang == "all") chapterUrl.queryParameter("lang")!! else lang
        return chapterUrl.newBuilder().apply {
            setQueryParameter("lang", chapterLang)
        }.build().toString()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            manga = parseMangaDetails(document, slug),
            chapters = parseChapterList(document, lang),
        )
    }

    private suspend fun fetchMangaDetails(slug: String): SManga {
        val document = client.get("$baseUrl/manhwa/$slug/").asJsoup()
        return parseMangaDetails(document, slug)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val script = document.select("script").firstOrNull { it.data().contains("chapterData") }?.data()
            ?: throw Exception("Could not find chapter data")
        val json = CHAPTER_DATA_REGEX.find(script)?.groupValues?.get(1)
            ?: throw Exception("Could not parse chapter data")
        val data = json.parseAs<ChapterData>()
        val pages = String(Base64.decode(data.data, Base64.DEFAULT)).parseAs<List<PageItem>>()
        val scramble = data.imageScramble?.takeIf { it.enabled }

        return pages.mapIndexed { index, item ->
            val fragment = scramble
                ?.let { "#scramble=${data.lang},${it.stripHeight},${it.salt}" }
                .orEmpty()
            Page(index, imageUrl = "${data.base}/${item.src}$fragment")
        }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = manga.memo["relatedMangas"]
        ?.parseAs<List<RelatedMangaDto>>()
        ?.map { it.toSManga() }
        .orEmpty()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("wp-json/wp/v2/genre")
            .addQueryParameter("per_page", "100")
            .build(),
    ).parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>()?.filter { it.count > 0 }.orEmpty()
        return FilterList(GenreFilter(genres))
    }
}

private val CHAPTER_DATA_REGEX = """chapterData\s*=\s*(\{.*?\})\s*;""".toRegex(RegexOption.DOT_MATCHES_ALL)
private const val NONCE_FIELD = "nonce"
private val NONCE_TIMEOUT_NANOS = TimeUnit.MINUTES.toNanos(10)

private fun findNonce(document: Document): String? = document.selectFirst("#formReport input[name=nonce]")?.attrOrNull("value")

private fun parseMangasPage(document: Document): MangasPage {
    val mangas = document.select(".manga-item").map(::mangaFromElement)
    return MangasPage(mangas, hasNextPage = document.selectFirst("a.next") != null)
}

private fun mangaFromElement(element: Element): SManga {
    val link = element.selectFirst("a.manga-item__link")!!
    val href = link.absUrl("href")

    return SManga.create().apply {
        url = href.toHttpUrl().pathSegments.last { it.isNotEmpty() }
        title = link.text()
        thumbnail_url = element.selectFirst("img.manga-item__img-inner")?.absUrl("src")
    }
}

private fun parseMangaDetails(document: Document, slug: String): SManga = SManga.create().apply {
    url = slug
    title = document.selectFirst(".manga-titles h1")!!.text()
    thumbnail_url = document.selectFirst("img[src*='/img/cover/']")?.absUrl("src")

    description = buildString {
        document.selectFirst(".manga-desc__content")?.text()?.let { append(it) }

        document.selectFirst(".manga-titles h2")?.textOrNull()?.let {
            if (isNotEmpty()) {
                append("\n\n")
            }
            append("Alternative Titles:\n")
            append(it.split(", ").joinToString("\n") { title -> "- $title" })
        }
    }

    genre = document.select(".manga-genres a").joinToString { it.text() }.ifEmpty { null }

    status = when (document.selectFirst(".manga-status[data-status]")?.attr("data-status")?.lowercase()) {
        "ongoing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    val relatedMangas = document.select("#relatedMangaSection .manga-item")
        .map(::mangaFromElement)
        .map { it.toRelatedMangaDto() }
    memo = buildJsonObject {
        put("relatedMangas", relatedMangas.toJsonElement())
    }
}

private fun parseChapterList(document: Document, lang: String): List<SChapter> {
    val chapterItems = document.select("a.chapter-item")
    if (lang != "all") {
        return chapterItems.filter { lang in it.attr("data-langs").split(" ") }
            .map { chapterFromElement(it, lang) }
    }

    return chapterItems.flatMap { anchor ->
        val chapterTitles = anchor.attr("data-langs").split(" ").associateBy { "data-title-$it" }
        val titleAttributes = anchor.attributes().asList().filter { it.key in chapterTitles }

        titleAttributes.asReversed().map { attribute ->
            chapterFromElement(anchor, chapterTitles.getValue(attribute.key))
        }
    }
}

private fun chapterFromElement(element: Element, lang: String): SChapter {
    val chapterSlug = element.absUrl("href").toHttpUrl().pathSegments.last { it.isNotEmpty() }
    val name = element.attrOrNull("data-title-$lang") ?: element.selectFirst(".chapter-item__name")!!.textOrNull()!!

    return SChapter.create().apply {
        url = "$chapterSlug?lang=$lang"
        this.name = name
        date_upload = CHAPTER_DATE_FORMAT.tryParseDate(element.selectFirst(".chapter-item__date")?.text())
    }
}
