package eu.kanade.tachiyomi.extension.pt.hentaifusion

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class HentaiFusion : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) addPathSegments("page/$page")
        }.build()
        val document = client.get(url).asJsoup()
        return parseListing(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.selectedSlug()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (genre != null) {
                addPathSegments("category/$genre")
                if (page > 1) addPathSegments("page/$page")
            } else {
                if (query.isNotBlank()) addQueryParameter("s", query)
                if (page > 1) addQueryParameter("paged", page.toString())
            }
        }.build()
        val document = client.get(url).asJsoup()
        return parseListing(document)
    }

    private fun parseListing(document: Document): MangasPage {
        val host = baseUrl.toHttpUrl().host
        val mangas = document.select("div.lista > ul > li").mapNotNull { item ->
            val anchor = item.selectFirst("div.thumb-conteudo > a") ?: return@mapNotNull null
            // First listing item is an ad pointing to an external site (e.g. t.me).
            if (anchor.attr("abs:href").toHttpUrlOrNull()?.host != host) return@mapNotNull null
            val title = anchor.attr("title").ifBlank {
                item.selectFirst("span.thumb-titulo")?.text().orEmpty()
            }.trim()
            if (title.isEmpty()) return@mapNotNull null
            SManga.create().apply {
                url = anchor.attr("abs:href")
                this.title = title
                thumbnail_url = anchor.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("a:contains(Próxima página)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(GenreFilter())

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        return SManga.create().apply {
            this.url = url.toString()
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(manga.url).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        url = document.location()
        title = document.selectFirst("h1.post-titulo")?.text()?.trim().orEmpty()
        require(title.isNotEmpty()) { "Manga title was empty: ${document.location()}" }
        thumbnail_url = document.selectFirst("div.post-capa img")?.attr("abs:src")
        val synopsis = document.selectFirst("div.post-texto p")?.text()?.trim().orEmpty()
        val meta = document.select("ul.post-itens li")
            .map { it.text().trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }
        description = (listOf(synopsis).filter { it.isNotEmpty() } + meta)
            .joinToString("\n\n")
            .ifEmpty { null }
        genre = document.select("ul.post-itens a[href*='/category/'], ul.post-itens a[href*='/tag/']")
            .map { it.text().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")
            .ifEmpty { null }
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val published = document.selectFirst("meta[property='article:published_time']")?.attr("content")
        return listOf(
            SChapter.create().apply {
                url = document.location()
                name = "Oneshot"
                date_upload = Instant.tryParse(published)
            },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(chapter.url).asJsoup()
        return document.select("ul.post-fotos > li > img")
            .mapNotNull { img ->
                val url = img.attr("data-src")
                    .ifBlank { img.attr("abs:src") }
                url.takeIf { it.startsWith("http") }
            }
            .distinct()
            .mapIndexed { index, url -> Page(index, imageUrl = url) }
    }

    private class GenreFilter : Filter.Select<String>("Gênero", GENRES.map { it.second }.toTypedArray()) {
        fun selectedSlug(): String? = GENRES[state].first.ifEmpty { null }
    }

    companion object {
        private val GENRES = listOf(
            "" to "Todos",
            "doujin" to "Doujin",
            "hentai" to "Hentai",
            "hq-hentai" to "HQ Hentai",
            "mangas-hentai" to "Mangás Hentai",
            "quadrinhos-eroticos" to "Quadrinhos Eróticos",
        )
    }
}
