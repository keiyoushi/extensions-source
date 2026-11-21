package eu.kanade.tachiyomi.extension.all.saycomic

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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class SayComic : KeiSource() {

    override val supportsLatest = true

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(permits = 1, period = 2.seconds)

    private val simpleDateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    // ============================== Popular / Browse ==============================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/$lang/series?page=$page", Fetch.DOCUMENT).asJsoup()
        return parseMangaListing(document, page)
    }

    // ============================== Latest Updates ==============================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/$lang/latest?page=$page", Fetch.DOCUMENT).asJsoup()
        return parseMangaListing(document, page)
    }

    // ============================== Search ==============================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/$lang/series".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return parseMangaListing(document, page)
    }

    private fun parseMangaListing(document: Document, page: Int): MangasPage {
        val mangas = document.select("article.series-card").mapNotNull { card ->
            val link = card.selectFirst("h2 a") ?: card.selectFirst("a.series-card-cover") ?: return@mapNotNull null
            val href = link.attr("href")
            val title = card.selectFirst("h2 a")?.text()?.trim() ?: link.text().trim()
            if (title.isBlank() || href.isBlank()) return@mapNotNull null

            val coverImg = card.selectFirst("img")
            val coverUrl = coverImg?.let {
                it.attr("abs:src").ifEmpty { it.attr("abs:data-src") }
            }

            SManga.create().apply {
                setUrlWithoutDomain(href)
                this.title = title
                this.thumbnail_url = coverUrl
            }
        }

        val hasNextPage = document.selectFirst("nav.pagination a[href*='page=${page + 1}']") != null ||
            document.selectFirst("nav.pagination a:contains(Next), nav.pagination a:contains(›)") != null

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Manga Details & Chapters ==============================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        val updatedManga = if (fetchDetails) {
            manga.apply {
                title = document.selectFirst("h1")?.text()?.trim() ?: manga.title

                val coverImg = document.selectFirst(".series-profile img, .series-seo-context img, img[alt*='cover'], img[src*='covers']")
                thumbnail_url = coverImg?.let {
                    it.attr("abs:src").ifEmpty { it.attr("abs:data-src") }
                } ?: manga.thumbnail_url

                val authorText = document.selectFirst(".series-creator-links a, .series-v72-meta-row:contains(Author) strong")?.text()?.trim()
                if (!authorText.isNullOrBlank() && !authorText.equals("updating", ignoreCase = true)) {
                    author = authorText
                    artist = authorText
                }

                val statusText = document.selectFirst(".series-v72-meta-pair strong")?.text()?.lowercase() ?: ""
                status = when {
                    "ongoing" in statusText -> SManga.ONGOING
                    "completed" in statusText -> SManga.COMPLETED
                    "hiatus" in statusText -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }

                val type = document.selectFirst(".series-v72-meta-pair div:contains(Type) strong")?.text()?.replace("Type", "")?.trim()
                val category = document.selectFirst(".series-v72-meta-row:contains(Category) strong")?.text()?.trim()
                genre = listOfNotNull(type, category).filter { it.isNotBlank() }.joinToString(", ")

                description = document.selectFirst(".series-seo-context p, .series-description, p[data-full-text]")?.text()?.trim()

                initialized = true
            }
        } else {
            manga
        }

        val updatedChapters = if (fetchChapters) {
            document.select("a.series-chapter-card, a[data-chapter-item]").mapNotNull { el ->
                val href = el.attr("href")
                if (href.isBlank()) return@mapNotNull null

                val chapterNo = el.attr("data-chapter-number").toFloatOrNull() ?: -1f
                val name = el.selectFirst(".series-chapter-number-text")?.text()?.trim()
                    ?: el.selectFirst("strong")?.text()?.trim()
                    ?: "Chapter $chapterNo"

                val dateUpload = el.selectFirst("time")?.let { timeEl ->
                    val iso = timeEl.attr("datetime")
                    if (iso.isNotBlank()) {
                        runCatching {
                            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
                        }.getOrNull()
                    } else {
                        null
                    } ?: simpleDateFormat.tryParseDate(timeEl.text().trim())
                } ?: 0L

                SChapter.create().apply {
                    setUrlWithoutDomain(href)
                    this.name = name
                    this.chapter_number = chapterNo
                    this.date_upload = dateUpload
                }
            }
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // ============================== Page List ==============================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.select(".reader-pages img, img[src*='img.saycomic.com'], img[data-src*='img.saycomic.com']")
            .mapNotNull { img ->
                val url = img.attr("abs:src").ifEmpty { img.attr("abs:data-src") }
                if (url.contains("img.saycomic.com") && !url.contains("brand")) url else null
            }
            .distinct()
            .mapIndexed { index, imageUrl ->
                Page(index = index, imageUrl = imageUrl)
            }
    }

    // ============================== Deeplink Support ==============================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (!url.host.contains("saycomic.com") || !url.encodedPath.contains("/series/")) return null
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null

        return SManga.create().apply {
            setUrlWithoutDomain(url.encodedPath)
            this.title = title
            this.thumbnail_url = document.selectFirst(".series-profile img, .series-seo-context img, img[alt*='cover']")?.let {
                it.attr("abs:src").ifEmpty { it.attr("abs:data-src") }
            }
        }
    }
}
