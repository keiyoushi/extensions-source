package eu.kanade.tachiyomi.extension.fr.chaostrad

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
import keiyoushi.utils.tryParseDate
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("d.M.yyyy")

@Source
abstract class ChaosTrad : KeiSource() {

    override val supportsLatest = false

    private fun normalizeSeriesTitle(rawTitle: String): String = rawTitle
        .removePrefix("Chapitre de ")
        .removePrefix("Voir le chapitre ")
        .substringBeforeLast(" #")
        .trim()

    private fun formatChapterName(chapterNumber: Float): String {
        val number = if (chapterNumber >= 0f && chapterNumber % 1f == 0f) {
            chapterNumber.toInt().toString()
        } else if (chapterNumber >= 0f) {
            chapterNumber.toString()
        } else {
            "?"
        }
        return "#$number"
    }

    // ========================= Catalog helper =============================

    /**
     * Builds the series list by reading the comics navigation menu.
     * Collection links (/search/...) are followed to discover their constituent series.
     */
    private suspend fun parseCatalog(): List<SManga> {
        val mangaList = mutableListOf<SManga>()
        val addedUrls = mutableSetOf<String>()

        val document = client.get(baseUrl).asJsoup()
        val submenu = document.selectFirst("#comics-main")?.nextElementSibling()

        submenu?.select("a[href]")?.forEach { link ->
            val href = link.attr("href").trim()
            val title = normalizeSeriesTitle(link.attr("title"))

            when {
                href.startsWith("/comics/") -> {
                    if (addedUrls.add(href)) {
                        mangaList.add(
                            SManga.create().apply {
                                this.title = title
                                this.url = href
                            },
                        )
                    }
                }
                href.startsWith("/search/") -> {
                    // May be a collection page or a redirect to a single series
                    val subResponse = client.get("$baseUrl$href")
                    val finalPath = subResponse.request.url.encodedPath
                    val subDoc = subResponse.asJsoup()

                    if (finalPath.startsWith("/search/")) {
                        // True collection page: each a.comic-link leads to a series
                        subDoc.select("a.comic-link[href]").forEach { colLink ->
                            val colHref = colLink.absUrl("href").removePrefix(baseUrl)
                            val seriesPath = when {
                                colHref.startsWith("/comics/") -> {
                                    val slug = colHref.split("/").getOrNull(2) ?: return@forEach
                                    "/comics/$slug"
                                }
                                colHref.startsWith("/search/") -> colHref
                                else -> return@forEach
                            }
                            if (addedUrls.add(seriesPath)) {
                                val seriesTitle = normalizeSeriesTitle(colLink.attr("title"))
                                mangaList.add(
                                    SManga.create().apply {
                                        this.title = seriesTitle
                                        this.url = seriesPath
                                    },
                                )
                            }
                        }
                    } else {
                        // Redirected to a series or reader page
                        val parts = finalPath.split("/")
                        val seriesPath = if (parts.size >= 4 && parts[1] == "comics") {
                            "/comics/${parts[2]}"
                        } else {
                            finalPath
                        }
                        if (addedUrls.add(seriesPath)) {
                            val seriesTitle = normalizeSeriesTitle(
                                subDoc.selectFirst("h1")?.text()?.trim()
                                    ?: subDoc.selectFirst("title")?.text().orEmpty(),
                            ).ifBlank { title }
                            mangaList.add(
                                SManga.create().apply {
                                    this.title = seriesTitle
                                    this.url = seriesPath
                                },
                            )
                        }
                    }
                }
            }
        }

        return mangaList
    }

    // ============================ Popular =================================

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(parseCatalog(), false)

    // ============================ Latest ==================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================ Search ==================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val trimmedQuery = query.trim()
        val all = parseCatalog()
        val results = if (trimmedQuery.isBlank()) all else all.filter { it.title.contains(trimmedQuery, ignoreCase = true) }
        return MangasPage(results, false)
    }

    // ============================ Details =================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url.substringBefore("?")}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$baseUrl${manga.url}")
        val finalPath = response.request.url.encodedPath
        val document = response.asJsoup()

        // Series list page has <h1>; reader pages have a <title>
        manga.title = normalizeSeriesTitle(
            document.selectFirst("h1")?.text()?.trim()
                ?: document.selectFirst("title")?.text().orEmpty(),
        )
        // Cover: first chapter thumbnail on a series list page, or first page on a reader page
        manga.thumbnail_url = document.selectFirst("a.comic-link img[src*='_thumbnail']")?.absUrl("src")
            ?: document.selectFirst("img.comic-image")?.absUrl("src")

        val chapterLinks = document.select("a.comic-link")

        // Single chapter series
        if (chapterLinks.isEmpty()) {
            val chapterNum = finalPath.substringAfterLast("/").toFloatOrNull() ?: 1f
            val chapter = SChapter.create().apply {
                name = formatChapterName(chapterNum)
                url = finalPath
                chapter_number = chapterNum
            }
            return SMangaUpdate(manga, listOf(chapter))
        }

        // Multi-chapter series: parse chapter cards
        val chapterList = chapterLinks.map { link ->
            val href = link.attr("href").trim()
            val chapterNum = href.substringAfterLast("/").toFloatOrNull() ?: -1f
            SChapter.create().apply {
                name = formatChapterName(chapterNum)
                url = href
                chapter_number = chapterNum
                date_upload = dateFormat.tryParseDate(link.selectFirst("p.release-date")?.text())
            }
        }.sortedByDescending { it.chapter_number }

        return SMangaUpdate(manga, chapterList)
    }

    // ============================= Pages ==================================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl${chapter.url}").asJsoup()
        return document.select("img.comic-image").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }
}
