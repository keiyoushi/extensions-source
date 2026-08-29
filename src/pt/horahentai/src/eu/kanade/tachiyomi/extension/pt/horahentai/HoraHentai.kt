package eu.kanade.tachiyomi.extension.pt.horahentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class HoraHentai : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(2) { !it.encodedPath.startsWith("/wp-content/uploads/") }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchListing("$baseUrl/popular/", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchListing("$baseUrl/", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/${pagePath(page)}".toHttpUrl().newBuilder()
                .addQueryParameter("s", query.trim())
                .build()

            return parseListing(client.get(url).asJsoup())
        }

        val tag = filters.firstInstanceOrNull<TagFilter>()?.selected().orEmpty()
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected().orEmpty()

        return when {
            tag.isNotEmpty() -> fetchListing("$baseUrl/tag/$tag/", page)
            category.isNotEmpty() -> fetchListing("$baseUrl/category/$category/", page)
            else -> getLatestUpdates(page)
        }
    }

    private suspend fun fetchListing(listingUrl: String, page: Int): MangasPage = parseListing(client.get(listingUrl + pagePath(page)).asJsoup())

    private fun pagePath(page: Int): String = if (page > 1) "page/$page/" else ""

    private fun parseListing(document: Document): MangasPage {
        val mangas = document.select(".lista li.novo-card-item a.novo-card-link-img").map { element ->
            SManga.create().apply {
                url = element.absUrl("href").toHttpUrl().encodedPath
                title = element.attr("title")
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }

        return MangasPage(
            mangas,
            hasNextPage = document.selectFirst("ul.paginacao li.next a") != null,
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null

        val manga = SManga.create().apply { this.url = "/$slug/" }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val galleries = document.select("div.galeriaTabItem")
        val date = document.selectFirst("meta[property=article:published_time]")
            ?.attr("content")
            .toTimestamp()

        val updatedManga = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1.post-titulo")!!.text()
            thumbnail_url = document.selectFirst(".post-capa img")?.absUrl("src")
            description = document.selectFirst("h1.post-titulo + .form-group")?.text()
            genre = document.select("ul.post-itens a[href*=/category/] .postTagNome, ul.post-itens a[href*=/tag/] .postTagNome")
                .joinToString { it.text() }
            status = SManga.COMPLETED
            // Galleries are a series that can still receive chapters, unlike a single post.
            update_strategy = if (galleries.isEmpty()) UpdateStrategy.ONLY_FETCH_ONCE else UpdateStrategy.ALWAYS_UPDATE
        }

        val chapters = when {
            galleries.isEmpty() -> listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Capítulo"
                    chapter_number = 1F
                    date_upload = date
                },
            )
            else -> galleries.mapNotNull { it.toSChapter(manga.url, date) }.reversed()
        }

        return SMangaUpdate(updatedManga, chapters)
    }

    private fun Element.toSChapter(mangaUrl: String, date: Long): SChapter? {
        val galleryId = selectFirst("div.galeriaConteudo[id~=^galeria-\\d+$]")?.id() ?: return null
        val label = selectFirst(".galeriaTabCapitulo")?.text().orEmpty()
        val chapterTitle = selectFirst(".galeriaTabTitulo")?.text().orEmpty()

        return SChapter.create().apply {
            // Every gallery lives on the same page, so the anchor is what makes a chapter unique.
            url = "$mangaUrl#$galleryId"
            name = when {
                label.isEmpty() -> chapterTitle.ifEmpty { "Capítulo" }
                chapterTitle.isEmpty() -> label
                else -> "$label: $chapterTitle"
            }
            chapter_number = CHAPTER_NUMBER_REGEX.find(label)?.groupValues?.get(1)?.toFloatOrNull() ?: -1F
            date_upload = date
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val galleryId = chapter.url.substringAfter('#', "")
        val document = client.get(baseUrl + chapter.url.substringBefore('#')).asJsoup()

        val images = when {
            galleryId.isEmpty() -> document.select("ul.post-fotos li img")
            else -> document.select("div.galeriaConteudo#$galleryId img")
        }

        return images.mapIndexed { index, img ->
            val imageUrl = img.absUrl("data-src").ifEmpty { img.absUrl("src") }
            Page(index, imageUrl = imageUrl)
        }
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/tags/").asJsoup()
        .select(".lista-tags a[href*=/tag/]")
        .mapNotNull { element ->
            val slug = element.absUrl("href").toHttpUrl().pathSegments.getOrNull(1)
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val name = element.attr("title").ifEmpty { element.text() }

            name.takeIf { it.isNotEmpty() }?.let { TagDto(it, slug) }
        }
        .distinctBy { it.slug }
        .sortedBy { it.name }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val tags = data?.parseAs<List<TagDto>>()

        return FilterList(
            buildList {
                add(Filter.Header("Filtros são ignorados na busca por texto"))
                add(CategoryFilter())
                if (tags != null) {
                    add(TagFilter(tags))
                }
            },
        )
    }

    private fun String?.toTimestamp(): Long = this?.let { Instant.parseOrNull(it)?.toEpochMilliseconds() } ?: 0L

    companion object {
        private val CHAPTER_NUMBER_REGEX = Regex("""(\d+(?:\.\d+)?)""")
    }
}
