package eu.kanade.tachiyomi.extension.ru.acomics

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.Pair
import kotlin.String
import kotlin.collections.List

@Source
abstract class AComics : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addCookie { listOf("ageRestrict" to "18") }
    }

    // ============================== Popular ===============================
    override suspend fun getPopularManga(page: Int): MangasPage = makeCatalogRequest("subscr_count", page)

    // ============================== Latest ===============================
    override suspend fun getLatestUpdates(page: Int): MangasPage = makeCatalogRequest("last_update", page)

    // ============================== Search ===============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = makeCatalogRequest("subscr_count", page, query, filters)

    // ============================== Search Utilities ===============================
    protected open suspend fun makeCatalogRequest(sortBy: String, page: Int, query: String? = null, filters: FilterList? = null): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query?.isNotBlank() == true) {
                if (query.length < 3) {
                    throw Exception("Запрос должен содержать не менее 3-х символов / The query must contain at least 3 characters")
                }
                addPathSegment("search")
                addQueryParameter("keyword", query)
            } else {
                val segment = filters?.firstInstanceOrNull<Categories>()?.selected ?: "comics"
                addPathSegment(segment)
                filters?.forEach { filter ->
                    when (filter) {
                        is Genres -> filter.selected?.forEach { addQueryParameter("categories[]", it) }
                        is AgeRatings -> filter.selected?.forEach { addQueryParameter("ratings[]", it) }
                        is ComicType -> filter.selected?.let { addQueryParameter("type", it) }
                        is Publication -> filter.selected?.let { addQueryParameter("updatable", it) }
                        is Subscription -> filter.selected?.let { addQueryParameter("subscribe", it) }
                        is OrderBy -> filter.selected?.let { addQueryParameter("sort", it) }
                        is MinPages -> addQueryParameter("issue_count", filter.state.toIntOrNull()?.coerceIn(0, 9999)?.toString() ?: "2")
                        else -> {}
                    }
                }
                if (filters == null) {
                    addQueryParameter("sort", sortBy)
                    (1..5).forEach { addQueryParameter("ratings[]", it.toString()) }
                    listOf(
                        "type" to "0",
                        "updatable" to "0",
                        "subscribe" to "0",
                        "issue_count" to "2",
                    ).forEach { (key, value) -> addQueryParameter(key, value) }
                }
            }
            if (page > 1) addQueryParameter("skip", ((page - 1) * 10).toString())
        }.build()

        return client.get(url).use { response ->
            val document = response.asJsoup()
            val mangas = document.select("section.serial-card").map { element ->
                SManga.create().apply {
                    thumbnail_url = element.selectFirst("a > img")?.absUrl("data-real-src")
                    element.selectFirst("h2 > a")!!.run {
                        setUrlWithoutDomain(attr("href") + "/about")
                        title = text()
                    }
                }
            }
            val hasNextPage = document.selectFirst("a.infinite-scroll") != null
            MangasPage(mangas, hasNextPage)
        }
    }

    // =========================== Deeplink ============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = null

    // =========================== Manga ============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val data = client.get(getMangaUrl(manga)).asJsoup()
        val newManga = mangaDetailsParse(data)
        val newChapters = chapterListParse(data, manga.url)

        return SMangaUpdate(newManga, newChapters)
    }

    // =========================== Manga Details ============================
    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val article = document.selectFirst("article.common-article")!!
        with(article) {
            title = selectFirst(".page-header-with-menu h1")!!.text()
            genre = select("p.serial-about-badges a.category").joinToString { it.text() }
            author = select("p.serial-about-authors a, p:contains(Автор оригинала)").joinToString { it.ownText().trim() }
            description = selectFirst("section.serial-about-text")?.text()
            status = if (selectFirst("p.serial-about-badges span.completed") != null) SManga.COMPLETED else SManga.ONGOING
        }
    }

    // ============================== Chapters ==============================
    private fun chapterListParse(doc: Document, mangaUrl: String): List<SChapter> {
        val count = doc
            .selectFirst("p:has(b:contains(Количество выпусков:))")!!
            .ownText()
            .toInt()

        val comicPath = mangaUrl.substringBefore("/about")

        return (count downTo 1).map {
            SChapter.create().apply {
                chapter_number = it.toFloat()
                name = it.toString()
                setUrlWithoutDomain("$comicPath/$it")
            }
        }
    }

    // =============================== Pages ================================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val imageElement = document.selectFirst("img.issue")!!
        return listOf(Page(0, imageUrl = imageElement.absUrl("src")))
    }

    // ============================== Filters ===============================
    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val data = client.get("$baseUrl/comics").asJsoup()
        return Dto(
            categories = data.getFilter("categories"),
            ageRatings = data.getFilter("age-ratings"),
            type = data.getFilter("type"),
            subscribe = data.getFilter("subscribe"),
            status = data.getFilter("updatable"),
            sort = data.select("select[name=sort] option").map { e -> e.text() to e.attr("value").trim() },
        ).toJsonElement()
    }

    private fun Document.getFilter(query: String): List<Pair<String, String>> = select(".$query label").mapNotNull { element ->
        val value = element.selectFirst("input")?.attr("value")?.trim() ?: return@mapNotNull null
        element.text() to value
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filters = mutableListOf<Filter<*>>()
        filters.add(Categories())
        data?.parseAs<Dto>()?.let {
            if (it.sort?.isNotEmpty() == true) filters.add(OrderBy(it.sort))
            if (it.categories?.isNotEmpty() == true) filters.add(Genres(it.categories))
            if (it.ageRatings?.isNotEmpty() == true) filters.add(AgeRatings(it.ageRatings))
            if (it.type?.isNotEmpty() == true) filters.add(ComicType(it.type))
            if (it.status?.isNotEmpty() == true) filters.add(Publication(it.status))
            if (it.subscribe?.isNotEmpty() == true) filters.add(Subscription(it.subscribe))
        }
        filters.add(MinPages())
        return FilterList(filters)
    }
}
