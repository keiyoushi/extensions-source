package eu.kanade.tachiyomi.extension.fr.chaostrad

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ChaosTrad : HttpSource() {

    override val name = "ChaosTrad"
    override val baseUrl = "https://chaostrad.fr"
    override val lang = "fr"
    override val supportsLatest = false

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.FRENCH)

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
    private fun parseCatalog(response: Response): List<SManga> {
        val mangaList = mutableListOf<SManga>()
        val addedUrls = mutableSetOf<String>()

        val document = response.asJsoup()
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
                    val subResponse = client.newCall(GET("$baseUrl$href", headers)).execute()
                    val finalPath = subResponse.request.url.encodedPath

                    if (finalPath.startsWith("/search/")) {
                        // True collection page: each a.comic-link leads to a series
                        val subDoc = subResponse.asJsoup()
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
                            val subDoc = subResponse.asJsoup()
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

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage = MangasPage(parseCatalog(response), false)

    // ============================ Latest ==================================

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================ Search ==================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl#$query", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val query = response.request.url.fragment?.trim().orEmpty()
        val all = parseCatalog(response)
        val results = if (query.isBlank()) all else all.filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(results, false)
    }

    // ============================ Details =================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url.substringBefore("?")}"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            // Series list page has <h1>; reader pages have a <title>
            title = normalizeSeriesTitle(
                document.selectFirst("h1")?.text()?.trim()
                    ?: document.selectFirst("title")?.text().orEmpty(),
            )
            // Cover: first chapter thumbnail on a series list page, or first page on a reader page
            thumbnail_url = document.selectFirst("a.comic-link img[src*='_thumbnail']")?.absUrl("src")
                ?: document.selectFirst("img.comic-image")?.absUrl("src")
        }
    }

    // ============================ Chapters ================================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapterLinks = document.select("a.comic-link")

        // Single chapter series
        if (chapterLinks.isEmpty()) {
            val chapterNum = response.request.url.encodedPath.substringAfterLast("/").toFloatOrNull() ?: 1f
            return listOf(
                SChapter.create().apply {
                    name = formatChapterName(chapterNum)
                    url = response.request.url.encodedPath
                    chapter_number = chapterNum
                },
            )
        }

        // Multi-chapter series: parse chapter cards
        return chapterLinks.map { link ->
            val href = link.attr("href").trim()
            val chapterNum = href.substringAfterLast("/").toFloatOrNull() ?: -1f
            val dateText = link.selectFirst("p.release-date")?.text().orEmpty()
            SChapter.create().apply {
                name = formatChapterName(chapterNum)
                url = href
                chapter_number = chapterNum
                date_upload = runCatching { dateFormat.parse(dateText)?.time ?: 0L }.getOrDefault(0L)
            }
        }.sortedByDescending { it.chapter_number }
    }

    // ============================= Pages ==================================

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("img.comic-image").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
