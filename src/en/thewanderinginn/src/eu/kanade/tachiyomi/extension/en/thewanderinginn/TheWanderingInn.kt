package eu.kanade.tachiyomi.extension.en.thewanderinginn

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

@Source
abstract class TheWanderingInn :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override suspend fun getPopularManga(page: Int) = MangasPage(Comic.entries.map { it.toSManga() }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = Comic.entries.filter { it.title.contains(query, ignoreCase = true) }.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val comic = when (val segment = url.pathSegments.first()) {
            "table-of-contents", "chapter", "tag", "scroll-reader" -> Comic.MAIN
            "collection" -> Comic.MINI_COMICS
            "" -> null
            "category" -> Comic.MINI_COMICS.takeIf { url.pathSegments.getOrNull(1) == "mini-comics" }
            else -> postComic(segment)
        }
        return comic?.toSManga()
    }

    // Page posts live at /<slug>/, so their entry comes from the post's category
    private suspend fun postComic(slug: String): Comic? {
        val url = apiUrl("posts").newBuilder()
            .addQueryParameter("slug", slug)
            .addQueryParameter("_fields", "categories")
            .build()
        val categories = client.get(url, Fetch.XHR).parseAs<List<CategoriesDto>>().firstOrNull()?.categories
            ?: return null
        return Comic.entries.firstOrNull { it.categoryId in categories }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = Comic.entries.first { it.url == manga.url }
        if (!fetchChapters) return SMangaUpdate(comic.toSManga(), chapters)

        val postsUrl = apiUrl("posts").newBuilder()
            .addQueryParameter("categories", comic.categoryId.toString())
            .addQueryParameter("_fields", "date_gmt,title,class_list")
            .build()
        val termsUrl = apiUrl(comic.taxonomy).newBuilder()
            .addQueryParameter("_fields", "id,name,slug")
            .build()
        val (terms, posts) = coroutineScope {
            val terms = async { fetchAll<TermDto>(termsUrl) }
            val posts = async { fetchAll<PostDto>(postsUrl) }
            terms.await() to posts.await()
        }

        // Each page post is titled "<term name>: Page N"
        val termsByName = terms.associateBy { it.name.unescape() }
        val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED, false)
        val chapterList = posts
            .groupBy { post -> termsByName[post.title.rendered.unescape().substringBefore(":").trim()] }
            .mapNotNull { (term, pages) ->
                if (term == null) return@mapNotNull null
                val locked = pages.any { it.isLocked }
                if (locked && !showLocked) return@mapNotNull null
                val name = term.name.unescape()
                SChapter.create().apply {
                    url = "/${comic.termPath}/${term.slug}/"
                    this.name = if (locked) "🔒 $name" else name
                    date_upload = pages.minOf { it.date }
                    memo = buildJsonObject { put("id", term.id) }
                }
            }
            .sortedByDescending { it.date_upload }

        return SMangaUpdate(comic.toSManga(), chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val comic = Comic.entries.first { chapter.url.startsWith("/${it.termPath}/") }
        val url = apiUrl("posts").newBuilder()
            .addQueryParameter(comic.taxonomy, chapter.memo["id"]!!.int.toString())
            .addQueryParameter("orderby", "date")
            .addQueryParameter("order", "asc")
            .addQueryParameter("per_page", "100")
            .addQueryParameter("_fields", "content")
            .build()
        // Patreon early access posts come back with an empty body
        val images = client.get(url, Fetch.XHR).parseAs<List<PageDto>>()
            .map { Jsoup.parseBodyFragment(it.content.rendered, baseUrl).select("img") }
        if (images.any { it.isEmpty() }) throw Exception("Patreon early access: this chapter isn't free to read yet")

        return images.flatten().mapIndexed { index, img -> Page(index, imageUrl = img.absUrl("src")) }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = "Show Patreon early access chapters"
            summary = "List chapters that are still Patreon early access, marked 🔒. They open once they're free to read."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun apiUrl(route: String) = "$baseUrl/wp-json/wp/v2/$route".toHttpUrl()

    private suspend inline fun <reified T> fetchAll(url: HttpUrl): List<T> {
        val first = client.get(pageUrl(url, 1), Fetch.XHR)
        val totalPages = first.header("X-WP-TotalPages")!!.toInt()
        val firstItems = first.parseAs<List<T>>()
        return coroutineScope {
            val rest = (2..totalPages).map { page -> async { client.get(pageUrl(url, page), Fetch.XHR).parseAs<List<T>>() } }
            firstItems + rest.awaitAll().flatten()
        }
    }

    private fun pageUrl(url: HttpUrl, page: Int) = url.newBuilder()
        .addQueryParameter("per_page", "100")
        .addQueryParameter("page", page.toString())
        .build()

    private fun String.unescape() = Parser.unescapeEntities(this, false)

    companion object {
        private const val PREF_SHOW_LOCKED = "show_locked_chapters"
    }
}
