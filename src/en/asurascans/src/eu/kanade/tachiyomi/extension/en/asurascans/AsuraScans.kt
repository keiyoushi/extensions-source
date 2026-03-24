package eu.kanade.tachiyomi.extension.en.asurascans

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import org.jsoup.nodes.Document
import java.util.Collections

class AsuraScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Asura Scans"

    override val baseUrl = "https://asurascans.com"

    private val apiUrl = "https://api.asurascans.com/api"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences: SharedPreferences = getPreferences()

    init {
        // remove legacy preferences
        preferences.run {
            if (contains("pref_url_map")) {
                edit().remove("pref_url_map").apply()
            }
            if (contains("pref_base_url_host")) {
                edit().remove("pref_base_url_host").apply()
            }
            if (contains("pref_permanent_manga_url_2_en")) {
                edit().remove("pref_permanent_manga_url_2_en").apply()
            }
            if (contains("pref_slug_map")) {
                edit().remove("pref_slug_map").apply()
            }
            if (contains("pref_dynamic_url")) {
                edit().remove("pref_dynamic_url").apply()
            }
            if (contains("pref_slug_map_2")) {
                edit().remove("pref_slug_map_2").apply()
            }
            if (contains("pref_force_high_quality")) {
                edit().remove("pref_force_high_quality").apply()
            }
        }
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(::scrambledImageInterceptor)
        .rateLimit(2, 2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter(defaultSort = "popular")))

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(SortFilter(defaultSort = "latest")))

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()

        url.addQueryParameter("offset", ((page - 1) * PER_PAGE_LIMIT).toString())
        url.addQueryParameter("limit", PER_PAGE_LIMIT.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("search", query)
        }

        filters.filterIsInstance<UriFilter>().forEach {
            it.addToUri(url)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<DataDto<List<MangaDto>>>()
        val mangas = result.data.orEmpty().map { it.toSManga() }
        result.data.orEmpty().forEach {
            slugMap[it.slug] = "$baseUrl${it.publicUrl}".toHttpUrl().pathSegments.last()
        }
        slugMap.persist()
        return MangasPage(mangas, result.meta!!.hasMore)
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenresFilter(),
        MinChaptersFilter(),
    )

    override fun getMangaUrl(manga: SManga): String {
        val match = OLD_FORMAT_MANGA_REGEX.find(manga.url)?.groupValues?.get(2)
        val slug = match ?: manga.url.substringAfter("/series/").substringBefore("/")
        val randomSlug = slugMap[slug] ?: slug

        return "$baseUrl/comics/$randomSlug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val match = OLD_FORMAT_MANGA_REGEX.find(manga.url)?.groupValues?.get(2)
        val slug = match ?: manga.url.substringAfter("/series/").substringBefore("/")
        val randomSlug = slugMap[slug] ?: slug

        return GET("$apiUrl/series/$randomSlug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val json = response.body.string()
        val mangaData = run {
            val wrapper = json.parseAs<DataDto<MangaDetailsDto>>()
            wrapper.data ?: json.parseAs<MangaDetailsDto>()
        }
        slugMap[mangaData.series.slug] = "$baseUrl${mangaData.series.publicUrl}".toHttpUrl().pathSegments.last()
        slugMap.persist()
        return mangaData.series.toSMangaDetails()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaSlug = chapter.url.substringAfter("/series/").substringBefore("/")
        val randomSlug = slugMap[mangaSlug] ?: mangaSlug
        val number = chapter.url.substringAfter("/chapter/")

        return "$baseUrl/comics/$randomSlug/chapter/$number"
    }

    override fun chapterListRequest(manga: SManga): Request {
        val match = OLD_FORMAT_MANGA_REGEX.find(manga.url)?.groupValues?.get(2)
        val slug = match ?: manga.url.substringAfter("/series/").substringBefore("/")
        val randomSlug = slugMap[slug] ?: slug
        return GET("$baseUrl/comics/$randomSlug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chaptersData = response.extractAstroProp<ChapterListDto>("chapters")
        val hidePremium = preferences.hidePremiumChapters()

        return chaptersData.chapters
            .filterNot { hidePremium && it.isLocked }
            .map { it.toSChapter() }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        var pages = document.extractAstroProp<PageListDto>("pages").pages

        if (pages.isEmpty()) {
            val accessToken = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
                .find { it.name == "access_token" }?.value
                ?: throw Exception("Premium Chapter")
            val pageToken = document.selectFirst("script:containsData(pageToken)")
                ?.data()?.let { pageTokenRegex.find(it)?.groupValues?.get(1) }
                ?: "asura-reader-2026"

            val mangaSlug = response.request.url.pathSegments[1]
            val number = response.request.url.pathSegments[3]
            val url = "$apiUrl/series/$mangaSlug/chapters/$number"
            val headers = headersBuilder()
                .set("Authorization", "Bearer $accessToken")
                .set("X-Page-Token", pageToken)
                .build()

            pages = client.newCall(GET(url, headers)).execute()
                .parseAs<PremiumPageListDto>().data.chapter.pages
        }

        return pages.mapIndexed { index, pageDto ->
            val url = if (pageDto.tiles.orEmpty().isNotEmpty()) {
                val data = PageData(
                    pageDto.tiles!!,
                    pageDto.tileCols ?: 4,
                    pageDto.tileRows ?: 5,
                )
                pageDto.url.toHttpUrl().newBuilder()
                    .fragment(data.toJsonString())
                    .toString()
            } else {
                pageDto.url
            }

            Page(index, imageUrl = url)
        }
    }

    val pageTokenRegex = Regex("""pageToken\*=\*"([^"]+)"""")

    private fun scrambledImageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment
            ?: return response
        if (!fragment.startsWith("{")) return response

        val pageData = fragment.parseAs<PageData>()
        val source = BitmapFactory.decodeStream(response.body.byteStream())

        val tileW = source.width / pageData.tileCols
        val tileH = source.height / pageData.tileRows

        val output = Bitmap.createBitmap(
            tileW * pageData.tileCols,
            tileH * pageData.tileRows,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)

        for (w in pageData.tiles.indices) {
            val j = pageData.tiles[w]

            val srcCol = w % pageData.tileCols
            val srcRow = w / pageData.tileCols
            val dstCol = j % pageData.tileCols
            val dstRow = j / pageData.tileCols

            val srcRect = Rect(srcCol * tileW, srcRow * tileH, (srcCol + 1) * tileW, (srcRow + 1) * tileH)
            val dstRect = Rect(dstCol * tileW, dstRow * tileH, (dstCol + 1) * tileW, (dstRow + 1) * tileH)

            canvas.drawBitmap(source, srcRect, dstRect, null)
        }

        val buffer = Buffer().apply {
            output.compress(Bitmap.CompressFormat.WEBP, 100, outputStream())
        }

        source.recycle()
        output.recycle()

        return response.newBuilder()
            .body(buffer.asResponseBody("image/webp".toMediaType()))
            .build()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PREMIUM_CHAPTERS
            title = "Hide premium chapters"
            summary = "Hides the chapters that require a subscription to view"
            setDefaultValue(true)
        }.let(screen::addPreference)
    }

    private fun SharedPreferences.hidePremiumChapters(): Boolean = getBoolean(
        PREF_HIDE_PREMIUM_CHAPTERS,
        true,
    )

    private inline fun <reified T> Document.extractAstroProp(key: String): T {
        val prop = selectFirst("[props*=$key]")?.attr("props")
            ?: throw Exception("Unable to find prop with $key")
        val json = prop.parseAs<JsonElement>()
        val unwrapped = json.unwrapAstro()

        return unwrapped.parseAs()
    }

    private inline fun <reified T> Response.extractAstroProp(key: String): T = asJsoup().extractAstroProp(key)

    private fun JsonElement.unwrapAstro(): JsonElement = when (this) {
        is JsonArray -> when {
            size == 2 && this[0] is JsonPrimitive -> this[1].unwrapAstro()
            else -> JsonArray(map { it.unwrapAstro() })
        }
        is JsonObject -> JsonObject(mapValues { it.value.unwrapAstro() })
        else -> this
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private val slugMap: MutableMap<String, String> =
        Collections.synchronizedMap(preferences.getString(SLUG_MAP, "{}")!!.parseAs())

    private val flushScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null
    private val flushLock = Mutex()

    private fun MutableMap<String, String>.persist() {
        flushScope.launch {
            flushLock.withLock {
                flushJob?.cancel()
                flushJob = launch {
                    delay(500)
                    flushLock.withLock {
                        preferences.edit().putString(SLUG_MAP, this@persist.toJsonString()).apply()
                    }
                }
            }
        }
    }

    companion object {
        private const val PER_PAGE_LIMIT = 20
        private val OLD_FORMAT_MANGA_REGEX = """^/manga/(\d+-)?([^/]+)/?$""".toRegex()
        private const val PREF_HIDE_PREMIUM_CHAPTERS = "pref_hide_premium_chapters"
        private const val SLUG_MAP = "slug_map_v2"
    }
}
