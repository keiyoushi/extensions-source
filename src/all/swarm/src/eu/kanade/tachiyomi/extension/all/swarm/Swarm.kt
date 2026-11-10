package eu.kanade.tachiyomi.extension.all.swarm

import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
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
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.aesCbc
import keiyoushi.utils.aesEncrypt
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.string
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.SecureRandom

@Source
abstract class Swarm :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val showAdult get() = preferences.getBoolean(PREF_ADULT, false)

    private val rscHeaders get() = headers.newBuilder().set("rsc", "1").build()

    // Chapter languages as the site's API names them
    private val siteLangs
        get() = when (lang) {
            "es-419" -> setOf("es-la")
            "pt-BR" -> setOf("pt-br")
            "ko" -> setOf("kr", "ko")
            else -> setOf(lang)
        }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * POPULAR_LIMIT
        val url = "$baseUrl/api/analytics/popular".toHttpUrl().newBuilder()
            .addQueryParameter("window", "7d")
            .addQueryParameter("limit", POPULAR_LIMIT.toString())
            .addQueryParameter("offset", offset.toString())
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<MangaListDto>()
        // The site's popular list has no content rating param
        val mangas = result.data.filter { showAdult || it.nsfw != true }.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, (result.total ?: 0) > offset + result.data.size)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/latest".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "50")
            .addQueryParameter("filter", "new")
            .addQueryParameter("nsfw", showAdult.toString())
            .addQueryParameter("suggestive", showAdult.toString())
            .addQueryParameter("page", page.toString())
            .build()
        val result = client.get(url, Fetch.XHR).parseAs<LatestDto>()
        return MangasPage(result.data.map { it.toSManga(baseUrl) }, result.hasMore)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val isSearch = query.isNotBlank()
        val limit = if (isSearch) SEARCH_LIMIT else BROWSE_LIMIT
        val offset = (page - 1) * limit
        val builder = "$baseUrl/api".toHttpUrl().newBuilder()
            .addPathSegments(if (isSearch) "comic/search" else "manga/filtered")
            .apply { if (isSearch) addQueryParameter("title", query.trim()) }
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
        filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(builder) }
        val result = client.get(builder.build(), Fetch.XHR).parseAs<MangaListDto>()
        // Pages can come back short, and with a content rating set the browse total is just offset + limit + 1
        val hasNextPage = result.data.isNotEmpty() && (result.total ?: 0) > offset + result.data.size
        return MangasPage(result.data.map { it.toSManga(baseUrl) }, hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "comic" || segments[1].isBlank()) return null
        val manga = SManga.create().apply { this.url = segments[1] }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comic/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        // Without the bare `_rsc` param the site answers an RSC fetch with a 307 to it
        val url = getMangaUrl(manga).toHttpUrl().newBuilder().addQueryParameter("_rsc", null).build()
        val page = client.get(url, rscHeaders, Fetch.XHR)
            .extractNextJs<ComicPageDto>()
            ?: throw Exception("Title not found on Swarm")
        return SMangaUpdate(page.manga.toSManga(baseUrl), chapterList(page.chapters).map { it.toSChapter(manga.url) })
    }

    // The comic page carries the site's chapter list: the newest 10000 uploads across all languages
    private fun chapterList(all: List<ChapterDto>): List<ChapterDto> {
        val langs = siteLangs
        val blocked = commaList(PREF_BLOCKED_GROUPS, "")
        val uploads = all
            .filter { it.lang in langs }
            .filterNot { upload -> blocked.any { it.equals(upload.group, true) || it.equals(upload.uploader, true) } }
            .sortedByDescending { it.number.toFloatOrNull() }
        // The site's pick among a number's uploads is the lowest rank letter
        val sitePick = compareBy<ChapterDto>({ it.rank ?: "~" }, { it.copyIndex })
        return when (preferences.getString(PREF_CHAPTER_LIST, ONE_PER_GROUP)) {
            ALL_UPLOADS -> uploads
            ONE_PER_CHAPTER -> {
                val preferred = commaList(PREF_PREFERRED_GROUPS, DEFAULT_PREFERRED_GROUPS)
                val preferredRank = { upload: ChapterDto ->
                    preferred.indexOfFirst { it.equals(upload.group, true) || it.equals(upload.uploader, true) }
                        .takeIf { it >= 0 } ?: preferred.size
                }
                uploads.groupBy { it.number }.values.map { same -> same.minWith(compareBy(preferredRank).then(sitePick)) }
            }
            else -> {
                val picks = uploads.groupBy { it.number to it.group }.values
                    .mapTo(HashSet()) { same -> same.minWith(sitePick).id }
                uploads.filter { it.id in picks }
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("comic")
            .addPathSegment(chapter.memo["slug"]!!.string)
            .addPathSegment("chapter")
            .addPathSegment(chapter.memo["number"]!!.string)
        // The site's reader picks the upload by its rank letter, a bare query like "?b"; "a" is the default
        chapter.memo["rank"]?.stringOrNull?.takeIf { it != "a" }?.let(url::encodedQuery)
        return url.build().toString()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val payload = PagesPayloadDto(encryptPayload(PageRequestDto(chapter.url).toJsonString()))
        val images = client.post("$baseUrl/api/sp", Fetch.XHR.from(getChapterUrl(chapter)), payload.toJsonRequestBody())
            .parseAs<PagesDto>().chapter.data
        // The site's placeholder for an upload it has no pages for
        if (images.all { it.endsWith("/noah/fall.png") }) return emptyList()
        return images.mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private val secureRandom = SecureRandom()

    // The site's client encrypts the page request: AES-CBC with SHA-256 of a constant from its bundle, "ivHex:ciphertextHex"
    private fun encryptPayload(json: String): String {
        val iv = ByteArray(16).also(secureRandom::nextBytes)
        val encrypted = json.encodeToByteArray().aesEncrypt(aesCbc(PAYLOAD_SECRET.sha256(), iv, encrypt = true))
        return "${iv.toHexString()}:${encrypted.toHexString()}"
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val genres = async { client.get("$baseUrl/api/manga/genres", Fetch.XHR).parseAs<GenresDto>().genres }
        val types = async { client.get("$baseUrl/api/manga/types", Fetch.XHR).parseAs<TypesDto>().types }
        FilterDataDto(genres.await(), types.await()).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val fetched = data?.parseAs<FilterDataDto>()
        return FilterList(
            buildList {
                add(Filter.Header("Sort by is ignored with a text search"))
                add(SortFilter())
                add(ContentFilter(showAdult))
                fetched?.genres?.takeIf { it.isNotEmpty() }?.let { add(GenreFilter(it)) }
                fetched?.types?.takeIf { it.isNotEmpty() }?.let { add(TypeFilter(it)) }
                add(TextParamFilter("Tags (comma-separated, exact names)", "tags"))
                add(TextParamFilter("Started from year", "yearStartMin"))
                add(TextParamFilter("Started up to year", "yearStartMax"))
                add(TextParamFilter("Ended from year", "yearEndedMin"))
                add(TextParamFilter("Ended up to year", "yearEndedMax"))
                add(TextParamFilter("Minimum chapters", "minChapters"))
            },
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_ADULT
            title = "Show 18+ content"
            summary = "In Popular and Latest, and as the default of the Content filter, like the site's content rating setting"
            setDefaultValue(false)
        }.let(screen::addPreference)

        val preferredGroups = EditTextPreference(screen.context).apply {
            key = PREF_PREFERRED_GROUPS
            title = "Preferred uploaders"
            summary = "Uploader names, comma-separated. One per chapter shows the first listed one that has the chapter. " +
                "Default: $DEFAULT_PREFERRED_GROUPS, the site's official releases"
            dialogTitle = "Preferred uploaders"
            setDefaultValue(DEFAULT_PREFERRED_GROUPS)
            setEnabled(preferences.getString(PREF_CHAPTER_LIST, ONE_PER_GROUP) == ONE_PER_CHAPTER)
        }

        ListPreference(screen.context).apply {
            key = PREF_CHAPTER_LIST
            title = "Chapter list"
            entries = arrayOf("All uploads, like the site's list", "One per uploader", "One per chapter")
            entryValues = arrayOf(ALL_UPLOADS, ONE_PER_GROUP, ONE_PER_CHAPTER)
            summary = "%s"
            setDefaultValue(ONE_PER_GROUP)
            setOnPreferenceChangeListener { _, newValue ->
                preferredGroups.setEnabled(newValue == ONE_PER_CHAPTER)
                true
            }
        }.let(screen::addPreference)

        screen.addPreference(preferredGroups)

        EditTextPreference(screen.context).apply {
            key = PREF_BLOCKED_GROUPS
            title = "Blocked uploaders"
            summary = "Uploader names, comma-separated. Their uploads are left out of the chapter list"
            dialogTitle = "Blocked uploaders"
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    private fun commaList(key: String, default: String) = preferences.getString(key, default).orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

    companion object {
        private const val POPULAR_LIMIT = 100
        private const val SEARCH_LIMIT = 20
        private const val BROWSE_LIMIT = 50
        private const val PAYLOAD_SECRET = "e3bca6f9a7e64e369ddc450bb7ff295b3d78e7d4fcb148c10d10ae9385476a5f"
        private const val PREF_ADULT = "pref_show_adult"
        private const val PREF_CHAPTER_LIST = "pref_chapter_list"
        private const val PREF_PREFERRED_GROUPS = "pref_preferred_groups"
        private const val PREF_BLOCKED_GROUPS = "pref_blocked_groups"
        private const val DEFAULT_PREFERRED_GROUPS = "official"
        private const val ALL_UPLOADS = "all"
        private const val ONE_PER_GROUP = "group"
        private const val ONE_PER_CHAPTER = "chapter"
    }
}
