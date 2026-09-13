package eu.kanade.tachiyomi.extension.all.manta

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class MantaComics :
    KeiSource(),
    ConfigurableSource {

    private val apiUrl get() = "https://" + baseUrl.toHttpUrl().host

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val supportsLatest = false

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .set("Origin", apiUrl)
        .set("Accept-Language", lang)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/manta/v1/search/series?cat=New&lang=$lang"

        return parseSearchManga(client.get(url))
    }

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/manta/v1/search/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("lang", lang)
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            } else {
                val category = filters.category
                val selected = if (category.second.isEmpty()) "tagId=288" else category.second
                val (key, value) = selected.split("=")
                addQueryParameter(key, value)
            }
        }.build()

        return parseSearchManga(client.get(url))
    }

    private fun parseSearchManga(response: Response) = response.parseAs<MantaResponse<List<Series<Title>>>>().data
        .map { it.toSManga(lang) }
        .let { MangasPage(it, false) }

    // =========================== Related Manga ============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val seriesUrl = "$apiUrl/front/v1/series/${manga.url}?lang=$lang"
        val related = client.get(seriesUrl).parseAs<MantaResponse<RelatedSeries>>().data
        return related.relatedSeriesList.map { it.toSManga(lang) }
    }

    // =========================== Manga Details / Chapters ============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val host = url.host
        val baseHost = baseUrl.toHttpUrl().host
        if (host != baseHost && host != "www.$baseHost") return null

        val segments = url.pathSegments
        val seriesIdx = segments.indexOf("series")
        if (seriesIdx == -1 || seriesIdx >= segments.lastIndex) return null

        val id = segments[seriesIdx + 1]
        return SManga.create().apply {
            this.url = id
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val seriesUrl = "$apiUrl/front/v1/series/${manga.url}?lang=$lang"
        val response = client.get(seriesUrl, headers)
        val mantaResponse = response.parseAs<MantaResponse<Series<Details>>>()
        val series = mantaResponse.data
        val details = series.data

        val mangaDetails = SManga.create().apply {
            description = details.asString(lang)
            genre = details.tags.joinToString { it.asString(lang) }
            artist = details.artists.joinToString()
            author = details.authors.joinToString()
            status = when (details.isCompleted) {
                true -> SManga.COMPLETED
                else -> SManga.ONGOING
            }
            initialized = true
        }

        val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED, true)
        val chapterList = series.episodes.orEmpty()
            .filter { showLocked || it.lockData?.isLocked != true }
            .map {
                SChapter.create().apply {
                    name = it.asString(lang)
                    url = it.id.toString()
                    date_upload = it.timestamp
                    chapter_number = it.ord.toFloat()
                }
            }.reversed()

        return SMangaUpdate(mangaDetails, chapterList)
    }

    // ============================= Page List ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$apiUrl/front/v1/episodes/${chapter.url}?lang=$lang".toHttpUrl()

        var token = client.cookieJar.loadForRequest(url)
            .firstOrNull { it.name == "token" }?.value
        if (token != null) {
            val headers = headersBuilder()
                .set("Authorization", "Bearer $token")
                .build()

            token = client.get("$apiUrl/manta/v1/login", headers)
                .parseAs<MantaResponse<Token>>().data.token
        }

        val headers = if (token != null) {
            headersBuilder()
                .set("Authorization", "Bearer $token")
                .build()
        } else {
            headers
        }

        client.get(url, headers).use { response ->
            val images = response.parseAs<MantaResponse<Episode>>().data.cutImages.orEmpty()
                .map { it.toString() }
            // Locked chapters return real preview images followed by "just-black.jpg" placeholders.
            val previewImages = images.filterNot { it.contains(BLACK_IMAGE_MARKER) }

            val pages = previewImages.mapIndexed { idx, img ->
                Page(idx, "", img)
            }.toMutableList()

            if (previewImages.size < images.size) {
                pages.add(Page(pages.size, "", previewEndImageUrl))
            }
            return pages
        }
    }

    private val previewEndImageUrl: String
        get() {
            val heading: String
            val subHeading: String
            if (lang == "es") {
                heading = "Fin de la vista previa del capítulo"
                subHeading = "Compra e inicia sesión con WebView y borra la caché del capítulo para leer completo"
            } else {
                heading = "End of chapter preview"
                subHeading = "Purchase and login via webview and clear chapter cache to read full"
            }
            return "https://fakeimg.ryd.tools/1500x2126/f4f4f4/222222/".toHttpUrl().newBuilder()
                .addQueryParameter("text", "$heading\n\n$subHeading")
                .addQueryParameter("font_size", "58")
                .build()
                .toString()
        }

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = if (lang == "es") "Mostrar capítulos de pago/bloqueados" else "Show paid/locked chapters"
            summary = if (lang == "es") {
                "Muestra los capítulos de pago o bloqueados en la lista de capítulos"
            } else {
                "Show paid or locked chapters in the chapter list"
            }
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    // ============================== Filters ===============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Filters are ignored when searching"),
        Filter.Separator(),
        Category(lang),
    )

    // ============================= Utilities ==============================

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/episodes/${chapter.url}"

    companion object {
        private const val PREF_SHOW_LOCKED = "show_locked_chapters"
        private const val BLACK_IMAGE_MARKER = "just-black.jpg"
    }
}
