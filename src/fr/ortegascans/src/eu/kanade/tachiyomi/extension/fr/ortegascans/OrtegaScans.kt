package eu.kanade.tachiyomi.extension.fr.ortegascans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferences
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class OrtegaScans :
    KeiSource(),
    ConfigurableSource {

    private val preferences = getPreferences()

    private val rscHeaders get() = headersBuilder().add("rsc", "1").build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val filters = getFilterList().apply {
            firstInstance<SortFilter>().state = 0
        }
        return getSearchMangaList(page, "", filters)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val filters = getFilterList().apply {
            firstInstance<SortFilter>().state = 2
        }
        return getSearchMangaList(page, "", filters)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sortFilter = filters.firstInstance<SortFilter>()
        val statusFilter = filters.firstInstance<StatusFilter>()
        val tagFilter = filters.firstInstance<TagFilter>()
        val minChaptersFilter = filters.firstInstance<MinChaptersFilter>()
        val maxChaptersFilter = filters.firstInstance<MaxChaptersFilter>()

        val url = "$baseUrl/api/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", "18")
            addQueryParameter("page", page.toString())
            addQueryParameter("search", query)
            addQueryParameter("tags", tagFilter.values)
            addQueryParameter("status", statusFilter.values)
            addQueryParameter("sort", sortFilter.value)
            addQueryParameter("minChapters", minChaptersFilter.value)
            addQueryParameter("isOrtegaOnly", "false")
            addQueryParameter("unreadOnly", "false")
            addQueryParameter("maxChapters", maxChaptersFilter.value)
        }.build()

        val dto = client.get(url).parseAs<SeriesResponse>()
        val mangas = dto.data.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, dto.hasMore)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get("$baseUrl/serie/${manga.mangaSlug()}", rscHeaders)
            .extractNextJs<MangaDetailsDataDto>()
            ?: throw Exception("Impossible d'extraire les détails du manga")

        val hidePremium = preferences.getBoolean(PREF_HIDE_PREMIUM, false)
        return SMangaUpdate(
            manga = dto.manga.toSManga(baseUrl),
            chapters = dto.manga.chapters
                .filter { !hidePremium || !it.isPremium }
                .map { it.toSChapter(dto.manga.slug) },
        )
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get("$baseUrl/serie/${manga.mangaSlug()}").asJsoup()
        val section = document.selectFirst("section:has(h2:containsOwn(Vous aimeriez))")
            ?: return emptyList()

        return section.select("a[href^=\"/serie/\"]").mapNotNull { element ->
            SManga.create().apply {
                url = element.absUrl("href").toHttpUrl().pathSegments[1]
                title = element.selectFirst("h3")?.text() ?: return@mapNotNull null
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/serie/${manga.mangaSlug()}"

    private fun SManga.mangaSlug(): String = if (url.startsWith("{")) url.parseAs<JsonObject>().getString("slug") else url

    private fun requireUpToDateChapterUrl(chapter: SChapter) {
        if (chapter.url.startsWith("{")) {
            throw Exception("Ancien format de chapitre détecté. Actualisez la liste des chapitres.")
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        requireUpToDateChapterUrl(chapter)
        val response = client.get(
            "$baseUrl/serie/${chapter.memo.getString("mangaSlug")}/chapter/${chapter.memo.getString("number")}",
            rscHeaders,
        )
        val dto = response.extractNextJs<PageListDto>()
            ?: throw Exception("Impossible d'extraire la liste des pages")
        return dto.images.map {
            val url = if (it.url.startsWith("http")) {
                it.url
            } else {
                "$baseUrl${it.url}"
            }
            Page(it.index, imageUrl = url)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        requireUpToDateChapterUrl(chapter)
        return "$baseUrl/serie/${chapter.memo.getString("mangaSlug")}/chapter/${chapter.memo.getString("number")}"
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        TagFilter(),
        MinChaptersFilter(),
        MaxChaptersFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PREMIUM
            title = "Masquer les chapitres premium"
            summary = "Masquer les chapitres verrouillés du site"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_HIDE_PREMIUM = "pref_hide_premium"
    }
}
