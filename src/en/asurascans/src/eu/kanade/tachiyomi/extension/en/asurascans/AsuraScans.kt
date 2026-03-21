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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import kotlin.text.replace

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
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            if (url.pathSegments[0] == "api") {
                val cookies = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
                val token = cookies.find { it.name == "access_token" }?.value

                if (token != null) {
                    val newRequest = request.newBuilder().apply {
                        header("Authorization", "Bearer $token")
                        if (request.url.pathSegments[3] == "chapters") {
                            header("X-Page-Token", "asura-reader-2026")
                        }
                    }.build()
                    chain.proceed(newRequest)
                } else {
                    chain.proceed(request)
                }
            } else {
                chain.proceed(request)
            }
        }
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
        return MangasPage(mangas, result.meta!!.hasMore)
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        GenresFilter(),
        MinChaptersFilter(),
    )

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}".replace("/series/", "/comics/")

    override fun mangaDetailsRequest(manga: SManga): Request {
        val match = OLD_FORMAT_MANGA_REGEX.find(manga.url)?.groupValues?.get(2)
        val slug = match ?: manga.url.substringAfter("/series/").substringBefore("/")
        return GET("$apiUrl/series/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val json = response.body.string()
        val mangaData = run {
            val wrapper = json.parseAs<DataDto<MangaDetailsDto>>()
            wrapper.data ?: json.parseAs<MangaDetailsDto>()
        }

        return mangaData.series.toSMangaDetails()
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}".replace("/series/", "/comics/")

    override fun chapterListRequest(manga: SManga): Request {
        val match = OLD_FORMAT_MANGA_REGEX.find(manga.url)?.groupValues?.get(2)
        val slug = match ?: manga.url.substringAfter("/series/").substringBefore("/")
        return GET("$baseUrl/comics/$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chaptersProps = document.selectFirst("[props*=chapters]")!!.attr("props")
        val jsonElement = Json.parseToJsonElement(chaptersProps).unwrapAstroProps()
        val chaptersData = jsonElement.parseAs<ChapterListDto>()
        val hidePremium = preferences.hidePremiumChapters()

        return chaptersData.chapters
            .filterNot { hidePremium && it.isLocked }
            .map { it.toSChapter() }
    }

    private fun JsonElement.unwrapAstroProps(insideArray: Boolean = false): JsonElement = when (this) {
        is JsonArray -> {
            if (insideArray) {
                JsonArray(this.map { it.unwrapAstroProps() })
            } else {
                this[1].unwrapAstroProps(true)
            }
        }
        is JsonObject -> {
            JsonObject(
                this.mapValues { it.value.unwrapAstroProps() },
            )
        }
        else -> this
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaSlug = chapter.url.substringAfter("/series/").substringBefore("/")
        val number = chapter.url.substringAfter("/chapter/")
        return GET("$apiUrl/series/$mangaSlug/chapters/$number", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapterData = response.parseAs<DataDto<ChapterWrapperDto>>().data
        return chapterData!!.chapter.pages.orEmpty().mapIndexed { index, pageDto ->
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

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private const val PER_PAGE_LIMIT = 20
        private val OLD_FORMAT_MANGA_REGEX = """^/manga/(\d+-)?([^/]+)/?$""".toRegex()
        private const val PREF_HIDE_PREMIUM_CHAPTERS = "pref_hide_premium_chapters"
    }
}
