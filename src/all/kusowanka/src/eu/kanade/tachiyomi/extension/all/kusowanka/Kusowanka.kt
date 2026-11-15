package eu.kanade.tachiyomi.extension.all.kusowanka

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
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

@Source
abstract class Kusowanka : KeiSource() {

    // Popular is the latest posts: the site's popular and top-rated lists are mostly videos and posts kept for members
    override val supportsLatest = false

    override val supportsRelatedMangas = true

    // The name lookup only answers requests sent like the site's own
    private val lookupHeaders get() = headersBuilder().set("X-Requested-With", "XMLHttpRequest").build()

    override suspend fun getPopularManga(page: Int): MangasPage = getPostList(baseUrl.toHttpUrl().newBuilder().addQueryParameter("page", page.toString()).build())

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("search").addPathSegment("")
        val fields = listOf("tags" to query) + filters.filterIsInstance<TermFilter>().map { it.type to it.state }
        for ((type, text) in fields) {
            val ids = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { term ->
                val name = term.removePrefix("-").trim()
                val id = findTerm(type, name) ?: return MangasPage(emptyList(), false)
                "${if (term.startsWith('-')) '-' else '+'}$id,"
            }
            url.addQueryParameter(SEARCH_KEYS.getValue(type), ids.joinToString(""))
        }
        return getPostList(url.addQueryParameter("page", page.toString()).build())
    }

    // The lookup is a full-text search: punctuation reads as operators, and words under three letters make it return nothing
    private suspend fun findTerm(type: String, name: String): String? {
        val words = name.words()
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("inc/search.php")
            .addQueryParameter("type", type)
            .addQueryParameter("name", words.split(' ').filter { it.length > 2 }.joinToString(" "))
            .build()
        return client.get(url, lookupHeaders, Fetch.XHR).parseAs<List<Term>>()
            .firstOrNull { Parser.unescapeEntities(it.name, false).words() == words }?.id
    }

    private fun String.words() = lowercase().replace(PUNCTUATION, " ").trim()

    private suspend fun getPostList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return MangasPage(postMangas(document, ".box_thumbs"), document.selectFirst(".pagination li:last-child:not(.disabled)") != null)
    }

    // Videos can't be shown, and blurred posts are kept for members who allow all content
    private fun postMangas(document: Document, list: String) = document.select("$list > .box_thumb:not(.blacklisted):not(:has(.box_preview.mp4))").map { item ->
        SManga.create().apply {
            url = item.selectFirst("a")!!.absUrl("href").toHttpUrl().pathSegments[1]
            title = "Post $url"
            thumbnail_url = item.selectFirst("[data-bg]")?.absUrl("data-bg")
        }
    }

    // Tag, artist, character, parody, metadata and search links open their lists
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] == "post") return super.getMangasByUrl(url, page)
        return getPostList(url.newBuilder().setQueryParameter("page", page.toString()).build())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "post") return null
        val manga = SManga.create().apply { this.url = url.pathSegments[1] }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        manga.apply {
            title = "Post $url"
            artist = document.select(".artists_list a").joinToString { it.text() }.ifEmpty { null }
            author = artist
            genre = document.select(".tags_list a").joinToString { it.text() }.ifEmpty { null }
            description = MarkdownDescription.Builder()
                .field("Parodies", document.select(".parodies_list a").map { it.text() })
                .field("Characters", document.select(".characters_list a").map { it.text() })
                .build()
            thumbnail_url = document.postImage() ?: thumbnail_url
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Post"
        }
        return SMangaUpdate(manga, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val image = document.postImage() ?: return emptyList()
        // Like the site's "Original image size" button
        val originalType = document.selectFirst("button.expand")?.attr("data-type")
        val imageUrl = originalType?.let { image.substringBeforeLast('.').replace("/samples/", "/original/") + ".$it" } ?: image
        return listOf(Page(0, imageUrl = imageUrl))
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = postMangas(client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup(), ".related_posts .box_thumbs")

    override fun getMangaUrl(manga: SManga): String = postUrl(manga.url)

    override fun getChapterUrl(chapter: SChapter): String = postUrl(chapter.url)

    override fun getFilterList(data: JsonElement?) = getFilters()

    // Members-only posts show a login notice over a blurred image, videos have no image
    private fun Document.postImage() = selectFirst(".preview_image img").takeIf { selectFirst(".post_image .alert") == null }?.absUrl("data-src")

    private fun postUrl(id: String) = baseUrl.toHttpUrl().newBuilder().addPathSegment("post").addPathSegment(id).addPathSegment("").build().toString()

    companion object {
        private val SEARCH_KEYS = mapOf(
            "tags" to "qt_key",
            "artists" to "qa_key",
            "characters" to "qc_key",
            "parodies" to "qp_key",
            "metadatas" to "qm_key",
        )
        private val PUNCTUATION = Regex("""[^\p{L}\p{N}]+""")
    }
}
