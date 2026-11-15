package eu.kanade.tachiyomi.extension.all.jagodibuja

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException

@Source
abstract class JagoDibuja : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int) = MangasPage(comics.map { it.toSManga() }, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = MangasPage(comics.filter { it.title.contains(query, ignoreCase = true) }.map { it.toSManga() }, false)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        return comics.firstOrNull { it.url == url.encodedPath }?.toSManga()?.apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comic = comics.first { it.url == manga.url }
        val chapters = if (fetchChapters) {
            val chapters = when (comic.category) {
                null -> posts("slug" to comic.post!!).map { it.toSChapter() }
                else -> {
                    val parts = posts("categories[terms]" to comic.category.toString(), "categories[include_children]" to "true")
                    val strips = if (comic.strips) newStrips(parts.maxOf { it.modified }) else emptyList()
                    strips + parts.map { it.toSChapter() }
                }
            }
            // Numbered in reading order: the parts' names hold ranges of strips
            chapters.asReversed().forEachIndexed { idx, chapter -> chapter.chapter_number = idx + 1f }
            chapters
        } else {
            chapters
        }
        return SMangaUpdate(comic.toSManga(), chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val slug = chapter.url.trim('/').substringAfterLast('/')
        val post = posts("slug" to slug, fields = "date_gmt,modified,link,title,content").first()
        return Jsoup.parseBodyFragment(post.content!!.rendered, baseUrl).select("img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.largestImage())
        }
    }

    // Strips are posted one by one, then join a part: those posted since the last part was updated.
    // Their titles often have the wrong number or language, unlike the strip's image (773.jpg, 773en.jpg).
    private suspend fun newStrips(after: String): List<SChapter> {
        val numbers = mutableSetOf<String>()
        return posts("search" to "LWHG", "after" to after, fields = "date_gmt,modified,link,title,content").mapNotNull { post ->
            val image = Jsoup.parseBodyFragment(post.content!!.rendered).selectFirst("img")?.attr("src").orEmpty()
            val (number, english) = STRIP_IMAGE.find(image.substringAfterLast('/'))?.destructured ?: return@mapNotNull null
            if (english.isNotEmpty() != (lang == "en") || !numbers.add(number)) return@mapNotNull null
            post.toSChapter(if (lang == "en") "LWHG comic #$number" else "LWHG tira #$number")
        }
    }

    private suspend fun posts(vararg query: Pair<String, String>, fields: String = "date_gmt,modified,link,title"): List<Post> {
        val posts = mutableListOf<Post>()
        var page = 1
        do {
            val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder().apply {
                query.forEach { (name, value) -> addQueryParameter(name, value) }
                addQueryParameter("_fields", fields)
                addQueryParameter("per_page", "100")
                addQueryParameter("page", page.toString())
            }.build()
            val response = getJson(url)
            val pages = response.header("X-WP-TotalPages")?.toInt() ?: 1
            posts += response.parseAs<List<Post>>()
        } while (page++ < pages)
        return posts
    }

    // The host's bot check answers some requests with a page that reloads itself after a few seconds,
    // which a browser gets through: the app shares the cookie it then gets
    private suspend fun getJson(url: HttpUrl): Response {
        val response = client.get(url, Fetch.XHR)
        if (response.header("Content-Type")?.startsWith("text/html") != true) return response
        response.close()
        runWebView<Unit> {
            // Older WebViews would send X-Requested-With with the app's package name
            if (!originMatchedHeaderSupported) throw IOException("Open WebView to get past the site's check, then retry")
            setOriginMatchedHeader("X-Requested-With", "XMLHttpRequest", setOf(baseUrl))
            userAgent = headers["User-Agent"]!!
            blockImages = true
            onPageFinished {
                evaluateJs("document.title == 'One moment, please...'") { checking -> if (checking == "false") resolve(Unit) }
            }
            loadUrl(url.toString())
        }
        return client.get(url, Fetch.XHR)
    }

    private fun Element.largestImage(): String = attr("srcset").split(",")
        .map { it.trim().split(" ") }
        .filter { it.size == 2 }
        .maxByOrNull { it[1].removeSuffix("w").toIntOrNull() ?: 0 }
        ?.let { baseUrl.toHttpUrl().resolve(it[0])!!.toString() }
        ?: absUrl("src")

    private val comics: List<Comic>
        get() = when (lang) {
            "es" -> listOf(
                Comic("/living-with-hipster-and-gamer-girl-es/", "Living with Hipstergirl and Gamergirl", "2022/09/seccion-living-with-Hipster.png", category = 7, strips = true),
                Comic("/comic-lwhg/", "Living with Erika and Sophie", "2026/10/fpface.webp", post = "comic-lwhg"),
                Comic("/diario-magico/", "Diario mágico", "2022/09/seccion-diario-magico.png", category = 27),
            )
            else -> listOf(
                Comic("/web-comic-living-with-hipstergirl-and-gamergirl-en/", "Living with Hipstergirl and Gamergirl", "2022/09/seccion-living-with-Hipster-1.png", category = 28, strips = true),
                Comic("/comic-lwhg-pag-1-english/", "Living with Erika and Sophie", "2026/10/fpface.webp", post = "comic-lwhg-pag-1-english"),
            )
        }

    private inner class Comic(
        val url: String,
        val title: String,
        private val cover: String,
        val category: Int? = null,
        val post: String? = null,
        val strips: Boolean = false,
    ) {
        fun toSManga() = SManga.create().apply {
            url = this@Comic.url
            title = this@Comic.title
            thumbnail_url = "$baseUrl/wp-content/uploads/$cover"
            author = "Jago"
            artist = author
        }
    }

    companion object {
        // 773.jpg, 773en-688x1024.jpg, 771-2-460x1024.jpg
        private val STRIP_IMAGE = Regex("""^(\d+)(en)?(?:-\d+(?:x\d+)?)*\.\w+$""", RegexOption.IGNORE_CASE)
    }
}
