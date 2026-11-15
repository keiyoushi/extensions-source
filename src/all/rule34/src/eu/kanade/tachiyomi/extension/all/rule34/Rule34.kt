package eu.kanade.tachiyomi.extension.all.rule34

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
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Source
abstract class Rule34 :
    KeiSource(),
    ConfigurableSource {
    private val preferences by getPreferencesLazy()
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override fun OkHttpClient.Builder.configureClient() = addCookie {
        listOf("filter_ai" to if (preferences.getBoolean("filter_ai", false)) "1" else "0")
    }.rateLimit(2)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = "filter_ai"
            title = "Filter AI posts"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override suspend fun getPopularManga(page: Int) = posts(page, "sort:score:desc")
    override suspend fun getLatestUpdates(page: Int) = posts(page, "sort:updated:desc")
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (filters.firstInstanceOrNull<CatalogueFilter>()?.state == 1) {
            require(query.isBlank()) { "The pool catalogue has no text search. Clear the search text." }
            return pools(page)
        }
        return posts(page, query)
    }

    private suspend fun pools(page: Int): MangasPage {
        val requestUrl = route("pool", "list").addQueryParameter("pid", ((page - 1) * 25).toString()).build()
        val document = client.get(requestUrl, Fetch.DOCUMENT).asJsoup()
        val entries = document.select("#pool-index tbody tr").map { row ->
            val link = row.selectFirst("td a")!!
            SManga.create().apply {
                url = "pool/${link.absUrl("href").toHttpUrl().queryParameter("id")!!}"
                title = link.text()
            }
        }
        return MangasPage(entries, document.selectFirst("#paginator a[alt=next]") != null)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(CatalogueFilter(), Filter.Header("Search posts using tags; use underscores within a tag and - to exclude a tag."))

    private fun route(type: String, action: String, id: String? = null) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("index.php").addQueryParameter("page", type).addQueryParameter("s", action)
        .apply { if (id != null) addQueryParameter("id", id) }

    private suspend fun posts(page: Int, tags: String): MangasPage {
        val requestUrl = route("post", "list").addQueryParameter("tags", "$tags -video".trim())
            .addQueryParameter("pid", ((page - 1) * 42).toString()).build()
        val document = client.get(requestUrl, Fetch.DOCUMENT).asJsoup()
        val entries = document.select(".thumb").map { element ->
            val id = element.selectFirst("a")!!.absUrl("href").toHttpUrl().queryParameter("id")!!
            val image = element.selectFirst("img")
            SManga.create().apply {
                url = "post/$id"
                title = "Post $id"
                thumbnail_url = image?.absUrl("src")
                genre = image?.attr("alt")?.split(' ')
                    ?.filterNot { it.startsWith("rating:") || it.startsWith("score:") || it.startsWith("user:") }
                    ?.joinToString { it.replace('_', ' ') }
            }
        }
        return MangasPage(entries, document.selectFirst("#paginator a[alt=next]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.encodedPath != "/index.php") return null
        val type = url.queryParameter("page") ?: return null
        val action = url.queryParameter("s")
        if (type != "post" && type != "pool") return null
        if ((type == "post" && action != "view") || (type == "pool" && action != "show")) return null
        val id = url.queryParameter("id")?.takeIf { it.toLongOrNull() != null } ?: return null
        return SManga.create().apply {
            this.url = "$type/$id"
            title = "${if (type == "pool") "Pool" else "Post"} $id"
        }
    }

    override fun getMangaUrl(manga: SManga) = entryUrl(manga.url)
    override fun getChapterUrl(chapter: SChapter) = entryUrl(chapter.url)

    private fun entryUrl(key: String): String {
        val (type, id) = key.split('/')
        return route(type, if (type == "pool") "show" else "view", id).build().toString()
    }

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val pool = manga.url.startsWith("pool/")
        manga.apply {
            if (pool) {
                title = document.selectFirst("#pool-show h4")!!.text().removePrefix("Pool: ")
                description = MarkdownDescription.Builder().description(document.selectFirst("#pool-show > div")).build()
                thumbnail_url = document.selectFirst(".thumb img")?.absUrl("src")
            } else {
                title = "Post ${url.split('/')[1]}"
                artist = document.select("#tag-sidebar .tag-type-artist a[href*=page=post]").joinToString { it.text() }.ifEmpty { null }
                genre = document.select("#tag-sidebar .tag a[href*=page=post]").joinToString { it.text() }.ifEmpty { null }
                thumbnail_url = document.selectFirst("a:containsOwn(Original image)")?.absUrl("href")
                description = MarkdownDescription.Builder()
                    .info(document.selectFirst("#stats li:contains(Rating:)")?.textOrNull())
                    .field("Score", document.selectFirst("#stats span[id^=psc]")?.textOrNull())
                    .field("Size", document.selectFirst("#stats li:contains(Size:)")?.textOrNull()?.removePrefix("Size: "))
                    .field("Uploader", document.selectFirst("#stats li:contains(Posted:) a")?.textOrNull())
                    .field("Title", document.selectFirst("input#title")?.attrOrNull("value"))
                    .links(document.select("#stats li:contains(Source:) a").map { "Source" to it.absUrl("href") }).build()
                status = SManga.COMPLETED
                update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            }
        }
        val chapterList = listOf(
            SChapter.create().apply {
                url = manga.url
                name = if (pool) "Pool" else "Post"
                if (!pool) date_upload = dateFormat.tryParseDateTime(document.selectFirst("#stats li:contains(Posted:)")?.ownText()?.removePrefix("Posted: ")?.trim()?.take(19), ZoneOffset.UTC)
            },
        )
        return SMangaUpdate(manga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return if (chapter.url.startsWith("pool/")) {
            document.select("#pool-show .thumb")
                .filter { "video" !in it.selectFirst("img")!!.attr("alt").split(' ') }
                .mapIndexed { index, element -> Page(index, url = element.selectFirst("a")!!.absUrl("href")) }
        } else {
            listOf(Page(0, imageUrl = document.originalImage()))
        }
    }

    override suspend fun getImageUrl(page: Page): String = client.get(page.url, Fetch.DOCUMENT).asJsoup().originalImage()

    private fun Document.originalImage(): String = checkNotNull(selectFirst("a:containsOwn(Original image)")) { "This post has no readable image." }.absUrl("href")
}
