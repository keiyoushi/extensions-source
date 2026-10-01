package eu.kanade.tachiyomi.extension.all.stashapp

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseGraphQLAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import okhttp3.Request
import kotlin.time.Instant

@Source
abstract class StashApp :
    KeiSource(),
    ConfigurableSource,
    UnmeteredSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun Headers.Builder.configureHeaders(): Headers.Builder = apply {
        preferences.getString(PREF_API_KEY, null)
            ?.takeIf(String::isNotBlank)
            ?.let { add("ApiKey", it) }
    }

    private val graphQlHeaders: Headers
        get() = headers.newBuilder()
            .add("Accept", "application/graphql-response+json, application/json")
            .build()

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaBrief(page, null, "rating", SortDirectionEnum.DESC)

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaBrief(page, null, "updated_at", SortDirectionEnum.DESC)

    // ============================== Search ===============================

    // TODO support getFilterList
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaBrief(page, query, "path", SortDirectionEnum.ASC)

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val id = urlLast(manga.url)

        val details = if (fetchDetails) {
            async {
                client.post(
                    url = "$baseUrl/graphql",
                    headers = graphQlHeaders,
                    body = graphQLBody(
                        operationName = "MangaDetails",
                        query = MANGA_DETAILS_QUERY,
                        variables = MangaDetailsVariables(id = id),
                    ),
                ).parseGraphQLAs<MangaDetailsData>().findGallery.toMangaDetails(baseUrl)!!
            }
        } else {
            null
        }

        val chapterList = if (fetchChapters) {
            async { getChapterList(id) }
        } else {
            null
        }

        SMangaUpdate(
            manga = details?.await() ?: manga,
            chapters = chapterList?.await() ?: chapters,
        )
    }

    // ============================= Chapters ==============================

    private suspend fun getChapterList(id: String): List<SChapter> {
        val gallery = client.post(
            url = "$baseUrl/graphql",
            headers = graphQlHeaders,
            body = graphQLBody(
                operationName = "ChapterList",
                query = CHAPTER_LIST_QUERY,
                variables = ChapterListVariables(id = id),
            ),
        ).parseGraphQLAs<ChapterListData>().findGallery

        val galleryId = gallery.id!!

        return listOf(
            SChapter.create().apply {
                url = toAbsoluteUrl(baseUrl, "/galleries/$galleryId")
                name = "Chapter"
                date_upload = Instant.tryParse(gallery.createdAt)
                chapter_number = 1f
                scanlator = gallery.photographer?.takeIf(String::isNotBlank)
            },
        )
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val images = client.post(
            url = "$baseUrl/graphql",
            headers = graphQlHeaders,
            body = graphQLBody(
                operationName = "PageList",
                query = PAGE_LIST_QUERY,
                variables = PageListVariables(id = urlLast(chapter.url).toInt()),
            ),
        ).parseGraphQLAs<PageListData>()
            .findImages
            .images
            ?: return emptyList()

        return images.mapIndexedNotNull { index, image -> image.toPage(index, baseUrl) }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Accept", "image/*")
        .build()

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
    private suspend fun getMangaBrief(page: Int, q: String?, sort: String?, direction: SortDirectionEnum?): MangasPage {
        val galleries = client.post(
            url = "$baseUrl/graphql",
            headers = graphQlHeaders,
            body = graphQLBody(
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
            ),
        ).parseGraphQLAs<MangaBriefData>()
            .findGalleries
            .galleries
            ?: return MangasPage(emptyList(), false)

        val mangas = galleries.mapNotNull { gallery -> gallery.toMangaBrief(baseUrl) }

        return MangasPage(
            mangas = mangas,
            hasNextPage = mangas.size >= MANGA_BRIEF_PER_PAGE,
        )
    }
}
