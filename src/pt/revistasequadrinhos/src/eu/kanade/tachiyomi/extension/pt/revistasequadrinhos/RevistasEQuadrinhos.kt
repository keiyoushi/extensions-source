package eu.kanade.tachiyomi.extension.pt.revistasequadrinhos

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Source
abstract class RevistasEQuadrinhos : KeiSource() {

    // ============================== Popular ==============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("category")
            addPathSegment("popular-comics")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        return fetchMangaList(url)
    }

    private suspend fun fetchMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()

        val mangas = document.select("ul.videos > li").map { element ->
            val a = element.selectFirst("a.titulo") ?: throw Exception("Manga URL is mandatory")
            val img = element.selectFirst("div.thumb-conteudo img")

            SManga.create().apply {
                title = a.text()
                setUrlWithoutDomain(a.attr("href"))
                thumbnail_url = img?.attr("abs:src")
            }
        }

        val hasNextPage = document.selectFirst(".paginacao li.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        return fetchMangaList(url)
    }

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addQueryParameter("s", query)
            }.build()

            return fetchMangaList(url)
        }

        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val tagFilter = filters.firstInstanceOrNull<TagFilter>()

        var path = ""
        if (categoryFilter != null && categoryFilter.toUriPart().isNotEmpty()) {
            path = "category/${categoryFilter.toUriPart()}"
        } else if (tagFilter != null && tagFilter.toUriPart().isNotEmpty()) {
            path = "tag/${tagFilter.toUriPart()}"
        }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (path.isNotEmpty()) {
                addPathSegments(path)
            }
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        return fetchMangaList(url)
    }

    // ============================== Details ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val chapterUrl = response.request.url.encodedPath
        val document = response.asJsoup()

        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst(".post-conteudo h1")?.text() ?: throw Exception("Manga title is mandatory")
            description = document.select(".post-texto p").joinToString("\n") { it.text() }
            genre = document.select(".post-tags a").joinToString { it.text() }
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")

            // Site treats each post/comic as a single entity, usually completed once uploaded.
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

        val chapter = SChapter.create().apply {
            name = "Capítulo Único"
            url = chapterUrl
            date_upload = Instant.tryParse(document.selectFirst("meta[property=article:published_time]")?.attr("content"))
        }

        return SMangaUpdate(details, listOf(chapter))
    }

    // =============================== Pages ===============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        val gallery = document.select("div.dgwt-jg-gallery figure.dgwt-jg-item a")
        if (gallery.isNotEmpty()) {
            return gallery.mapIndexed { index, a ->
                Page(index, imageUrl = a.attr("abs:href"))
            }
        }

        return document.select(".post-texto img").mapIndexed { index, img ->
            val url = img.attr("abs:src").ifEmpty { img.attr("abs:data-lazy-src") }
            Page(index, imageUrl = url)
        }
    }

    // ============================== Filters ==============================
    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Nota: Ignorado se usar pesquisa de texto."),
        Filter.Separator(),
        CategoryFilter(),
        Filter.Separator(),
        Filter.Header("Nota: Ignorado se escolher uma categoria."),
        TagFilter(),
    )
}
