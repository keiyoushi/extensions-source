package eu.kanade.tachiyomi.extension.ru.remanga

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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ReManga :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val showLocked get() = preferences.getBoolean(SHOW_LOCKED_PREF, true)

    private val showFreeDate get() = preferences.getBoolean(SHOW_FREE_DATE_PREF, true)

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3, 1.seconds)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_PREF
            title = "Show locked chapters"
            summary = "Show paid chapters that require a purchase or premium subscription"
            setDefaultValue(true)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_FREE_DATE_PREF
            title = "Show free publication date"
            summary = "Show the date when locked chapters become free to read"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchCatalogList(page, "-views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchCatalogList(page, "-chapter_date")

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = fetchSearch(page, query)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val title = client.get(titleApiUrl(manga.url)).parseAs<TitleDetailDto>().content
        val updatedManga = if (fetchDetails) title.toSManga(manga, baseUrl) else manga
        val updatedChapters = if (fetchChapters) fetchChapters(title, manga.url) else chapters
        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() !in setOf("titles", "manga")) return null
        val dir = url.pathSegments.lastOrNull() ?: return null
        val manga = SManga.create().apply { this.url = dir }
        return client.get(titleApiUrl(dir)).parseAs<TitleDetailDto>().content.toSManga(manga, baseUrl)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/titles/".toHttpUrl().newBuilder().addPathSegment(manga.url).build().toString()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = "$baseUrl${chapter.url}".toHttpUrlOrNull()?.queryParameter("chapter")
            ?: return emptyList()
        val response = client.get(chapterApiUrl(chapterId), ensureSuccess = false)
        if (!response.isSuccessful) {
            response.close()
            return emptyList()
        }
        return response.use { it.parseAs<ChapterDetailDto>() }
            .content
            .pages
            .flatten()
            .sortedBy { it.id }
            .mapIndexed { index, page -> Page(index, imageUrl = page.link) }
    }

    private suspend fun fetchCatalogList(page: Int, ordering: String): MangasPage {
        val dto = client.get(
            "$API_URL/search/catalog/".toHttpUrl().newBuilder()
                .addQueryParameter("ordering", ordering)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("count", "30")
                .addQueryParameter("content", "manga")
                .build(),
        ).parseAs<CatalogDto>()
        return parseCatalogPage(dto)
    }

    private suspend fun fetchSearch(page: Int, query: String): MangasPage {
        val dto = client.get(
            "$API_URL/search/".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("page", page.toString())
                .addQueryParameter("count", "30")
                .addQueryParameter("field", "titles")
                .build(),
        ).parseAs<CatalogDto>()
        return parseCatalogPage(dto)
    }

    private fun parseCatalogPage(dto: CatalogDto): MangasPage {
        val mangas = dto.content.mapNotNull { it.toSManga(baseUrl) }
        return MangasPage(mangas, dto.props.page < dto.props.totalPages)
    }

    private suspend fun fetchChapters(title: TitleContentDto, dir: String): List<SChapter> {
        val branchId = title.branches.firstOrNull()?.id ?: return emptyList()
        val chapters = mutableListOf<SChapter>()
        var page = 1
        while (true) {
            val items = client.get(chaptersApiUrl(branchId, page)).parseAs<ChaptersDto>().content
            if (items.isEmpty()) break
            chapters += items.mapNotNull { item ->
                if (item.isLocked && !showLocked) return@mapNotNull null
                item.toSChapter(dir, baseUrl, showFreeDate)
            }
            if (items.size < CHAPTERS_PER_PAGE) break
            page++
        }
        return chapters
    }

    private fun titleApiUrl(dir: String): HttpUrl = "$API_URL/titles/".toHttpUrl().newBuilder().addPathSegment(dir).build()

    private fun chaptersApiUrl(branchId: Int, page: Int): HttpUrl = "$API_URL/titles/chapters/".toHttpUrl().newBuilder()
        .addQueryParameter("branch_id", branchId.toString())
        .addQueryParameter("ordering", "-index")
        .addQueryParameter("page", page.toString())
        .addQueryParameter("count", CHAPTERS_PER_PAGE.toString())
        .addQueryParameter("user_data", "1")
        .build()

    private fun chapterApiUrl(chapterId: String): String = "$API_URL/titles/chapters/$chapterId/"

    private companion object {
        const val API_URL = "https://api.remanga.org/api"
        const val CHAPTERS_PER_PAGE = 100
        const val SHOW_LOCKED_PREF = "show_locked_chapters"
        const val SHOW_FREE_DATE_PREF = "show_free_publication_date"
    }
}
