package eu.kanade.tachiyomi.extension.ar.mangaswat

import android.widget.Toast
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MangaSwat :
    MangaThemesia(
        "MangaSwat",
        "https://appswat.com",
        "ar",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
    ),
    ConfigurableSource {

    override val baseUrl by lazy { getPrefBaseUrl() }

    private val preferences by getPreferencesLazy()

    private val apiBaseUrl = "https://appswat.com/v2/api/v2"

    private val apiDateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    override val client = super.client.newBuilder()
        .addInterceptor(::tokenInterceptor)
        .rateLimit(1)
        .build()

    // From Akuma - CSRF token
    private var storedToken: String? = null

    private fun tokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.method == "POST" && request.header("X-CSRF-TOKEN") == null) {
            val newRequest = request.newBuilder()
            val token = getToken()
            val response = chain.proceed(
                newRequest
                    .addHeader("X-CSRF-TOKEN", token)
                    .build(),
            )

            if (response.code == 419) {
                response.close()
                storedToken = null // reset the token
                val newToken = getToken()
                return chain.proceed(
                    newRequest
                        .addHeader("X-CSRF-TOKEN", newToken)
                        .build(),
                )
            }

            return response
        }

        val response = chain.proceed(request)

        if (response.header("Content-Type")?.contains("text/html") != true) {
            return response
        }

        storedToken = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())
            .selectFirst("head meta[name*=csrf-token]")
            ?.attr("content")

        return response
    }

    private fun getToken(): String {
        if (storedToken.isNullOrEmpty()) {
            val request = GET(baseUrl, headers)
            client.newCall(request).execute().close() // updates token in interceptor
        }
        return storedToken!!
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val apiHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .add("Origin", baseUrl)
            .add("Referer", "$baseUrl/")
            .build()
        return GET("$apiBaseUrl/series/?status=79&page=$page", apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = json.decodeFromString<LatestUpdatesResponse>(response.body.string())
        val mangas = data.results.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = it.poster.mediumUrl
                url = "/series/id/${it.id}/"
            }
        }
        return MangasPage(mangas, data.next != null)
    }

    override fun popularMangaRequest(page: Int): Request {
        val apiHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .add("Origin", baseUrl)
            .add("Referer", "$baseUrl/")
            .build()
        return GET("$apiBaseUrl/series/?is_hot=true&page=$page", apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val data = json.decodeFromString<LatestUpdatesResponse>(response.body.string())
        val mangas = data.results.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = it.poster.mediumUrl
                url = "/series/id/${it.id}/"
            }
        }
        return MangasPage(mangas, data.next != null)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.removePrefix("/series/id/").removeSuffix("/")
        val apiHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .add("Origin", baseUrl)
            .add("Referer", "$baseUrl/")
            .build()
        return GET("$apiBaseUrl/series/$id/", apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = json.decodeFromString<MangaDetailsDto>(response.body.string())
        return SManga.create().apply {
            title = manga.title
            description = manga.story
            author = manga.author?.let {
                when (it) {
                    is JsonPrimitive -> it.contentOrNull
                    is JsonObject -> it["name"]?.jsonPrimitive?.contentOrNull
                    else -> null
                }
            }
            artist = manga.artist?.let {
                when (it) {
                    is JsonPrimitive -> it.contentOrNull
                    is JsonObject -> it["name"]?.jsonPrimitive?.contentOrNull
                    else -> null
                }
            }
            genre = manga.genres.joinToString { it.name }
            status = when (manga.status.name.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = manga.poster.mediumUrl
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.removePrefix("/series/id/").removeSuffix("/")
        val apiHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .add("Origin", baseUrl)
            .add("Referer", "$baseUrl/")
            .build()
        return GET("$apiBaseUrl/chapters/?serie=$id&order_by=-order&page_size=200", apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        var data = json.decodeFromString<ChapterListResponse>(response.body.string())
        val chapters = mutableListOf<SChapter>()
        chapters.addAll(data.results.map { chapterFromDto(it) })

        var nextPage = data.next
        while (nextPage != null) {
            val nextResponse = client.newCall(GET(nextPage, response.request.headers)).execute()
            data = json.decodeFromString<ChapterListResponse>(nextResponse.body.string())
            chapters.addAll(data.results.map { chapterFromDto(it) })
            nextPage = data.next
        }

        return chapters
    }

    private fun chapterFromDto(chapter: ChapterDto): SChapter = SChapter.create().apply {
        name = chapter.chapter
        date_upload = runCatching { apiDateFormat.parse(chapter.createdAt)?.time }.getOrNull() ?: 0L
        url = "/chapters/${chapter.id}/${chapter.slug}/"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.removePrefix("/chapters/").substringBefore("/")
        val apiHeaders = headersBuilder()
            .add("Accept", "application/json, text/plain, */*")
            .add("Origin", baseUrl)
            .add("Referer", "$baseUrl/")
            .build()
        return GET("$apiBaseUrl/chapters/$id/", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val chapter = json.decodeFromString<PageListResponse>(response.body.string())
        return chapter.images.map {
            Page(it.order, imageUrl = it.image)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val apiHeaders = headersBuilder()
                .add("Accept", "application/json, text/plain, */*")
                .add("Origin", baseUrl)
                .add("Referer", "$baseUrl/")
                .build()
            return GET("$apiBaseUrl/series/?search=$query&page=$page", apiHeaders)
        }

        return super.searchMangaRequest(page, query, filters)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if ("appswat.com" !in response.request.url.host) {
            return super.searchMangaParse(response)
        }

        val data = json.decodeFromString<LatestUpdatesResponse>(response.body.string())
        val mangas = data.results.map {
            SManga.create().apply {
                title = it.title
                thumbnail_url = it.poster.mediumUrl
                url = "/series/id/${it.id}/"
            }
        }
        return MangasPage(mangas, data.next != null)
    }

    @Serializable
    private data class MangaDetailsDto(
        @SerialName("title") val title: String,
        @SerialName("story") val story: String? = null,
        @SerialName("author") val author: JsonElement? = null,
        @SerialName("artist") val artist: JsonElement? = null,
        @SerialName("genres") val genres: List<GenreDto> = emptyList(),
        @SerialName("status") val status: StatusDto,
        @SerialName("poster") val poster: PosterDto,
    )

    @Serializable
    private data class GenreDto(
        @SerialName("name") val name: String,
    )

    @Serializable
    private data class StatusDto(
        @SerialName("name") val name: String,
    )

    @Serializable
    private data class LatestUpdatesResponse(
        @SerialName("results") val results: List<LatestMangaDto> = emptyList(),
        @SerialName("next") val next: String? = null,
    )

    @Serializable
    private data class LatestMangaDto(
        @SerialName("id") val id: Int,
        @SerialName("slug") val slug: String,
        @SerialName("title") val title: String,
        @SerialName("poster") val poster: PosterDto,
    )

    @Serializable
    private data class PosterDto(
        @SerialName("medium") val mediumUrl: String,
    )

    @Serializable
    private data class ChapterListResponse(
        @SerialName("count") val count: Int,
        @SerialName("next") val next: String? = null,
        @SerialName("results") val results: List<ChapterDto> = emptyList(),
    )

    @Serializable
    private data class ChapterDto(
        @SerialName("id") val id: Int,
        @SerialName("slug") val slug: String,
        @SerialName("chapter") val chapter: String,
        @SerialName("created_at") val createdAt: String,
    )

    @Serializable
    private data class PageListResponse(
        @SerialName("images") val images: List<PageDto>,
    )

    @Serializable
    private data class PageDto(
        @SerialName("image") val image: String,
        @SerialName("order") val order: Int,
    )

    companion object {
        private const val RESTART_APP = "Restart the app to apply the new URL"
        private const val BASE_URL_PREF_TITLE = "Override BaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val BASE_URL_PREF_SUMMARY = "For temporary uses. Updating the extension will erase this setting."
        private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            setDefaultValue(super.baseUrl)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: ${super.baseUrl}"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }
        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl(): String = preferences.getString(BASE_URL_PREF, super.baseUrl)!!

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != super.baseUrl) {
                preferences.edit()
                    .putString(BASE_URL_PREF, super.baseUrl)
                    .putString(DEFAULT_BASE_URL_PREF, super.baseUrl)
                    .apply()
            }
        }
    }
}
