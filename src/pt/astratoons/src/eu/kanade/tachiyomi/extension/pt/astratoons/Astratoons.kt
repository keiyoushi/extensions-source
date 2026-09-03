package eu.kanade.tachiyomi.extension.pt.astratoons

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
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup

@Source
abstract class Astratoons : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()

        val mangas = document.select("#comicsSlider a").map { element ->
            SManga.create().apply {
                title = element.selectFirst("h3")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
        return MangasPage(mangas, hasNextPage = false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addQueryParameter("sortBy", "updated_at")
            .addQueryParameter("page", page.toString())
            .build()

        val response = client.get(url)

        return getMangasPage(response)
    }

    private fun getMangasPage(response: Response): MangasPage {
        val dto = response.parseAs<ComicsResponseDto>()
        val mangas = dto.data.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, dto.currentPage < dto.lastPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/comics".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("search", query)
        }

        filters.firstInstanceOrNull<SortFilter>()?.let {
            url.addQueryParameter("sortBy", it.toQuery())
        }

        filters.firstInstanceOrNull<StatusFilter>()?.toQuery()
            ?.takeIf { it.isNotEmpty() }
            ?.let { url.addQueryParameter("status", it) }

        filters.firstInstanceOrNull<TypeFilter>()?.state
            ?.filter { it.state }
            ?.forEach { url.addEncodedQueryParameter("types[]", it.value) }

        filters.firstInstanceOrNull<TagFilter>()?.state
            ?.filter { it.state }
            ?.forEach { url.addEncodedQueryParameter("tags[]", it.value) }

        return getMangasPage(client.get(url.build()))
    }

    override suspend fun fetchMangaUpdate(manga: SManga, chapters: List<SChapter>, fetchDetails: Boolean, fetchChapters: Boolean): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        manga.apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst("img[class*=object-cover]")?.absUrl("src")
            description = document.selectFirst("div.space-y-4 > p")?.text().takeUnless(String?::isNullOrBlank)
                ?: document.selectFirst("div:has(>h1) + div")?.text()
            genre = document.select("h3:contains(Tags) + div a").joinToString { it.text() }
            author = document.selectFirst("span:contains(Autor) > span")?.text()
            artist = document.selectFirst("span:contains(Artista) > span")?.text()

            val statusText = document.selectFirst("h3:contains(Informações) + div span.capitalize")?.text()
            status = when (statusText?.lowercase()) {
                "em andamento", "em dia" -> SManga.ONGOING
                "completo" -> SManga.COMPLETED
                "hiato" -> SManga.ON_HIATUS
                "cancelado", "dropado" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }

            memo = buildJsonObject {
                put("id", MANGA_ID.find(document.html())?.groupValues[1])
            }
        }

        val chapters = when {
            fetchChapters || manga.memo["id"] != null -> fetchChapters(manga)
            else -> chapters
        }

        return SMangaUpdate(manga, chapters)
    }

    private suspend fun fetchChapters(manga: SManga): List<SChapter> {
        var page = 1
        var hasMore = true
        val chapters = mutableListOf<SChapter>()

        while (hasMore) {
            val url = "$baseUrl/api/comics/${manga.memo["id"]!!.int}/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("search", "")
                .addQueryParameter("order", "desc")
                .addQueryParameter("page", page.toString())
                .build()

            val response = client.get(url)
            val dto = response.parseAs<ChapterListDto>()

            val fragment = Jsoup.parseBodyFragment(dto.html, baseUrl)
            chapters += fragment.select("a").map { element ->
                SChapter.create().apply {
                    name = element.selectFirst(".text-lg")?.text() ?: "Chapter"
                    setUrlWithoutDomain(element.absUrl("href"))
                }
            }

            hasMore = dto.hasMore
            page++
        }

        return chapters
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#reader-container img[src], #reader-container canvas[data-src]")
            .mapIndexed { index, element ->
                val imageUrl = element.absUrl("src").ifEmpty { element.absUrl("data-src") }
                Page(index, document.location(), imageUrl)
            }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Referer", page.url)
            .build()

        return super.imageRequest(page).newBuilder()
            .headers(imageHeaders)
            .build()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        TypeFilter(),
        TagFilter(),
    )

    companion object {
        val MANGA_ID = """comicId:\s*(\d+)""".toRegex()
    }
}
