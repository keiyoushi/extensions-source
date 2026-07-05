package eu.kanade.tachiyomi.extension.all.stashapp

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLPost
import keiyoushi.utils.parseGraphQLAs
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import kotlin.time.Instant

@Source
abstract class StashApp :
    HttpSource(),
    ConfigurableSource,
    UnmeteredSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().apply {
        preferences.getString(PREF_API_KEY, null)
            ?.takeIf(String::isNotBlank)
            ?.let { add("ApiKey", it) }
    }

    private val graphQlHeaders: Headers by lazy {
        headersBuilder()
            .add("Accept", "application/graphql-response+json, application/json")
            .build()
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int) = mangaBriefRequest(page, null, "rating", SortDirectionEnum.DESC)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaBrief(response)

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = mangaBriefRequest(page, null, "updated_at", SortDirectionEnum.DESC)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaBrief(response)

    // ============================== Search ===============================

    // TODO support getFilterList
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = mangaBriefRequest(page, query, "path", SortDirectionEnum.ASC)

    override fun searchMangaParse(response: Response): MangasPage = parseMangaBrief(response)

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = graphQLPost(
        url = "$baseUrl/graphql",
        headers = graphQlHeaders,
        operationName = "MangaDetails",
        query = MANGA_DETAILS_QUERY,
        variables = MangaDetailsVariables(id = urlLast(manga.url)),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val gallery = response.parseGraphQLAs<MangaDetailsData>().findGallery

        return gallery.toMangaDetails(baseUrl)!!
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = graphQLPost(
        url = "$baseUrl/graphql",
        headers = graphQlHeaders,
        operationName = "ChapterList",
        query = CHAPTER_LIST_QUERY,
        variables = ChapterListVariables(id = urlLast(manga.url)),
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val gallery = response.parseGraphQLAs<ChapterListData>().findGallery

        val id = gallery.id!!

        return listOf(
            SChapter.create().apply {
                url = toAbsoluteUrl(baseUrl, "/galleries/$id")
                name = "Chapter"
                date_upload = gallery.createdAt?.takeIf(String::isNotBlank)?.let(::parseRFC3339Millis) ?: 0L
                chapter_number = 1f
                scanlator = gallery.photographer?.takeIf(String::isNotBlank)
            },
        )
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = graphQLPost(
        url = "$baseUrl/graphql",
        headers = graphQlHeaders,
        operationName = "PageList",
        query = PAGE_LIST_QUERY,
        variables = PageListVariables(id = urlLast(chapter.url).toInt()),
    )

    override fun pageListParse(response: Response): List<Page> {
        val images = response.parseGraphQLAs<PageListData>()
            .findImages
            .images
            ?: return emptyList()

        return images.mapIndexedNotNull { index, image -> image.toPage(index, baseUrl) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Accept", "image/*")
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    // ============================= Utilities =============================

    override fun getMangaUrl(manga: SManga): String = manga.url

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Base URL preference is now handled dynamically by the generated source
        EditTextPreference(screen.context).apply {
            key = PREF_API_KEY
            title = "API key"
            summary = "Settings | Security | Authentication | API Key"
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    /**
     * @param sort https://github.com/stashapp/stash/blob/v0.30.1/pkg/sqlite/gallery.go#L773
     */
    private fun mangaBriefRequest(page: Int, q: String?, sort: String?, direction: SortDirectionEnum?): Request = graphQLPost(
        url = "$baseUrl/graphql",
        headers = graphQlHeaders,
        operationName = "MangaBrief",
        query = MANGA_BRIEF_QUERY,
        variables = MangaBriefVariables(
            filter = FindFilterType(
                q = q,
                page = page,
                perPage = MANGA_BRIEF_PER_PAGE,
                sort = sort,
                direction = direction,
            ),
        ),
    )

    private fun parseMangaBrief(response: Response): MangasPage {
        val galleries = response.parseGraphQLAs<MangaBriefData>()
            .findGalleries
            .galleries
            ?: return MangasPage(emptyList(), false)

        val mangas = galleries.mapNotNull { gallery -> gallery.toMangaBrief(baseUrl) }

        return MangasPage(
            mangas = mangas,
            hasNextPage = mangas.size >= MANGA_BRIEF_PER_PAGE,
        )
    }

    private fun parseRFC3339Millis(value: String): Long {
        // ISO 8601 covers RFC3339
        return Instant.parseOrNull(value)?.toEpochMilliseconds() ?: 0L
    }
}
