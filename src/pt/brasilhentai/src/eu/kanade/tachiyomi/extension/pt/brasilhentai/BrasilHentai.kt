package eu.kanade.tachiyomi.extension.pt.brasilhentai

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Instant

@Source
abstract class BrasilHentai : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = rateLimit(4)

    override suspend fun getPopularManga(page: Int) = popularMangaParse(client.get("$baseUrl/page/$page").asJsoup())

    private fun popularMangaParse(document: Document): MangasPage {
        val mangas = document.select(".content-area article").map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst("a[title]")!!
                title = anchor.attr("title")
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(anchor.absUrl("href"))
            }
        }
        val hasNextPage = document.selectFirst(".next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selectedValue() ?: ""

        val url = if (category.isEmpty()) {
            "$baseUrl/page/$page".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .build()
        } else {
            "$baseUrl/category/$category/page/$page/".toHttpUrl()
        }

        return popularMangaParse(client.get(url).asJsoup())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.count(String::isNotBlank) < 1) return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            SManga.create().apply {
                url = manga.url
                title = doc.selectFirst("h1")!!.ownText()
                thumbnail_url = doc.selectFirst(".entry-content p a img")?.absUrl("src")
                description = doc.selectFirst(".entry-content")?.text()
                genre = doc.select(".cat-links a").eachText().joinToString()

                author = doc.selectFirst(".author")?.text()
                status = SManga.COMPLETED
            },
            listOf(
                SChapter.create().apply {
                    name = "Capítulo único"
                    url = manga.url
                    date_upload = Instant.tryParse(
                        doc.selectFirst(".entry-date.published")?.attr("datetime"),
                    )
                },
            ),
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".entry-content p > img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get(baseUrl).asJsoup().select("#categories-2 li a").associate { element ->
        val url = element.absUrl("href")
        val category = url.split("/").filter { it.isNotBlank() }.last()
        element.ownText() to category
    }.toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        listOfNotNull(
            data?.parseAs<Map<String, String>>()
                ?.takeIf { it.isNotEmpty() }
                ?.let { CategoryFilter("Categoria", it.toList()) },
        ),
    )
}
