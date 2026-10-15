package eu.kanade.tachiyomi.extension.en.mangabolt

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
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder

@Source
abstract class MangaBolt : KeiSource() {

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList(page, "")

    // The site 301s any path without a trailing slash by appending "/" to the whole URL, so a
    // throwaway "_=/" parameter keeps the real query parameters intact.
    private suspend fun getMangaList(page: Int, query: String): MangasPage {
        val url = buildString {
            append("$baseUrl/manga-list/?page=$page")
            if (query.isNotEmpty()) append("&search=").append(URLEncoder.encode(query, "UTF-8"))
            append("&_=/")
        }
        val apiHeaders = headers.newBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .set("Accept", "application/json")
            .build()
        val data = client.get(url, apiHeaders).parseAs<MangaListDto>()
        val mangas = data.mangas.map {
            SManga.create().apply {
                this.url = "/manga/${it.slug}/"
                title = it.name
                thumbnail_url = it.imageUrl
            }
        }
        return MangasPage(mangas, data.nextPageUrl != null)
    }

    @Serializable
    class MangaListDto(
        val mangas: List<MangaDto>,
        @SerialName("next_page_url") val nextPageUrl: String? = null,
    )

    @Serializable
    class MangaDto(
        val name: String,
        val slug: String,
        @SerialName("image_url") val imageUrl: String? = null,
    )

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/latest").asJsoup()
        val mangas = document.select("div.bg-bg-secondary:has(a[href*=/chapter/])").asSequence().mapNotNull { element ->
            val link = element.selectFirst("a[href*=/chapter/]")?.attr("href") ?: return@mapNotNull null

            val slug = link.substringAfter("/chapter/", "").substringBefore("-chapter-", "")
            if (slug.isEmpty()) return@mapNotNull null

            SManga.create().apply {
                url = "/manga/$slug/"
                title = element.select(".font-bold").text().substringBefore("Chapter").trim()
                if (title.isEmpty()) return@mapNotNull null
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }.distinctBy { it.url }.toList()

        return MangasPage(mangas, false)
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = getMangaList(page, query.trim())

    // Details & Chapters
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        manga.apply {
            title = document.selectFirst("#main-content h1")?.text()?.trim()?.takeIf { it.isNotEmpty() } ?: throw Exception("Missing title")
            description = document.select("div.bg-bg-secondary div.px-6 div.flex-col div.text-text-muted").text().trim()
            thumbnail_url = document.selectFirst("div.flex img")?.attr("abs:src")
        }

        val chapterList = document.select("div.w-full div.bg-bg-secondary:has(div.grid)").mapNotNull { element ->
            val link = element.selectFirst("div.grid a") ?: return@mapNotNull null
            SChapter.create().apply {
                name = link.text()
                val secondaryTitle = link.parent()?.selectFirst(".text-xs")?.text()?.takeIf { !it.equals("READ", ignoreCase = true) } ?: ""
                if (secondaryTitle.isNotEmpty()) {
                    name += " - $secondaryTitle"
                }
                url = link.attr("abs:href")
            }
        }

        return SMangaUpdate(manga, chapterList)
    }

    // Pages
    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".js-pages-container img.js-page").asSequence()
            .filter { !it.parents().any { parent -> parent.tagName() == "noscript" } }
            .map { img ->
                if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src")
            }
            .filter { it.isNotEmpty() && !it.contains("data:image") }
            .distinct()
            .mapIndexed { index, url -> Page(index, "", url) }
            .toList()
    }
}
