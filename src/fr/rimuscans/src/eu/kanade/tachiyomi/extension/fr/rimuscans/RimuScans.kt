package eu.kanade.tachiyomi.extension.fr.rimuscans

import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class RimuScans : HttpSource(), ConfigurableSource {

    override val name = "Rimu Scans"

    override val baseUrl = "https://rimuscans.com"

    override val lang = "fr"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "application/json, text/plain, */*")

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .addQueryParameter("sortBy", "popular")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = mangaPageParse(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", "24")
            .addQueryParameter("sortBy", "latest")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = mangaPageParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            }
            filters.forEach { filter ->
                when (filter) {
                    is OrderByFilter -> addQueryParameter("sortBy", filter.toUriPart())
                    is GenreFilter -> {
                        filter.state
                            .filter { it.state }
                            .forEach { addQueryParameter("genres", it.name) }
                    }
                    is StatusFilter -> {
                        if (filter.state != 0) {
                            addQueryParameter("status", filter.toUriPart())
                        }
                    }
                    is TypeFilter -> {
                        if (filter.state != 0) {
                            addQueryParameter("type", filter.toUriPart())
                        }
                    }
                    else -> {}
                }
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = mangaPageParse(response)

    private fun mangaPageParse(response: Response): MangasPage {
        val result = response.parseAs<MangaListDto>()
        val mangas = result.mangas.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, result.pagination.hasNextPage)
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = (baseUrl + manga.url).toHttpUrl().pathSegments.last()
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaDetailsWrapperDto>()
        return result.manga.toSManga(baseUrl)
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDetailsWrapperDto>()
        val showPremium = preferences.getBoolean(SHOW_PREMIUM_KEY, SHOW_PREMIUM_DEFAULT)

        return result.manga.chapters
            .filter { showPremium || it.type != "PREMIUM" }
            .map { it.toSChapter(result.manga.slug) }
            .reversed()
    }

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val segments = (baseUrl + chapter.url).toHttpUrl().pathSegments
        val mangaSlug = segments[1]
        val chapterNumber = segments.last()

        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder()
            .addQueryParameter("slug", mangaSlug)
            .fragment(chapterNumber)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<MangaDetailsWrapperDto>()
        val chapterNumber = response.request.url.fragment?.toDoubleOrNull()
            ?: throw Exception("Chapter number not found in request")

        val chapter = result.manga.chapters.find { it.number == chapterNumber }
            ?: throw Exception("Chapter not found")

        if (chapter.type == "PREMIUM" && chapter.images.isEmpty()) {
            throw Exception("This chapter is premium. Please read it on the website.")
        }

        return chapter.images.map { image ->
            Page(image.order - 1, imageUrl = baseUrl + image.url)
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // Filters

    override fun getFilterList() = getFilters()

    // Preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = SHOW_PREMIUM_KEY
            title = "Show premium chapters"
            summary = "Show paid chapters (identified by 🔒) in the list."
            setDefaultValue(SHOW_PREMIUM_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val SHOW_PREMIUM_KEY = "show_premium_chapters"
        private const val SHOW_PREMIUM_DEFAULT = false
    }
}
