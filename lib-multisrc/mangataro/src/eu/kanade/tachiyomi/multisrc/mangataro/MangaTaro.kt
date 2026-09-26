package eu.kanade.tachiyomi.multisrc.mangataro

import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.IOException
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

abstract class MangaTaro : KeiSource() {

    override val supportsLatest = true

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int) = browseManga(page, "", SortFilter.popular)

    // ========================== Latest =========================
    override suspend fun getLatestUpdates(page: Int) = browseManga(page, "", SortFilter.latest)

    // ========================== Search =========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = if (
        query.isNotBlank() &&
        filters.firstInstanceOrNull<SearchWithFilters>()?.state == false
    ) {
        querySearch(query)
    } else {
        browseManga(page, query, filters)
    }

    private suspend fun querySearch(query: String): MangasPage {
        val body = SearchQueryPayload(
            query = query.trim(),
            limit = 25,
        ).toJsonString().toRequestBody(JSON_MEDIA_TYPE)

        val data = client.post("$baseUrl/auth/search", body).parseAs<SearchQueryResponse>().results

        val mangas = data.filter { it.type != "Novel" }
            .map {
                SManga.create().apply {
                    url = MangaUrl(it.id.toString(), it.slug).toJsonString()
                    title = it.title.unescapeHtml()
                    thumbnail_url = it.thumbnail
                    description = it.description.unescapeHtml()
                    status = when (it.status) {
                        "Ongoing" -> SManga.ONGOING
                        "Completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
            }

        return MangasPage(
            mangas = mangas,
            hasNextPage = false,
        )
    }

    protected suspend fun fetchBrowsePage(page: Int, query: String, filters: FilterList): List<BrowseManga> {
        val body = SearchPayload(
            page = page,
            search = query.trim(),
            years = filters.firstInstanceOrNull<YearFilter>()
                ?.selected.let(::listOfNotNull),
            genres = filters.firstInstanceOrNull<TagFilter>()
                ?.checked.orEmpty(),
            types = filters.firstInstanceOrNull<TypeFilter>()
                ?.selected.let(::listOfNotNull),
            statuses = filters.firstInstanceOrNull<StatusFilter>()
                ?.selected.let(::listOfNotNull),
            sort = filters.firstInstance<SortFilter>().selected,
            genreMatchMode = filters.firstInstance<TagFilterMatch>().selected,
        ).toJsonString().toRequestBody(JSON_MEDIA_TYPE)

        return client.post("$baseUrl/wp-json/manga/v1/load", body).parseAs()
    }

    private suspend fun browseManga(page: Int, query: String, filters: FilterList): MangasPage {
        val data = fetchBrowsePage(page, query, filters)

        val mangas = data.filter { it.type != "Novel" && it.url.isNotBlank() }
            .map(::browseMangaToSManga)

        return MangasPage(
            mangas = mangas,
            hasNextPage = data.size == 24,
        )
    }

    protected fun browseMangaToSManga(manga: BrowseManga) = SManga.create().apply {
        url = MangaUrl(id = manga.id, slug = manga.url.toSlug()).toJsonString()
        title = manga.title.unescapeHtml()
        thumbnail_url = manga.cover
        description = manga.description.unescapeHtml()
        status = when (manga.status) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val slug = url.toString().toSlug()
        val document = client.get("$baseUrl/manga/$slug").asJsoup()

        val id = document.body().dataset()["manga-id"]!!
        val statusElement = document.select(".capitalize").firstOrNull {
            val text = it.text().lowercase()
            text == "ongoing" || text == "completed"
        }
        val status = when (statusElement?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        if (document.select(".capitalize").any { it.text().contains("Novel") }) {
            throw Exception("Novels are not supported")
        }

        return fetchMangaDetails(id, status)
    }

    // ========================= Filters =========================
    override fun getFilterList(data: JsonElement?) = FilterList(
        SearchWithFilters(),
        Filter.Header("If unchecked, all filters will be ignored with search query"),
        Filter.Header("But will give more relevant results"),
        Filter.Separator(),
        SortFilter(),
        TypeFilter(),
        StatusFilter(),
        YearFilter(),
        TagFilter(),
        TagFilterMatch(),
    )

    // ========================= Details =========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) fetchMangaDetails(manga.url.parseAs<MangaUrl>().id, manga.status) else manga
        }
        val chapterList = async { if (fetchChapters) fetchChapterList(manga) else chapters }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun fetchMangaDetails(id: String, status: Int): SManga {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("wp-json/wp/v2/manga")
            addPathSegment(id)
            addQueryParameter("_embed", null)
        }.build()

        val data = client.get(url).parseAs<MangaDetails>()

        return SManga.create().apply {
            this.url = MangaUrl(data.id.toString(), data.slug).toJsonString()
            title = data.title.rendered.unescapeHtml()
            description = Jsoup.parseBodyFragment(data.content.rendered).wholeText().unescapeHtml()
            genre = buildSet {
                addAll(data.embedded.getTerms("post_tag"))
                if (listOf("Manhwa", "Manhua", "Manga").none { this.contains(it) }) {
                    add(data.type)
                }
            }.joinToString()
            author = data.embedded.getTerms("manga_author").joinToString()
            this.status = status
            thumbnail_url = data.embedded.featuredMedia.firstOrNull()?.url
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.parseAs<MangaUrl>().slug

        return "$baseUrl/manga/$slug"
    }

    // ======================== Chapters =========================
    private suspend fun fetchChapterList(manga: SManga): List<SChapter> {
        val timestamp = System.currentTimeMillis() / 1000
        val token = md5(
            "${timestamp}mng_ch_${isoDateFormatter.format(Instant.now())}",
        ).substring(0, 16)

        val dto = manga.url.parseAs<MangaUrl>()

        val url = "$baseUrl/auth/manga-chapters".toHttpUrl().newBuilder().apply {
            addQueryParameter("manga_id", dto.id)
            addQueryParameter("offset", "0")
            addQueryParameter("limit", "9999")
            addQueryParameter("order", "DESC")
            addQueryParameter("_t", token)
            addQueryParameter("_ts", timestamp.toString())
            dto.group?.let {
                addQueryParameter("group_id", it.toString())
            }
        }.build()

        val data = client.get(url).parseAs<ChapterList>()
        countViews(dto.id)

        val placeholders = listOf(null, "", "N/A", "—")
        var hasScanlator = false

        val chapters = data.chapters.filter {
            it.language.equals(lang, ignoreCase = true)
        }.map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.url.removeSuffix("/"))
                name = buildString {
                    append("Chapter ")
                    append(it.chapter)
                    it.title?.takeIf { it !in placeholders }?.let { title ->
                        append(": ", title.unescapeHtml())
                    }
                }
                it.groupName.let { group ->
                    if (group !in placeholders) {
                        scanlator = group
                        hasScanlator = true
                    }
                }
                date_upload = it.date.parseRelativeDate()
            }
        }

        if (hasScanlator) {
            chapters.onEach { it.scanlator = it.scanlator ?: "​" } // Insert zero-width space
        }

        return chapters
    }

    // ========================== Pages ==========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = getChapterUrl(chapter).removeSuffix("/").toHttpUrl()
            .pathSegments.last()
            .substringAfterLast("-")

        val url = "$baseUrl/auth/chapter-content".toHttpUrl().newBuilder()
            .addQueryParameter("chapter_id", chapterId)
            .build()

        return client.get(url).parseAs<Pages>().images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    // ========================= Helpers =========================
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun String.toSlug() = toHttpUrl().let { url ->
        val path = url.pathSegments.filter(String::isNotBlank)

        if ((path.size == 2 && path[0] == "manga") || (path.size == 3 && path[0] == "read")) {
            path[1]
        } else {
            throw Exception("Expected manga or read path, got $this")
        }
    }

    private fun String.parseRelativeDate(): Long {
        val calendar = Calendar.getInstance()
        val (amount, unit) = relativeDateRegex.matchEntire(this)?.destructured
            ?: return 0L

        when (unit) {
            "second" -> calendar.add(Calendar.SECOND, -amount.toInt())
            "minute" -> calendar.add(Calendar.MINUTE, -amount.toInt())
            "hour" -> calendar.add(Calendar.HOUR, -amount.toInt())
            "day" -> calendar.add(Calendar.DAY_OF_YEAR, -amount.toInt())
            "week" -> calendar.add(Calendar.WEEK_OF_YEAR, -amount.toInt())
            "month" -> calendar.add(Calendar.MONTH, -amount.toInt())
            "year" -> calendar.add(Calendar.YEAR, -amount.toInt())
        }

        return calendar.timeInMillis
    }

    private fun countViews(postId: String) {
        val payload = """{"post_id":"$postId"}"""
            .toRequestBody(JSON_MEDIA_TYPE)
        val url = "$baseUrl/wp-json/pviews/v1/increment/"
        val request = POST(url, headers, payload)

        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onResponse(call: okhttp3.Call, response: Response) {
                        response.closeQuietly()
                    }

                    override fun onFailure(call: okhttp3.Call, e: IOException) {
                        Log.e(name, "Failed to count views", e)
                    }
                },
            )
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val isoDateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHH", Locale.US).withZone(ZoneOffset.UTC)

        private val relativeDateRegex = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""")
    }
}

// Map groups by language
abstract class MangaTaroGroup :
    MangaTaro(),
    ConfigurableSource {

    abstract val groups: List<Long>

    override val supportsLatest: Boolean = false

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val libraryCached: MutableList<SManga> = mutableListOf()

    private val userGroups: List<Long> by lazy {
        val myGroups = preferences.getString(GROUP_PREF, "")?.takeIf(String::isNotBlank)
            ?: return@lazy emptyList()

        return@lazy try {
            myGroups.split(",").map { it.trim().toLong() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    val groupsMapped: List<Long> by lazy { (groups + userGroups).distinct() }

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (libraryCached.isNotEmpty()) return MangasPage(libraryCached, hasNextPage = false)

        return client.get("$baseUrl/auth/groups/${groupsMapped[page - 1]}/titles?page=$page")
            .parseAs<ProjectList>().titles
            .map(ProjectList.MangaDto::toSManga)
            .let { MangasPage(it, hasNextPage = page < groupsMapped.size) }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (libraryCached.isEmpty()) {
            do {
                val mangasPage = getPopularManga(page)
                libraryCached += mangasPage.mangas
            } while (mangasPage.hasNextPage)
        }
        return MangasPage(libraryCached.filter { it.title.contains(query, ignoreCase = true) }, false)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = GROUP_PREF
            title = "My groups"
            summary = "Add the group number to view the list of projects"
            dialogMessage = buildString {
                appendLine("* Visit '$baseUrl/groups' and add the group number here.")
                appendLine("\t\tEx.: $baseUrl/groups/50. The group number is 50.")
                appendLine("\n* Use a comma to add multiple groups.")
                appendLine("\t\tEx.: 50, 60, 12")
                appendLine("\n⚠  Groups added here may not appear due to typos. Also, check if the group actually publishes in your language.")
            }

            setDefaultValue(groups.joinToString())

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }.let(screen::addPreference)
    }

    companion object {
        private const val GROUP_PREF = "groupPref"
        private const val RESTART_APP = "Restart app to apply new setting."
    }
}

internal fun String.unescapeHtml(): String {
    var decoded = this
    do {
        val previous = decoded
        decoded = Parser.unescapeEntities(decoded, false)
    } while (decoded != previous)
    return decoded
}
