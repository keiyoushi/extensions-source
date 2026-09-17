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
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.IOException
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaBall :
    KeiSource(),
    ConfigurableSource {

    private val siteLang: List<String>
        get() = when (lang) {
            "ar" -> listOf("ar")
            "bg" -> listOf("bg")
            "bn" -> listOf("bn")
            "ca" -> listOf("ca", "ca-ad", "ca-es", "ca-fr", "ca-it", "ca-pt")
            "cs" -> listOf("cs")
            "da" -> listOf("da")
            "de" -> listOf("de")
            "el" -> listOf("el")
            "en" -> listOf("en")
            "es" -> listOf("es", "es-ar", "es-mx", "es-es", "es-la", "es-419")
            "fa" -> listOf("fa")
            "fi" -> listOf("fi")
            "fr" -> listOf("fr")
            "he" -> listOf("he")
            "hi" -> listOf("hi")
            "hu" -> listOf("hu")
            "id" -> listOf("id")
            "it" -> listOf("it", "it-it")
            "is" -> listOf("ib", "ib-is", "is")
            "ja" -> listOf("jp")
            "ko" -> listOf("kr")
            "kn" -> listOf("kn", "kn-in", "kn-my", "kn-sg", "kn-tw")
            "ml" -> listOf("ml", "ml-in", "ml-my", "ml-sg", "ml-tw")
            "ms" -> listOf("ms")
            "ne" -> listOf("ne")
            "nl" -> listOf("nl", "nl-be")
            "no" -> listOf("no")
            "pl" -> listOf("pl")
            "pt-BR" -> listOf("pt-br", "pt-pt")
            "ro" -> listOf("ro")
            "ru" -> listOf("ru")
            "sk" -> listOf("sk")
            "sl" -> listOf("sl")
            "sq" -> listOf("sq")
            "sr" -> listOf("sr", "sr-cyrl")
            "sv" -> listOf("sv")
            "ta" -> listOf("ta")
            "th" -> listOf("th", "th-hk", "th-kh", "th-la", "th-my", "th-sg")
            "tr" -> listOf("tr")
            "uk" -> listOf("uk")
            "vi" -> listOf("vi")
            "zh" -> listOf("zh", "zh-cn", "zh-hk", "zh-mo", "zh-sg", "zh-tw")
            else -> listOf(lang)
        }

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = addCookie { listOf("show18PlusContent" to hideNsfwPreference().not().toString()) }
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
                    csrf = null
                    request = request.newBuilder()
                        .header("X-CSRF-TOKEN", getCSRF())
                        .build()

                    chain.proceed(request)
                } else {
                    response
                }
            } else {
                chain.proceed(request)
            }
        }

    private var csrf: String? = null

    private fun setCSRF(document: Document) {
        document.selectFirst("meta[name=csrf-token]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?.also { csrf = it }
    }

    @Synchronized
    private fun getCSRF(): String {
        if (csrf == null) {
            val document = client.newCall(GET(baseUrl, headers)).execute().asJsoup()
            setCSRF(document)
        }

        return csrf ?: throw Exception("CSRF token not found")
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val filters = getFilterList(data = null).apply {
            firstInstance<SortFilter>().state = 6
        }

        return searchAdvanced(page, "", filters)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchAdvanced(page, "", getFilterList(data = null))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val defaultFilterState = run {
            filters.filterIsInstance<TriStateGroupFilter<String>>().all { filter -> filter.state.all { it.isIgnored() } } &&
                filters.firstInstance<DemographicFilter>().state == 0 &&
                filters.firstInstance<StatusFilter>().state == 0
        }

        if (query.isNotBlank() && defaultFilterState) {
            return if (page == 1) {
                querySearch(query)
            } else {
                searchAdvanced(page - 1, query, filters)
            }
        }

        return searchAdvanced(page, query, filters)
    }

    private suspend fun querySearch(query: String): MangasPage {
        val url = "$baseUrl/api/v1/smart-search/search/"
        val body = FormBody.Builder()
            .add("search_input", query.trim())
            .build()

        val mangas = client.post(url, headers, body).parseAs<QuerySearchResponse>().data.manga
            .map { manga ->
                SManga.create().apply {
                    this.url = "$baseUrl${manga.url}".toHttpUrl().pathSegments[1]
                    title = manga.title
                    thumbnail_url = manga.img
                }
            }

        return MangasPage(mangas, true)
    }

    private suspend fun searchAdvanced(page: Int, query: String, filters: FilterList): MangasPage {
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

        val response = client.post("$baseUrl/api/v1/title/search-advanced/", headers, body)

        return parseSearchManga(response)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
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

    private fun parseSearchManga(response: Response): MangasPage {
        val data = response.parseAs<SearchResponse>()

        val mangas = data.data
            .map {
                SManga.create().apply {
                    url = it.url.toHttpUrl().pathSegments[1]
                    title = it.name
                    thumbnail_url = it.cover
                }
            }

        return MangasPage(mangas, data.hasNextPage())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host ||
            url.pathSegments.size < 2 ||
            url.pathSegments[0] !in listOf("title-detail", "chapter-detail")
        ) {
            return null
        }

        val slug = if (url.pathSegments[0] == "title-detail") {
            url.pathSegments[1]
        } else {
            client.get(url).asJsoup()
                .selectFirst(".yoast-schema-graph")!!.data()
                .parseAs<Yoast>()
                .graph.first { it.type == "WebPage" }
                .url!!.toHttpUrl()
                .pathSegments[1]
        }

        return getMangaDetails(SManga.create().apply { this.url = slug })
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/title-detail/${manga.url}/"

    private suspend fun getMangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        setCSRF(document)

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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val mangaDeferred = async { if (fetchDetails) getMangaDetails(manga) else manga }
        val chaptersDeferred = async { if (fetchChapters) getChapterList(manga) else chapters }

        SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun getChapterList(manga: SManga): List<SChapter> {
        val id = manga.url.substringAfterLast("-")
        val body = FormBody.Builder()
            .add("title_id", id)
            .build()

        val response = client.post("$baseUrl/api/v1/chapter/chapter-listing-by-title-id/", headers, body)
        val data = response.parseAs<ChapterListResponse>()
        updateViews(id)

        return data.chapters.flatMap { chapter ->
            chapter.translations.mapNotNull { translation ->
                if (translation.language in siteLang) {
                    SChapter.create().apply {
                        url = translation.id
                        name = buildString {
                            val volume = translation.volume.toString().removeSuffix(".0")
                            if (translation.volume > 0) {
                                append("Vol. ")
                                append(volume)
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
                        date_upload = dateFormat.tryParseDateTime(translation.date)
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

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter-detail/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        setCSRF(document)

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
            summary = "Restart of the app required"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun hideNsfwPreference() = preferences.getBoolean(NSFW_PREF, false)
}

private const val NSFW_PREF = "nsfw_pref"
