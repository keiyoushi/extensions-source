package eu.kanade.tachiyomi.extension.all.mangaball

import android.util.Log
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.IOException
import org.jsoup.nodes.Document
import rx.Observable
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale

class MangaBall(
    override val lang: String,
    private vararg val siteLang: String,
) : HttpSource(),
    ConfigurableSource {

    override val name = "Manga Ball"
    override val baseUrl = "https://mangaball.net"
    override val supportsLatest = true
    private val preferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            var request = chain.request()
            if (request.url.pathSegments[0] == "api") {
                request = request.newBuilder()
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("X-CSRF-TOKEN", getCSRF())
                    .build()

                val response = chain.proceed(request)
                if (!response.isSuccessful && response.code == 403) {
                    response.close()
                    request = request.newBuilder()
                        .header("X-CSRF-TOKEN", getCSRF(forceReset = true))
                        .build()

                    chain.proceed(request)
                } else {
                    response
                }
            } else {
                chain.proceed(request)
            }
        }
        .build()

    private var csrf: String? = null

    @Synchronized
    private fun getCSRF(document: Document? = null, forceReset: Boolean = false): String {
        if (csrf == null || document != null || forceReset) {
            val doc = document ?: client.newCall(
                GET(baseUrl, headers),
            ).execute().asJsoup()

            doc.selectFirst("meta[name=csrf-token]")
                ?.attr("content")
                ?.takeIf { it.isNotBlank() }
                ?.also { csrf = it }
        }

        return csrf ?: throw Exception("CSRF token not found")
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val filters = getFilterList().apply {
            firstInstance<SortFilter>().state = 6
        }

        return searchMangaRequest(page, "", filters)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", getFilterList())

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = if (query.startsWith("https://")) {
        deepLink(query)
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = FormBody.Builder().apply {
            add("search_input", query.trim())
            add("filters[sort]", filters.firstInstance<SortFilter>().selected)
            add("filters[page]", page.toString())
            filters.filterIsInstance<TriStateGroupFilter<String>>().forEach { tags ->
                tags.included.forEach { tag ->
                    add("filters[tag_included_ids][]", tag)
                }
            }
            add("filters[tag_included_mode]", filters.firstInstance<TagIncludeMode>().selected)
            filters.filterIsInstance<TriStateGroupFilter<String>>().forEach { tags ->
                tags.excluded.forEach { tag ->
                    add("filters[tag_excluded_ids][]", tag)
                }
            }
            add("filters[tag_excluded_mode]", filters.firstInstance<TagExcludeMode>().selected)
            add("filters[contentRating]", "any")
            add("filters[demographic]", filters.firstInstance<DemographicFilter>().selected)
            add("filters[person]", "any")
            add("filters[publicationYear]", "")
            add("filters[publicationStatus]", filters.firstInstance<StatusFilter>().selected)
            siteLang.forEach {
                add("filters[translatedLanguage][]", it)
            }
        }.build()

        return POST("$baseUrl/api/v1/title/search-advanced/", headers, body)
    }

    override fun getFilterList() = FilterList(
        SortFilter(),
        DemographicFilter(),
        StatusFilter(),
        ContentFilter(),
        FormatFilter(),
        GenreFilter(),
        OriginFilter(),
        ThemeFilter(),
        TagIncludeMode(),
        TagExcludeMode(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResponse>()
        val hideNsfw = hideNsfwPreference()

        val mangas = data.data
            .filterNot {
                it.isAdult && hideNsfw
            }
            .map {
                SManga.create().apply {
                    url = it.url.toHttpUrl().pathSegments[1]
                    title = it.name
                    thumbnail_url = it.cover
                }
            }

        if (mangas.isEmpty() && hideNsfw) {
            throw Exception("All results filtered out due to nsfw filter")
        }

        return MangasPage(mangas, data.hasNextPage())
    }

    private fun deepLink(url: String): Observable<MangasPage> {
        val httpUrl = url.toHttpUrl()
        if (
            httpUrl.host == baseUrl.toHttpUrl().host &&
            httpUrl.pathSegments.size >= 2 &&
            httpUrl.pathSegments[0] in listOf("title-detail", "chapter-detail")
        ) {
            val slug = if (httpUrl.pathSegments[0] == "title-detail") {
                httpUrl.pathSegments[1]
            } else {
                client.newCall(GET(httpUrl, headers)).execute()
                    .use { response ->
                        response.asJsoup()
                            .selectFirst(".yoast-schema-graph")!!.data()
                            .parseAs<Yoast>()
                            .graph.first { it.type == "WebPage" }
                            .url!!.toHttpUrl()
                            .pathSegments[1]
                    }
            }

            val manga = SManga.create().apply {
                this.url = slug
            }

            return fetchMangaDetails(manga).map {
                MangasPage(listOf(it), false)
            }
        }

        throw Exception("Unsupported url")
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title-detail/${manga.url}/"

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        getCSRF(document)

        return SManga.create().apply {
            url = document.location().toHttpUrl().pathSegments[1]
            title = document.selectFirst("#comicDetail h6")!!.ownText()
            thumbnail_url = document.selectFirst("img.featured-cover")?.absUrl("src")
            genre = buildList {
                document.selectFirst("#featuredComicsCarousel img[src*=/flags/]")
                    ?.attr("src")?.also {
                        when {
                            it.contains("jp") -> add("Manga")
                            it.contains("kr") -> add("Manhwa")
                            it.contains("cn") -> add("Manhua")
                        }
                    }
                document.select("#comicDetail span[data-tag-id]")
                    .mapTo(this) { it.ownText() }
            }.joinToString()
            author = document.select("#comicDetail span[data-person-id]")
                .eachText().joinToString()
            description = buildString {
                document.selectFirst("#descriptionContent p")
                    ?.also { append(it.wholeText()) }
                document.selectFirst("#comicDetail span.badge:contains(Published)")
                    ?.also { append("\n\n", it.text()) }
                val titles = document.select("div.alternate-name-container").text().split("/")
                if (titles.isNotEmpty()) {
                    append("\n\nAlternative Names: \n")
                    titles.forEach {
                        append("- ", it.trim(), "\n")
                    }
                }
            }.trim()
            status = when (document.selectFirst("span.badge-status")?.text()) {
                "Ongoing" -> SManga.ONGOING
                "Completed" -> SManga.COMPLETED
                "Hiatus" -> SManga.ON_HIATUS
                "Cancelled" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("-")
        val body = FormBody.Builder()
            .add("title_id", id)
            .build()

        return POST("$baseUrl/api/v1/chapter/chapter-listing-by-title-id/", headers, body)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        (response.request.body as FormBody).also {
            updateViews(it.value(0))
        }

        val data = response.parseAs<ChapterListResponse>()

        return data.chapters.flatMap { chapter ->
            chapter.translations.mapNotNull { translation ->
                if (translation.language in siteLang) {
                    SChapter.create().apply {
                        url = translation.id
                        name = buildString {
                            if (translation.volume > 0) {
                                append("Vol. ")
                                append(translation.volume)
                                append(" ")
                            }
                            val number = chapter.number.toString().removeSuffix(".0")
                            if (translation.name.contains(number)) {
                                append(translation.name.trim())
                            } else {
                                append("Ch. ")
                                append(number)
                                append(" ")
                                append(translation.name.trim())
                            }
                        }
                        chapter_number = chapter.number
                        date_upload = dateFormat.tryParse(translation.date)
                        scanlator = buildString {
                            append(translation.group.name)
                            // id is usually the name of the site the chapter was scraped from
                            // if not then it is generated id of an active group on the site
                            if (groupIdRegex.matchEntire(translation.group.id) == null) {
                                append(" (")
                                append(translation.group.id)
                                append(")")
                            }
                        }
                    }
                } else {
                    null
                }
            }
        }
    }

    private val groupIdRegex = Regex("""[a-z0-9]{24}""")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter-detail/${chapter.url}/"

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        getCSRF(document)

        document.select("script:containsData(titleId)").joinToString(";") { it.data() }.also {
            val titleId = titleIdRegex.find(it)
                ?.groupValues?.get(1)
                ?: return@also
            val chapterId = chapterIdRegex.find(it)
                ?.groupValues?.get(1)
                ?: return@also

            updateViews(titleId, chapterId)
        }

        val script = document.select("script:containsData(chapterImages)").joinToString(";") { it.data() }
        val images = imagesRegex.find(script)
            ?.groupValues?.get(1)
            ?.parseAs<List<String>>()
            .orEmpty()

        return images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    private val imagesRegex = Regex("""const\s+chapterImages\s*=\s*JSON\.parse\(`([^`]+)`\)""")
    private val titleIdRegex = Regex("""const\s+titleId\s*=\s*`([^`]+)`;""")
    private val chapterIdRegex = Regex("""const\s+chapterId\s*=\s*`([^`]+)`;""")

    private fun updateViews(titleId: String, chapterId: String = "") {
        val body = FormBody.Builder()
            .add("title_id", titleId)
            .add("chapter_id", chapterId)
            .build()

        val request = POST("$baseUrl/api/v1/views/update/", headers, body)

        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.closeQuietly()
                    }
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(name, "Failed to update views", e)
                    }
                },
            )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = NSFW_PREF
            title = "Hide NSFW content"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun hideNsfwPreference() = preferences.getBoolean(NSFW_PREF, false)

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

private const val NSFW_PREF = "nsfw_pref"
