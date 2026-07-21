package eu.kanade.tachiyomi.extension.ar.rewayatfans

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document

@Source
abstract class RewayatFans : KeiSource() {

    override val supportsLatest = true

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/%d9%82%d8%a7%d8%a6%d9%85%d8%a9-%d8%a7%d9%84%d8%b1%d9%88%d8%a7%d9%8a%d8%a7%d8%aa/".toHttpUrl()
            .newBuilder()
            .apply {
                if (page > 1) addPathSegment(page.toString())
            }
            .build()
        val response = client.get(url)
        return parseNovelListPage(response)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val url = "$baseUrl/".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .addQueryParameter("post_type", "page")
                .build()
            val response = client.get(url)
            return parseSearchResults(response)
        }
        return getPopularManga(page)
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.last { it.isNotEmpty() }
        if (slug.isBlank() || slug == "page") return null
        val response = client.get(url)
        return parseNovelDetails(response)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaUrl = manga.url.toHttpUrl()
        val response = client.get(mangaUrl)
        val doc = response.asJsoup()

        val mangaDetail = parseNovelDetailsFromDoc(doc).apply { url = manga.url }
        val chapterList = parseChaptersFromDoc(doc)

        return SMangaUpdate(mangaDetail, chapterList)
    }

    // ============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl${chapter.url}".toHttpUrl()
        val response = client.get(url)
        val doc = response.asJsoup()
        return doc.select(".entry-content p, .entry-content img").mapIndexed { index, element ->
            if (element.tagName() == "img") {
                val imageUrl = element.attr("src").ifBlank { element.attr("data-src") }
                Page(index, imageUrl = imageUrl)
            } else {
                val text = element.text()
                if (text.isNotBlank()) {
                    Page(index, imageUrl = "", content = text)
                } else {
                    Page(index, imageUrl = "")
                }
            }
        }.filter { it.imageUrl.isNotBlank() || !it.content.isNullOrBlank() }
    }

    // ============================== URLs ================================

    override fun getMangaUrl(manga: SManga): String = manga.url

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ============================== Parsers ============================

    private fun parseNovelListPage(response: Response): MangasPage {
        val doc = response.asJsoup()
        val novels = doc.select("figure.wp-block-image").mapNotNull { figure ->
            val link = figure.selectFirst("a[href]") ?: return@mapNotNull null
            val novelUrl = link.attr("abs:href")
            if (novelUrl.isBlank() || !novelUrl.contains(baseUrl)) return@mapNotNull null

            val img = figure.selectFirst("img")
            val thumbnail = img?.attr("abs:src")?.ifBlank { img.attr("abs:data-src") } ?: ""

            val caption = figure.selectFirst("figcaption")
            val title = caption?.selectFirst("a")?.text()
                ?: caption?.text()
                ?: img?.attr("alt")
                ?: ""

            if (title.isBlank()) return@mapNotNull null

            SManga.create().apply {
                url = novelUrl
                this.title = title
                thumbnail_url = thumbnail
            }
        }.distinctBy { it.url }

        // Check for next page
        val hasNextPage = doc.select("a.post-page-numbers").any { element ->
            val href = element.attr("href")
            !href.isBlank() && !element.hasClass("current")
        }

        return MangasPage(novels, hasNextPage)
    }

    private fun parseSearchResults(response: Response): MangasPage {
        val doc = response.asJsoup()
        val novels = doc.select("article, figure.wp-block-image").mapNotNull { element ->
            if (element.tagName() == "figure") {
                val link = element.selectFirst("a[href]") ?: return@mapNotNull null
                val novelUrl = link.attr("abs:href")
                if (novelUrl.isBlank()) return@mapNotNull null

                val img = element.selectFirst("img")
                val thumbnail = img?.attr("abs:src") ?: ""
                val title = element.selectFirst("figcaption")?.text() ?: img?.attr("alt") ?: ""

                if (title.isBlank()) return@mapNotNull null

                SManga.create().apply {
                    url = novelUrl
                    this.title = title
                    thumbnail_url = thumbnail
                }
            } else {
                val link = element.selectFirst("a[href]") ?: return@mapNotNull null
                val novelUrl = link.attr("abs:href")
                if (novelUrl.isBlank()) return@mapNotNull null

                val title = element.selectFirst("h2, h3, .entry-title")?.text()
                    ?: link.text()
                    ?: ""

                if (title.isBlank()) return@mapNotNull null

                SManga.create().apply {
                    url = novelUrl
                    this.title = title
                }
            }
        }.distinctBy { it.url }

        return MangasPage(novels, false)
    }

    private fun parseNovelDetails(response: Response): SManga {
        val doc = response.asJsoup()
        return parseNovelDetailsFromDoc(doc)
    }

    private fun parseNovelDetailsFromDoc(doc: Document): SManga = SManga.create().apply {
        title = doc.select("h1, .entry-title").text().ifBlank {
            doc.title().substringBefore("–").trim()
        }
        description = doc.select("meta[name=description]").attr("content").ifBlank {
            doc.select(".entry-content p").firstOrNull { it.text().length > 50 }?.text()
        }
        thumbnail_url = doc.select("meta[property=og:image]").attr("content").ifBlank {
            doc.selectFirst(".entry-content img")?.attr("abs:src")
        }
        val statusText = doc.text()
        status = when {
            statusText.contains("مكتمل") -> SManga.COMPLETED
            statusText.contains("مستمر") || statusText.contains("جارية") -> SManga.ONGOING
            statusText.contains("متوقف") || statusText.contains("متوقفة") -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
        genre = doc.select("a[href*=/genre/], .entry-content strong")
            .filter { it.text().length in 2..30 }
            .distinctBy { it.text() }
            .joinToString(", ") { it.text() }
            .ifBlank {
                // Try to extract from text pattern like "التصنيفات : كوميديا، دراما، ..."
                val genrePattern = Regex("التصنيفات\\s*:\\s*(.+?)(?:\\n|النوع|$)")
                genrePattern.find(doc.text())?.groupValues?.get(1)?.trim() ?: ""
            }
    }

    private fun parseChaptersFromDoc(doc: Document): List<SChapter> {
        val chapterList = mutableListOf<SChapter>()

        // Look for chapter links in various formats
        // Format 1: Direct numbered links (01, 02, 03, etc.)
        doc.select("a[href]").forEach { link ->
            val href = link.attr("href")
            val text = link.text().trim()

            // Check if this looks like a chapter link
            if (text.matches(Regex("^\\d{1,3}$")) || text.matches(Regex("^الفصل\\s*\\d+"))) {
                val chapterNumber = text.replace(Regex("[^\\d.]"), "").toFloatOrNull() ?: 0f
                if (chapterNumber > 0) {
                    chapterList.add(
                        SChapter.create().apply {
                            this.url = href.removePrefix(baseUrl)
                            this.name = "الفصل $text"
                            chapter_number = chapterNumber
                        }
                    )
                }
            }
        }

        // Format 2: Look for chapter list in specific containers
        doc.select(".entry-content a, .chapter-list a").forEach { link ->
            val href = link.attr("href")
            val text = link.text().trim()

            if (text.matches(Regex("^\\d{1,3}$")) && !chapterList.any { it.url == href.removePrefix(baseUrl) }) {
                val chapterNumber = text.toFloatOrNull() ?: 0f
                if (chapterNumber > 0) {
                    chapterList.add(
                        SChapter.create().apply {
                            this.url = href.removePrefix(baseUrl)
                            this.name = "الفصل $text"
                            chapter_number = chapterNumber
                        }
                    )
                }
            }
        }

        return chapterList.sortedBy { it.chapter_number }
    }
}
