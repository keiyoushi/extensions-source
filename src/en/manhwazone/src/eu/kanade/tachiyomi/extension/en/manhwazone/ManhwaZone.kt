package eu.kanade.tachiyomi.extension.en.manhwazone

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ManhwaZone : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/series?sortBy=popularity&page=$page").asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/series?sortBy=latest&page=$page").asJsoup())

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !url.encodedPath.startsWith("/series/")) return null

        return parseMangaDetails(client.get(url).asJsoup()).apply {
            this.url = url.encodedPath
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("keyword", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> url.addQueryParameter("sortBy", filter.toUriPart())
                is StatusFilter -> {
                    val status = filter.toUriPart()
                    if (status.isNotEmpty()) {
                        url.addQueryParameter("status", status)
                    }
                }
                is GenreFilterGroup -> {
                    val selectedGenres = filter.state
                        .filter { it.state }
                        .map { it.slug }
                    if (selectedGenres.isNotEmpty()) {
                        url.addQueryParameter("genres", selectedGenres.joinToString("_"))
                    }
                }
                else -> {}
            }
        }

        return parseMangaList(client.get(url.build()).asJsoup())
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("article.group").map { element ->
            SManga.create().apply {
                title = element.selectFirst(".min-w-0 > a.font-semibold")!!.text()
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("a[rel=next], nav a:contains(›)") != null || mangas.size >= 24
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = parseMangaDetails(document).apply { url = manga.url }
        val chapterList = if (fetchChapters) fetchChapterList(document) else chapters

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun parseMangaDetails(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.selectFirst("h1.page-title")!!.text()
        manga.description = document.selectFirst("p.page-subtitle")?.text()
        manga.thumbnail_url = document.selectFirst("img.aspect-\\[7\\/10\\], figure.relative img")?.attr("abs:src")
        manga.genre = document.select("a.badge-genre").joinToString { it.text() }

        val statusText = document.selectFirst("span.badge-sm, span:contains(On Going), span:contains(Completed)")?.text()?.trim()
        manga.status = when (statusText?.lowercase()) {
            "on going", "ongoing", "currently publishing" -> SManga.ONGOING
            "completed", "finished" -> SManga.COMPLETED
            "on hiatus" -> SManga.ON_HIATUS
            "discontinued", "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        val jsonLd = document.selectFirst("script[type=application/ld+json]")?.data()
        if (jsonLd != null) {
            val authorMatch = authorRegex.find(jsonLd)?.groupValues?.get(1)
            if (authorMatch != null && authorMatch.lowercase() != "unknown") {
                manga.author = authorMatch
            }
        }

        return manga
    }

    private val jsonHeaders get() = headers.newBuilder()
        .add("Accept", "application/json")
        .build()

    private suspend fun fetchChapterList(document: Document): List<SChapter> {
        val wireDiv = document.selectFirst("div[wire:snapshot][wire:id][wire:init=bootLoad]")
            ?: return emptyList()

        val csrfToken = document.selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
        val snapshot = wireDiv.attr("wire:snapshot")

        val payload = LivewireRequestDto(
            token = csrfToken,
            components = listOf(
                LivewireRequestComponentDto(
                    snapshot = snapshot,
                    updates = JsonObject(emptyMap()),
                    calls = listOf(LivewireCallDto(path = "", method = "bootLoad", params = emptyList())),
                ),
            ),
        )

        val postResponse = client.post("$baseUrl/livewire/update", jsonHeaders, payload.toJsonRequestBody(), ensureSuccess = false)
        if (!postResponse.isSuccessful) {
            postResponse.close()
            return emptyList()
        }

        val updateDto = postResponse.parseAs<LivewireUpdateDto>()
        val snapshotStr = updateDto.components.firstOrNull()?.snapshot ?: return emptyList()
        val snapshotDto = snapshotStr.parseAs<SnapshotDto>()

        // Livewire serializes collections as [value, meta] tuples
        val actualChapters = snapshotDto.data?.chapters?.getOrNull(0)?.parseAs<List<List<JsonElement>>>()
            ?: return emptyList()

        return actualChapters.mapNotNull { chapterTuple ->
            val chapterDto = chapterTuple.getOrNull(0)?.parseAs<ChapterDto>() ?: return@mapNotNull null
            val webUrl = chapterDto.webUrl ?: return@mapNotNull null

            SChapter.create().apply {
                url = webUrl
                name = chapterDto.name ?: "Chapter"
                date_upload = dateFormat.tryParseDateTime(chapterDto.published)
            }
        }
    }

    // The __RS_CONF__ image host (img.mangalaxy.net) no longer resolves; the page also lists the images directly
    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get(getChapterUrl(chapter)).asJsoup()
        .select("img.lazy-image[data-src]")
        .mapIndexed { i, element -> Page(i, imageUrl = element.attr("abs:data-src")) }

    // The image CDN answers 403 when the Referer is the site
    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .removeHeader("Referer")
        .build()

    // ── Filters ───────────────────────────────────────────────────────────────

    override fun getFilterList(data: JsonElement?) = FilterList(
        SortFilter(),
        StatusFilter(),
        GenreFilterGroup(getGenreList()),
    )

    companion object {
        private val authorRegex = """"author":\s*\[\s*\{"@type":"Person","name":"([^"]+)"""".toRegex()
    }
}
