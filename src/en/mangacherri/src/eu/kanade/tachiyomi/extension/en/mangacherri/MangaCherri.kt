package eu.kanade.tachiyomi.extension.en.mangacherri

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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class MangaCherri : KeiSource() {
    override fun OkHttpClient.Builder.configureClient() = rateLimit(5)

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page) { addQueryParameter("sort", "popular") }

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page) { addQueryParameter("sort", "updated") }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = browse(page) {
        if (query.isNotBlank()) addQueryParameter("q", query)
        filters.filterIsInstance<QueryFilter>().forEach { it.addToUrl(this) }
    }

    private suspend fun browse(page: Int, params: HttpUrl.Builder.() -> Unit): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .apply(params)
            .apply { if (page > 1) addQueryParameter("page", page.toString()) }
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val mangas = document.select("article.cherri-list-card").map { card ->
            SManga.create().apply {
                val link = card.selectFirst("h2 a")!!
                this.url = link.pathId()
                title = link.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
                genre = card.select(".cherri-detail__chips a").joinToString { it.text() }.ifEmpty { null }
                status = card.selectFirst("small.eyebrow")?.text().toStatus()
            }
        }
        return MangasPage(mangas, document.selectFirst("nav.pager a:containsOwn(Next)") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null
        val slug = when (url.pathSegments[0]) {
            "title" -> id
            "read" -> {
                val chapter = SChapter.create().apply { this.url = (id.toLongOrNull() ?: return null).toString() }
                client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
                    .selectFirst("[data-cherri-reader]")!!.attrOrNull("data-manga-slug")!!
            }
            else -> return null
        }
        return getMangaUpdate(SManga.create().apply { this.url = slug }, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val details = document.selectFirst(".cherri-detail")!!

        manga.apply {
            title = details.selectFirst("h1")!!.text()
            thumbnail_url = details.selectFirst(".cherri-detail__cover img")?.absUrl("src")
            author = details.selectFirst(".cherri-detail__copy > p > strong")?.text()
            genre = details.select(".cherri-detail__chips a").joinToString { it.text() }.ifEmpty { null }
            status = details.selectFirst(".cherri-detail__copy > p.eyebrow")?.text().toStatus()
            val votes = details.stat("VOTES")?.replace(",", "")?.toIntOrNull()?.takeIf { it > 0 }
            val rating = votes?.let { details.stat("RATING")?.removePrefix("★")?.trim()?.toDoubleOrNull() }
            description = MarkdownDescription.Builder()
                .rating(rating, max = 5, votes = votes)
                .info(document.selectFirst("p.eyebrow:containsOwn(FIRST BLOOMED)")?.text()?.substringAfter("·"))
                .description(details.selectFirst(".cherri-detail__description"))
                .altTitles(details.selectFirst(".cherri-detail__alt")?.text()?.split("; ").orEmpty().filter { it != title })
                .build()
        }

        val chapterList = document.select("[data-chapter-list] a[data-chapter-row]").map { row ->
            SChapter.create().apply {
                url = row.pathId()
                name = row.selectFirst("strong")!!.ownText()
                date_upload = Instant.tryParse(row.selectFirst("time")?.attrOrNull("datetime"))
            }
        }

        return SMangaUpdate(manga, chapterList)
    }

    private fun Element.pathId(): String = absUrl("href").toHttpUrl().pathSegments[1]

    private fun Element.stat(label: String): String? = selectFirst(".cherri-detail__stats > div:has(small:containsOwn($label)) strong")?.text()

    private fun String?.toStatus(): Int {
        val text = this?.lowercase().orEmpty()
        return when {
            "ongoing" in text -> SManga.ONGOING
            "completed" in text -> SManga.COMPLETED
            "hiatus" in text -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        .select(".cherri-related-manga article.manga-card")
        .map { card ->
            SManga.create().apply {
                val link = card.selectFirst("h3 a")!!
                url = link.pathId()
                title = link.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        .select("[data-cherri-reader] img[data-page]")
        .mapIndexed { index, image -> Page(index, imageUrl = image.absUrl("src")) }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment("manga").build()
        return client.get(url, Fetch.DOCUMENT).asJsoup()
            .select("form.cherri-filter select[name=genre] option")
            .mapNotNull { option -> option.attrOrNull("value")?.let { it to option.text() } }
            .toMap()
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<Map<String, String>>().orEmpty()
        return FilterList(
            listOfNotNull(
                genres.takeIf { it.isNotEmpty() }?.let { GenreFilter(it.map { (slug, label) -> label to slug }) },
                StatusFilter(),
                SortFilter(),
            ),
        )
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("title")
        .addPathSegment(manga.url)
        .build()
        .toString()

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment("read")
        .addPathSegment(chapter.url)
        .build()
        .toString()
}
