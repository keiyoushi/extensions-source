package eu.kanade.tachiyomi.multisrc.galleryadults

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.randomua.addRandomUAPreferenceToScreen
import eu.kanade.tachiyomi.lib.randomua.getPrefCustomUA
import eu.kanade.tachiyomi.lib.randomua.getPrefUAType
import eu.kanade.tachiyomi.lib.randomua.setRandomUserAgent
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

abstract class GalleryAdults(
    override val name: String,
    override val baseUrl: String,
    override val lang: String = "all",
    protected open val mangaLang: String = "",
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ", Locale.getDefault()),
) : ConfigurableSource, ParsedHttpSource() {

    override val supportsLatest = false

    protected open val utils: GalleryAdultsUtils = GalleryAdultsUtils()

    protected open val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        addRandomUAPreferenceToScreen(screen)
    }

    override val client: OkHttpClient by lazy {
        network.cloudflareClient.newBuilder()
            .setRandomUserAgent(
                userAgentType = preferences.getPrefUAType(),
                customUA = preferences.getPrefCustomUA(),
                filterInclude = listOf("chrome"),
            )
            .rateLimit(4)
            .build()
    }

    protected open fun Element.mangaTitle(selector: String = ".caption"): String? =
        selectFirst(selector)?.text()

    protected open fun Element.mangaUrl() =
        selectFirst(".inner_thumb a")?.attr("abs:href")

    protected open fun Element.mangaThumbnail() =
        selectFirst(".inner_thumb img")?.imgAttr()

    // Overwrite this to filter other languages' manga from search result. Default to [mangaLang] won't filter anything
    protected open fun Element.mangaLang() = mangaLang

    /* Popular */
    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (mangaLang.isNotEmpty()) addPathSegments("language/$mangaLang/")
            if (page > 1) addQueryParameter("page", page.toString())
        }
        return GET(url.build(), headers)
    }

    override fun popularMangaSelector() = "div.thumb"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.mangaTitle()!!
            setUrlWithoutDomain(element.mangaUrl()!!)
            thumbnail_url = element.mangaThumbnail()
        }
    }

    override fun popularMangaNextPageSelector() =
        "li.active + li:not(.disabled), li.page-item:last-of-type:not(.disabled)" // ".pagination .next"

    /* Latest */
    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    /* Search */
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_ID_SEARCH) -> {
                val id = query.removePrefix(PREFIX_ID_SEARCH)
                client.newCall(searchMangaByIdRequest(id))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, id) }
            }
            query.toIntOrNull() != null -> {
                client.newCall(searchMangaByIdRequest(query))
                    .asObservableSuccess()
                    .map { response -> searchMangaByIdParse(response, query) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    protected open fun searchMangaByIdRequest(id: String): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(PREFIX_ID)
            addPathSegments("$id/")
        }
        return GET(url.build(), headers)
    }

    protected open fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/$PREFIX_ID/$id/"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tagFilter = filters.filterIsInstance<TagFilter>().firstOrNull()
        val favoriteFilter = filters.filterIsInstance<FavoriteFilter>().firstOrNull()
        return when {
            query.isNotBlank() -> {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegments("search/")
                    addQueryParameter("q", query.trim())
                    addQueryParameter("sort", "popular")
                    if (page > 1) addQueryParameter("page", page.toString())
                }
                GET(url.build(), headers)
            }
            favoriteFilter?.state == true -> {
                val url = "$baseUrl/$favoritePath/".toHttpUrl().newBuilder()
                    .addQueryParameter("page", page.toString())

                return GET(url.build(), headers)
            }
            tagFilter!!.state > 0 -> {
                val url = baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("tag")
                    addPathSegment(tagFilter.toUriPart())
                    addPathSegment("") // add ending slash (/)
                }
                GET(tagPageUri(url, page).build(), headers)
            }
            else -> popularMangaRequest(page)
        }
    }

    protected open val favoritePath = "user"

    protected open fun tagPageUri(url: HttpUrl.Builder, page: Int) =
        url.apply {
            addQueryParameter("page", page.toString())
        }

    protected class SMangaDto(
        val title: String,
        val url: String,
        val thumbnail: String?,
        val lang: String,
    )

    protected open fun loginRequired(document: Document, url: String): Boolean {
        return (
            url.contains("/login/") &&
                document.select("input[value=Login]").isNotEmpty()
            )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (loginRequired(document, response.request.url.toString())) {
            throw Exception("Log in via WebView to view favorites")
        } else {
            val mangas = document.select(searchMangaSelector())
                .map {
                    SMangaDto(
                        title = it.mangaTitle()!!,
                        url = it.mangaUrl()!!,
                        thumbnail = it.mangaThumbnail(),
                        lang = it.mangaLang(),
                    )
                }
                .let { unfiltered ->
                    if (mangaLang.isNotEmpty()) unfiltered.filter { it.lang == mangaLang } else unfiltered
                }
                .map {
                    SManga.create().apply {
                        title = it.title
                        setUrlWithoutDomain(it.url)
                        thumbnail_url = it.thumbnail
                    }
                }

            return MangasPage(mangas, document.select(searchMangaNextPageSelector()).isNotEmpty())
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    /* Details */
    protected open fun Element.getCover() =
        selectFirst(".cover img")?.imgAttr()

    protected open fun Element.getTag(tag: String): String {
        return select("ul.${tag.lowercase()} a")
            .joinToString { it.ownText() }
    }

    protected open fun Element.getDescription(): String = (
        listOf("Parodies", "Characters", "Languages", "Categories")
            .mapNotNull { tag ->
                getTag(tag)
                    .let { if (it.isNotEmpty()) "$tag: $it" else null }
            } +
            listOfNotNull(
                selectFirst(".pages:contains(Pages:)")?.ownText(),
            )
        )
        .joinToString("\n")

    protected open val mangaDetailInfoSelector = ".gallery_top"

    override fun mangaDetailsParse(document: Document): SManga {
        return document.selectFirst(mangaDetailInfoSelector)!!.run {
            SManga.create().apply {
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                status = SManga.COMPLETED
                title = mangaTitle("h1")!!
                thumbnail_url = getCover()
                genre = getTag("Tags")
                author = getTag("Artists")
                description = getDescription()
            }
        }
    }

    /* Chapters */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                scanlator = document.selectFirst(mangaDetailInfoSelector)
                    ?.getTag("Groups")
                date_upload = utils.getTime(document, dateFormat)
                setUrlWithoutDomain(response.request.url.encodedPath)
            },
        )
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    /* Pages */
    // convert thumbnail URLs to full image URLs
    protected open fun String.full(): String {
        val fType = substringAfterLast("t")
        return replace("t$fType", fType)
    }

    protected open fun Document.inputIdValueOf(string: String): String {
        return select("input[id=$string]").attr("value")
    }

    override fun pageListParse(document: Document): List<Page> {
        val thumbUrls = document.select(".preview_thumb img")
            .map { it.imgAttr() }
            .toMutableList()

        // input only exists if pages > 10 and have to make a request to get the other thumbnails
        val totalPages = document.inputIdValueOf("t_pages")

        if (totalPages.isNotEmpty()) {
            val token = document.select("[name=csrf-token]").attr("content")

            val form = FormBody.Builder()
                .add("_token", token)
                .add("id", document.inputIdValueOf("load_id"))
                .add("dir", document.inputIdValueOf("load_dir"))
                .add("visible_pages", "10")
                .add("t_pages", totalPages)
                .add("type", "2") // 1 would be "more", 2 is "all remaining"
                .build()

            val xhrHeaders = headers.newBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .build()

            client.newCall(POST("$baseUrl/inc/thumbs_loader.php", xhrHeaders, form))
                .execute()
                .asJsoup()
                .select("img")
                .mapTo(thumbUrls) { it.imgAttr() }
        }
        return thumbUrls.mapIndexed { i, url -> Page(i, "", url?.full()) }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    /* Filters */
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        TagFilter(),
        FavoriteFilter(),
    )

    // Top 50 tags
    private class TagFilter : UriPartFilter(
        "Tag",
        arrayOf(
            Pair("<select>", "---"),
            Pair("Big breasts", "big-breasts"),
            Pair("Sole female", "sole-female"),
            Pair("Sole male", "sole-male"),
            Pair("Anal", "anal"),
            Pair("Nakadashi", "nakadashi"),
            Pair("Group", "group"),
            Pair("Stockings", "stockings"),
            Pair("Blowjob", "blowjob"),
            Pair("Schoolgirl uniform", "schoolgirl-uniform"),
            Pair("Rape", "rape"),
            Pair("Lolicon", "lolicon"),
            Pair("Glasses", "glasses"),
            Pair("Defloration", "defloration"),
            Pair("Ahegao", "ahegao"),
            Pair("Incest", "incest"),
            Pair("Shotacon", "shotacon"),
            Pair("X-ray", "x-ray"),
            Pair("Bondage", "bondage"),
            Pair("Full color", "full-color"),
            Pair("Double penetration", "double-penetration"),
            Pair("Femdom", "femdom"),
            Pair("Milf", "milf"),
            Pair("Yaoi", "yaoi"),
            Pair("Multi-work series", "multi-work-series"),
            Pair("Schoolgirl", "schoolgirl"),
            Pair("Mind break", "mind-break"),
            Pair("Paizuri", "paizuri"),
            Pair("Mosaic censorship", "mosaic-censorship"),
            Pair("Impregnation", "impregnation"),
            Pair("Males only", "males-only"),
            Pair("Sex toys", "sex-toys"),
            Pair("Sister", "sister"),
            Pair("Dark skin", "dark-skin"),
            Pair("Ffm threesome", "ffm-threesome"),
            Pair("Hairy", "hairy"),
            Pair("Cheating", "cheating"),
            Pair("Sweating", "sweating"),
            Pair("Yuri", "yuri"),
            Pair("Netorare", "netorare"),
            Pair("Full censorship", "full-censorship"),
            Pair("Schoolboy uniform", "schoolboy-uniform"),
            Pair("Dilf", "dilf"),
            Pair("Big penis", "big-penis"),
            Pair("Futanari", "futanari"),
            Pair("Swimsuit", "swimsuit"),
            Pair("Collar", "collar"),
            Pair("Uncensored", "uncensored"),
            Pair("Big ass", "big-ass"),
            Pair("Story arc", "story-arc"),
            Pair("Teacher", "teacher"),
        ),
    )

    private class FavoriteFilter : Filter.CheckBox("Show favorites only", false)

    protected open class UriPartFilter(displayName: String, private val pairs: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, pairs.map { it.first }.toTypedArray()) {
        fun toUriPart() = pairs[state].second
    }

    protected fun Element.imgAttr(): String? = when {
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
        hasAttr("data-src") -> absUrl("data-src")
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        hasAttr("srcset") -> absUrl("srcset").substringBefore(" ")
        else -> absUrl("src")
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        const val PREFIX_ID = "g"
    }
}
