package eu.kanade.tachiyomi.extension.ja.mangarw

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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MangaRw : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/?page=$page").asJsoup()
        return parseMangaList(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/browse?page=$page").asJsoup()
        return parseMangaList(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("search", query)
            .build()
        val document = client.get(url).asJsoup()
        return parseMangaList(document)
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("a[href^=/manga/]").mapNotNull { element ->
            val href = element.attr("href")
            if (!href.matches(Regex("/manga/[^/]+/\\d+"))) return@mapNotNull null
            SManga.create().apply {
                url = href
                title = element.attr("title").ifBlank { element.text().trim() }
                thumbnail_url = element.selectFirst("img")?.let {
                    it.attr("data-src").ifBlank { it.attr("src") }
                }
            }
        }.distinctBy { it.url }

        val hasNext = document.selectFirst("a[href*=page=${getNextPage(document)}]") != null
        return MangasPage(mangas, hasNext)
    }

    private fun getNextPage(document: Document): Int {
        val current = document.location().toHttpUrl().queryParameter("page")?.toIntOrNull() ?: 1
        return current + 1
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = async {
            if (fetchDetails) {
                val document = client.get(baseUrl + manga.url).asJsoup()
                manga.apply {
                    title = document.selectFirst("h1")?.text()?.trim() ?: title
                    thumbnail_url = document.selectFirst("img[src*=covers]")?.absUrl("src")
                    description = document.selectFirst("div.description, p.synopsis")?.text()?.trim()
                    genre = document.select("a[href*=genre]").joinToString { it.text().trim() }
                }
            } else {
                manga
            }
        }

        val chaptersDeferred = async {
            if (fetchChapters) {
                val mangaId = "$baseUrl${manga.url}".toHttpUrl().pathSegments.last()
                val response = client.get("$baseUrl/api/v1/chapters?id=$mangaId").asJsoup()
                response.select("option").mapNotNull { option ->
                    val name = option.text().trim()
                    if (name.isEmpty() || option.attr("value").isEmpty()) return@mapNotNull null
                    SChapter.create().apply {
                        url = "/read?id=${option.attr("value")}"
                        this.name = name
                        parseChapterNumber(name)?.let { chapter_number = it }
                    }
                }
            } else {
                chapters
            }
        }

        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    private fun parseChapterNumber(name: String): Float? {
        val match = Regex("第(\\d+(?:\\.\\d+)?)話").find(name) ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return document.select("img[src*=mangabuzz], img[data-src*=mangabuzz]").mapIndexed { index, img ->
            val imageUrl = img.attr("data-src").ifBlank { img.attr("src") }
            Page(index, imageUrl = imageUrl)
        }.distinctBy { it.imageUrl }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList()
}
