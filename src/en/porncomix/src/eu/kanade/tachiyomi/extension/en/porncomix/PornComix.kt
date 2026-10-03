package eu.kanade.tachiyomi.extension.en.porncomix

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class PornComix : KeiSource() {

    override val supportsLatest = false

    // ======================== Popular ========================

    override suspend fun getPopularManga(page: Int): MangasPage = if (page == 1) {
        parseMangaList("$baseUrl/multporn-net/".toHttpUrl())
    } else {
        parseMangaList("$baseUrl/multporn-net/page/$page/".toHttpUrl())
    }

    private suspend fun parseMangaList(url: HttpUrl): MangasPage {
        val document = client.get(url).asJsoup()
        val mangas = document.select("#loops-wrapper article").map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst("h2.post-title a")!!
                setUrlWithoutDomain(anchor.attr("href"))
                title = anchor.text()

                thumbnail_url = element.selectFirst("img")?.let { img ->
                    img.absUrl("data-pagespeed-lazy-src")
                        .ifEmpty { img.absUrl("data-src") }
                        .ifEmpty { img.absUrl("src") }
                }
            }
        }
        val hasNextPage = document.selectFirst("a.nextp") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ======================== Latest (disabled) ========================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ======================== Search ========================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addQueryParameter("s", query.trim())
        }.build()

        return parseMangaList(url)
    }

    // ======================== Details ========================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails) fetchMangaDetails(manga) else manga
        val chapterList = if (fetchChapters) {
            listOf(
                SChapter.create().apply {
                    name = "CHAPTER"
                    setUrlWithoutDomain(manga.url)
                },
            )
        } else {
            chapters
        }

        return SMangaUpdate(details, chapterList)
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        manga.title = document.selectFirst("h1.post-title, h1.entry-title")!!.text()

        manga.thumbnail_url = document.selectFirst(
            "div.post-inner img, div.entry-content img, article img",
        )?.let { img ->
            img.absUrl("data-pagespeed-lazy-src")
                .ifEmpty { img.absUrl("data-src") }
                .ifEmpty { img.absUrl("src") }
        }

        manga.description = document.selectFirst(
            "div.entry-content p, div.post-content p",
        )?.text()

        // Tags / genres
        val tags = document.select("a[rel=tag], .post-tags a, .tags-links a")
            .map { it.text() }
            .filter { it.isNotEmpty() }
        if (tags.isNotEmpty()) {
            manga.genre = tags.joinToString()
        }

        manga.status = SManga.COMPLETED
        manga.update_strategy = UpdateStrategy.ONLY_FETCH_ONCE

        return manga
    }

    // ======================== Pages ========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val pswp = document.select(".pswp-gallery__item")

        if (pswp.isNotEmpty()) {
            return pswp.mapIndexed { index, element ->
                val url = element.absUrl("data-pswp-src")
                Page(index, imageUrl = url)
            }
        }

        val images = document.select("div.entry-content img")

        return images.mapIndexed { index, img ->
            val url = img.absUrl("data-pagespeed-lazy-src")
                .ifEmpty { img.absUrl("data-src") }
                .ifEmpty { img.absUrl("src") }

            Page(index, imageUrl = url)
        }
    }
}
