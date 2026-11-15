package eu.kanade.tachiyomi.extension.all.naisho

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@Source
abstract class Naisho : KeiSource() {

    // Past 20 list pages a minute, the site sends a "Slow down your scrolling!" notice instead of the next one
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 15, period = 1.minutes) { it.encodedPath.endsWith("/update") }

    private val livewireHeaders get() = headers.newBuilder().set("X-Livewire", "1").build()

    // The site's ranking, which it loads into the page afterwards
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/trending"
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val snapshot = document.selectFirst("[wire:name=pages::trending]")!!.attr("wire:snapshot")
        val island = snapshot.parseAs<PageSnapshotDto>().memo.islands.first().name
        val script = document.selectFirst("script[data-update-uri]")!!
        val call = CallDto("__lazyLoadIsland", emptyList(), MetadataDto(IslandDto(island, "morph")))
        val result = livewire(script.attr("data-update-uri"), script.attr("data-csrf"), url, snapshot, call)
        val mangas = result.effects.islandFragments.flatMap { fragment ->
            Jsoup.parseBodyFragment(fragment, baseUrl).select("div[wire:key^=post-]").map { it.toSManga() }
        }
        return MangasPage(mangas, false)
    }

    private fun Element.toSManga() = SManga.create().apply {
        val link = selectFirst("a[title]")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.attr("title")
        thumbnail_url = selectFirst("img")?.absUrl("src")
    }

    override suspend fun getLatestUpdates(page: Int) = getGrid("$baseUrl/g".toHttpUrl(), page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val text = query.trim()
        val tags = filters.firstInstanceOrNull<TagsFilter>()?.state.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        val url = "$baseUrl/g".toHttpUrl().newBuilder().apply {
            // A tag with its namespace, like a tapped genre: the site's text search finds nothing for it
            if (TAG.matches(text)) {
                tags += text
            } else if (text.isNotEmpty()) {
                addQueryParameter("search", text)
            }
            filters.firstInstanceOrNull<LanguageFilter>()?.selected?.let { addQueryParameter("lang", it) }
            filters.firstInstanceOrNull<CategoryFilter>()?.selected.orEmpty().forEachIndexed { index, id -> addQueryParameter("cats[$index]", id.toString()) }
            tags.forEachIndexed { index, tag ->
                addQueryParameter("tags[$index][name]", tag.removePrefix("-"))
                addQueryParameter("tags[$index][excluded]", if (tag.startsWith("-")) "1" else "0")
            }
        }.build()
        return getGrid(url, page)
    }

    // A list link of the site (with its filters), the latest galleries, the ranking, or a gallery (any of its pages)
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val segments = url.pathSegments.filter(String::isNotEmpty)
        return when {
            segments == listOf("g") -> getGrid(url, page)
            segments.isEmpty() || segments == listOf("home") -> getLatestUpdates(page)
            segments == listOf("trending") -> getPopularManga(page)
            segments.size >= 2 && segments[0] == "g" -> {
                val manga = SManga.create().apply { this.url = "/g/${segments[1]}" }
                MangasPage(listOf(getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga), false)
            }
            else -> MangasPage(emptyList(), false)
        }
    }

    // The site's lists grow by "load more" calls to its Livewire component, each one from the state the previous one returned
    private class GridState(val url: HttpUrl, val page: Int, val update: String, val token: String, val snapshot: String)

    private class GridPage(val state: GridState, val posts: List<PostDto>, val hasMore: Boolean)

    @Volatile
    private var gridState: GridState? = null

    private suspend fun getGrid(url: HttpUrl, page: Int): MangasPage {
        // Without the previous page's state (another list was opened since), the list is loaded again from its start
        var result = gridState?.takeIf { it.url == url && it.page == page - 1 }?.let { loadMore(it) } ?: openGrid(url)
        while (result.state.page < page) result = loadMore(result.state)
        gridState = result.state
        return MangasPage(result.posts.map { it.toSManga() }, result.hasMore)
    }

    private suspend fun openGrid(url: HttpUrl): GridPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val script = document.selectFirst("script[data-update-uri]")!!
        val grid = document.selectFirst("[wire:name=post-grid]")!!
        val state = GridState(url, 0, script.attr("data-update-uri"), script.attr("data-csrf"), grid.attr("wire:snapshot"))
        val lazyLoad = LAZY_LOAD.find(grid.attr("x-init"))!!.groupValues[1]
        return gridCall(state, CallDto("__lazyLoad", listOf(lazyLoad), MetadataDto(null)))
    }

    private suspend fun loadMore(state: GridState) = gridCall(state, CallDto("loadMore", emptyList(), MetadataDto(null)))

    private suspend fun gridCall(state: GridState, call: CallDto): GridPage {
        val result = livewire(state.update, state.token, state.url.toString(), state.snapshot, call)
        val posts = result.effects.dispatches.firstOrNull { it.name == "posts-loaded" }
            ?: throw Exception(result.effects.dispatches.first { it.name == "toast" }.params.parseAs<ToastDto>().message)
        return GridPage(
            GridState(state.url, state.page + 1, state.update, state.token, result.snapshot),
            posts.params.parseAs<PostsDto>().items,
            result.snapshot.parseAs<GridSnapshotDto>().data.hasMore,
        )
    }

    private suspend fun livewire(update: String, token: String, page: String, snapshot: String, call: CallDto): ComponentResultDto {
        val body = UpdateDto(token, listOf(ComponentUpdateDto(snapshot, emptyMap(), listOf(call)))).toJsonRequestBody()
        return client.post(update, livewireHeaders, Fetch.XHR.from(page), body).parseAs<UpdateResultDto>().components.single()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val article = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup().selectFirst("article")!!
        val galleryTitle = article.selectFirst("header h1")!!.text()
        val tags = article.select("section div.flex-wrap:has(> button)").associate { row ->
            row.selectFirst("> span")!!.text() to row.select("> button").map { it.text() }
        }
        val details = SManga.create().apply {
            url = manga.url
            title = galleryTitle
            thumbnail_url = article.selectFirst("section img")?.absUrl("src")
            val artists = tags["Artist"].orEmpty()
            author = tags["Group"].orEmpty().ifEmpty { artists }.joinToString().ifEmpty { null }
            artist = artists.joinToString().ifEmpty { null }
            // Named like the site's tag filter, so that a tapped genre searches by its tag
            genre = tags.filterKeys { it != "Category" && it != "Language" }
                .flatMap { (namespace, names) -> names.map { "${namespace.lowercase()}:$it" } }
                .joinToString().ifEmpty { null }
            description = MarkdownDescription.Builder()
                .field("Category", tags["Category"].orEmpty())
                .field("Language", tags["Language"].orEmpty())
                .field("Pages", article.selectFirst("section span:containsOwn(Pages) + span")?.text())
                .altTitles(listOfNotNull(article.selectFirst("header span")?.text()).filter { it != galleryTitle })
                .build()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = "${manga.url}/1"
            name = "Gallery"
            date_upload = DATE_FORMAT.tryParseDateTime(article.selectFirst("time")?.attr("datetime"), ZoneOffset.UTC)
        }
        return SMangaUpdate(details, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reader = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup().selectFirst("[wire:name=pages::post-reader]")!!
        // Laravel's Js::from: JSON in a JavaScript string whose escapes are JSON ones
        val json = "\"${PAGES.find(reader.attr("x-data"))!!.groupValues[1]}\"".parseAs<String>()
        return json.parseAs<List<PageDto>>().mapIndexed { index, page -> Page(index, imageUrl = page.src) }
    }

    override fun getFilterList(data: JsonElement?) = getFilters()

    companion object {
        private val LAZY_LOAD = Regex("""__lazyLoad\('([^']+)'\)""")
        private val PAGES = Regex("""pages: JSON\.parse\('([^']*)'\)""")
        private val TAG = Regex("""-?(?:artist|character|cosplayer|female|group|language|location|male|mixed|other|parody|reclass|temp):.+""")
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
    }
}
