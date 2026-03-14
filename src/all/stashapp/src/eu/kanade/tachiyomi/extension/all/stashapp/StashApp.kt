package eu.kanade.tachiyomi.extension.all.stashapp

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.UnmeteredSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import kotlin.collections.mapNotNull
import kotlin.collections.orEmpty
import kotlin.time.Instant

class StashApp :
    HttpSource(),
    ConfigurableSource,
    UnmeteredSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val name = "StashApp"

    override val lang = "all"

    override val supportsLatest = true

    override val baseUrl: String
        get() = preferences.getString(BASE_URL_KEY, BASE_URL_DEFAULT)!!.trim()

    private inline fun <reified T> graphQLRequest(query: String, operationName: String, variables: T): Request {
        val headers = headersBuilder()
            .add("Content-Type", "application/json")
            .add("Accept", "application/graphql-response+json, application/json")
            .build()

        val body = GraphQLRequest(
            query = query.trimIndent(),
            operationName = operationName,
            variables = variables,
        ).toJsonString().toRequestBody("application/json".toMediaType())

        return POST("$baseUrl/graphql", headers, body)
    }

    /**
     * @param sort https://github.com/stashapp/stash/blob/v0.30.1/pkg/sqlite/gallery.go#L773
     */
    private fun mangaBriefRequest(page: Int, q: String?, sort: String?, direction: SortDirectionEnum?): Request = graphQLRequest(
        query = MANGA_BRIEF_QUERY,
        operationName = "MangaBrief",
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
        val galleries = response.parseAs<GraphQLResponse<MangaBriefData>>()
            .data
            ?.findGalleries
            ?.galleries
            ?: return MangasPage(emptyList(), false)

        val mangas = galleries.mapNotNull { gallery -> gallery.toMangaBrief() }

        return MangasPage(
            mangas = mangas,
            hasNextPage = mangas.size >= MANGA_BRIEF_PER_PAGE,
        )
    }

    private fun Gallery.toMangaBrief(): SManga? {
        val id = id?.takeIf(String::isNotBlank) ?: return null

        return SManga.create().apply {
            url = toAbsoluteUrl("/galleries/$id")
            this.title = toTitle()
            thumbnail_url = cover?.toThumbnailUrl()
        }
    }

    override fun popularMangaRequest(page: Int) = mangaBriefRequest(page, null, "rating", SortDirectionEnum.DESC)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaBrief(response)

    // TODO support getFilterList
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = mangaBriefRequest(page, query, "path", SortDirectionEnum.ASC)

    override fun searchMangaParse(response: Response): MangasPage = parseMangaBrief(response)

    override fun latestUpdatesRequest(page: Int): Request = mangaBriefRequest(page, null, "updated_at", SortDirectionEnum.DESC)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaBrief(response)

    override fun mangaDetailsRequest(manga: SManga): Request = graphQLRequest(
        query = MANGA_DETAILS_QUERY,
        operationName = "MangaDetails",
        variables = MangaDetailsVariables(id = urlLast(manga.url)),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val gallery = response.parseAs<GraphQLResponse<MangaDetailsData>>()
            .data!!
            .findGallery

        return gallery.toMangaDetails()!!
    }

    private fun Gallery.toMangaDetails(): SManga? {
        val id = id?.takeIf(String::isNotBlank) ?: return null

        return SManga.create().apply {
            url = toAbsoluteUrl("/galleries/$id")
            title = toTitle()
            artist = photographer?.takeIf(String::isNotBlank)
            author = artist
            description = details?.takeIf(String::isNotBlank)
            genre = tags
                .orEmpty()
                .mapNotNull(Tag::name)
                .filter(String::isNotBlank)
                .joinToString(", ")
                .takeIf(String::isNotBlank)
            status = SManga.UNKNOWN
            thumbnail_url = cover?.toThumbnailUrl()
            update_strategy = UpdateStrategy.ALWAYS_UPDATE
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga): Request = graphQLRequest(
        query = CHAPTER_LIST_QUERY,
        operationName = "ChapterList",
        variables = ChapterListVariables(id = urlLast(manga.url)),
    )

    override fun chapterListParse(response: Response): List<SChapter> {
        val gallery = response.parseAs<GraphQLResponse<ChapterListData>>()
            .data
            ?.findGallery
            ?: return emptyList()

        val id = gallery.id!!

        return listOf(
            SChapter.create().apply {
                url = toAbsoluteUrl("/galleries/$id")
                name = "Chapter"
                date_upload = gallery.createdAt?.takeIf(String::isNotBlank)?.let(::parseRFC3339Millis) ?: 0L
                chapter_number = 1f
                scanlator = gallery.photographer?.takeIf(String::isNotBlank)
            },
        )
    }

    override fun pageListRequest(chapter: SChapter): Request = graphQLRequest(
        query = PAGE_LIST_QUERY,
        operationName = "PageList",
        variables = PageListVariables(id = urlLast(chapter.url).toInt()),
    )

    override fun pageListParse(response: Response): List<Page> {
        val images = response.parseAs<GraphQLResponse<PageListData>>()
            .data
            ?.findImages
            ?.images
            ?: return emptyList()

        return images.mapIndexedNotNull { index, image -> image.toPage(index) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Accept", "image/*")
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun getMangaUrl(manga: SManga): String = manga.url

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    // TODO override getFilterList

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = BASE_URL_KEY
            title = "Server URL"
            summary = "Example: http://localhost:9999"
            setDefaultValue(BASE_URL_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString("baseUrl", newValue as String).commit()
            }
        }.let(screen::addPreference)
    }

    private fun toAbsoluteUrl(path: String): String = when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        path.startsWith("/") -> "$baseUrl$path"
        else -> "$baseUrl/$path"
    }

    private fun urlLast(url: String): String = url.substringBefore('?')
        .substringBefore('#')
        .let(::pathLast)

    private fun pathLast(path: String): String = path.trimEnd('/', '\\')
        .substringAfterLast('/')
        .substringAfterLast('\\')

    private fun parseRFC3339Millis(value: String): Long {
        // ISO 8601 covers RFC3339
        return Instant.parseOrNull(value)?.toEpochMilliseconds() ?: 0L
    }

    private fun Gallery.toTitle(): String = title?.takeIf(String::isNotBlank)
        ?: folder?.path?.takeIf(String::isNotBlank)?.let(::pathLast)
        ?: "Untitled"

    private fun Image.toThumbnailUrl(): String? {
        if (visualFiles.firstOrNull()?.isImage() != true) return null

        return paths
            ?.thumbnail
            ?.takeIf { it.isNotBlank() }
    }

    private fun Image.toPage(index: Int): Page? {
        val id = id?.takeIf(String::isNotBlank) ?: return null
        return Page(index = index, url = toAbsoluteUrl("/images/$id"), imageUrl = toAbsoluteUrl("/image/$id/image"))
    }

    private fun VisualFile.isImage(): Boolean = __typename == "ImageFile"
}
