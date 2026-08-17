package eu.kanade.tachiyomi.extension.id.voratoon

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
class Voratoon(
    override val lang: String = "id",
    override val id: Long = 0L,
) : HttpSource(),
    ConfigurableSource {

    override val name = "VoraToon"

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override val baseUrl: String
        get() = preferences.getString(BASE_URL_PREF, DEFAULT_BASE_URL)!!

    private val apiUrl: String
        get() = preferences.getString(API_URL_PREF, DEFAULT_API_URL)!!

    private val cdnUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    override val supportsLatest = true

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0L

        return try {
            val cleanDate = dateStr.substringBefore("+").substringBefore("Z")

            dateFormat.parse(cleanDate)?.time ?: 0L
        } catch (e: Exception) {
            try {
                val fallbackFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fallbackFormat.parse(dateStr.substringBefore("+").substringBefore("Z"))?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
        }
    }

    override fun headersBuilder() = Headers.Builder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept", "*/*")
        .add("User-Agent", cdnUserAgent)

    private fun parseSeriesList(
        response: Response,
    ): MangasPage {
        val root =
            json.parseToJsonElement(
                response.body.string(),
            ).jsonObject

        val items =
            root["data"]?.jsonArray
                ?: JsonArray(emptyList())

        val mangas =
            items.mapNotNull { item ->

                val data =
                    item.jsonObject["data"]
                        ?.jsonObject
                        ?: return@mapNotNull null

                val slug =
                    data["slug"]
                        ?.jsonPrimitive
                        ?.content
                        ?: return@mapNotNull null

                SManga.create().apply {
                    title =
                        data["title"]
                            ?.jsonPrimitive
                            ?.content
                            ?: "Unknown"

                    thumbnail_url =
                        data["coverImage"]
                            ?.jsonPrimitive
                            ?.content

                    setUrlWithoutDomain(
                        "/series/$slug",
                    )
                }
            }

        return MangasPage(
            mangas,
            mangas.isNotEmpty(),
        )
    }

    override fun popularMangaRequest(
        page: Int,
    ) = GET(
        "$apiUrl/series/trending?take=10&page=$page&includeMeta=true",
        headers,
    )

    override fun popularMangaParse(
        response: Response,
    ) = parseSeriesList(response)

    override fun latestUpdatesRequest(
        page: Int,
    ) = GET(
        "$apiUrl/series?take=30&page=$page&sort=latest&sortOrder=desc&includeMeta=true&takeChapter=4",
        headers,
    )

    override fun latestUpdatesParse(
        response: Response,
    ) = parseSeriesList(response)

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        val encoded =
            URLEncoder.encode(
                query,
                "UTF-8",
            )

        return GET(
            "$apiUrl/series?take=8&page=$page&sort=title&sortOrder=asc&includeMeta=true&takeChapter=1&title=$encoded",
            headers,
        )
    }

    override fun searchMangaParse(
        response: Response,
    ) = parseSeriesList(response)

    override fun mangaDetailsRequest(
        manga: SManga,
    ): Request {
        val slug =
            manga.url.substringAfterLast("/")

        val filter =
            URLEncoder.encode(
                "slug==$slug",
                "UTF-8",
            )

        return GET(
            "$apiUrl/series?take=1&page=1&includeMeta=true&takeChapter=5&filter=$filter",
            headers,
        )
    }

    override fun mangaDetailsParse(
        response: Response,
    ): SManga {
        val root =
            json.parseToJsonElement(
                response.body.string(),
            ).jsonObject

        val item =
            root["data"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?: return SManga.create()

        val data =
            item["data"]
                ?.jsonObject
                ?: return SManga.create()

        return SManga.create().apply {
            title =
                data["title"]
                    ?.jsonPrimitive
                    ?.content
                    ?: ""

            author =
                data["author"]
                    ?.jsonPrimitive
                    ?.content

            description =
                data["synopsis"]
                    ?.jsonPrimitive
                    ?.content

            thumbnail_url =
                data["coverImage"]
                    ?.jsonPrimitive
                    ?.content

            genre =
                data["genres"]
                    ?.jsonArray
                    ?.joinToString(", ") {
                        it.jsonObject["data"]
                            ?.jsonObject
                            ?.get("name")
                            ?.jsonPrimitive
                            ?.content
                            ?: ""
                    }

            status =
                when (
                    data["status"]
                        ?.jsonPrimitive
                        ?.content
                        ?.lowercase()
                ) {
                    "ongoing" -> SManga.ONGOING
                    "completed" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
        }
    }

    override fun chapterListRequest(
        manga: SManga,
    ): Request {
        val slug =
            manga.url.substringAfterLast("/")

        return GET(
            "$apiUrl/series/$slug/chapters",
            headers,
        )
    }

    override fun chapterListParse(
        response: Response,
    ): List<SChapter> {
        val root =
            json.parseToJsonElement(
                response.body.string(),
            ).jsonObject

        val items =
            root["data"]?.jsonArray
                ?: return emptyList()

        val segments = response.request.url.pathSegments
        val slug = segments[segments.size - 2]

        return items.mapNotNull { item ->

            val chapter =
                item.jsonObject

            val chapterData =
                chapter["data"]
                    ?.jsonObject
                    ?: return@mapNotNull null

            val chapterIndex =
                chapterData["index"]
                    ?.jsonPrimitive
                    ?.content
                    ?: return@mapNotNull null

            SChapter.create().apply {
                name = "Chapter $chapterIndex"

                setUrlWithoutDomain(
                    "/series/$slug/chapters/$chapterIndex",
                )

                val dateStr = chapter["createdAt"]?.jsonPrimitive?.content
                    ?: chapter["updatedAt"]?.jsonPrimitive?.content
                    ?: chapterData["createdAt"]?.jsonPrimitive?.content
                    ?: chapterData["updatedAt"]?.jsonPrimitive?.content

                date_upload = parseDate(dateStr)
            }
        }
    }

    override fun pageListRequest(
        chapter: SChapter,
    ): Request {
        val regex =
            """/series/([^/]+)/chapters/(.+)""".toRegex()

        val match =
            regex.find(chapter.url)
                ?: return GET(baseUrl, headers)

        val slug =
            match.groupValues[1]

        val chapterIndex =
            match.groupValues[2]

        return GET(
            "$apiUrl/series/$slug/chapters/$chapterIndex",
            headers,
        )
    }

    override fun pageListParse(
        response: Response,
    ): List<Page> {
        val root =
            json.parseToJsonElement(
                response.body.string(),
            ).jsonObject

        val chapterData =
            root["data"]
                ?.jsonObject
                ?.get("data")
                ?.jsonObject
                ?: return emptyList()

        val imagesArray =
            chapterData["images"]
                ?.jsonArray

        val imageUrls: List<String> =
            if (!imagesArray.isNullOrEmpty()) {
                imagesArray.map { it.jsonPrimitive.content }
            } else {
                root["data"]
                    ?.jsonObject
                    ?.get("dataImages")
                    ?.jsonObject
                    ?.toSortedMap(
                        compareBy { it.toIntOrNull() ?: Int.MAX_VALUE },
                    )
                    ?.values
                    ?.map { it.jsonPrimitive.content }
                    ?: emptyList()
            }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(
                index = index,
                url = imageUrl,
                imageUrl = imageUrl,
            )
        }
    }

    override fun imageRequest(page: Page): Request = GET(
        page.imageUrl!!,
        Headers.Builder()
            .add("User-Agent", cdnUserAgent)
            .build(),
    )

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = "Base URL (Link Utama)"
            summary = "Link saat ini: ${this@Voratoon.baseUrl}\nUbah jika website berganti domain."
            setDefaultValue(DEFAULT_BASE_URL)
            dialogTitle = "Masukkan Base URL baru"
            dialogMessage = "Contoh: https://v2.voratoon.com"
        }

        val apiUrlPref = EditTextPreference(screen.context).apply {
            key = API_URL_PREF
            title = "API URL (Link Data)"
            summary = "Link saat ini: ${this@Voratoon.apiUrl}\nUbah jika link API berganti."
            setDefaultValue(DEFAULT_API_URL)
            dialogTitle = "Masukkan API URL baru"
            dialogMessage = "Contoh: https://api.v2.voratoon.com"
        }

        screen.addPreference(baseUrlPref)
        screen.addPreference(apiUrlPref)
    }

    companion object {
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val DEFAULT_BASE_URL = "https://v1.voratoon.com"

        private const val API_URL_PREF = "overrideApiUrl"
        private const val DEFAULT_API_URL = "https://api.voratoon.com"
    }
}
