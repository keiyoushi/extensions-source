package eu.kanade.tachiyomi.extension.ar.neverscans

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NeverScans : KeiSource() {
    private val dateFormatter = DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH)

    private fun Response.toMangasPage(): MangasPage {
        val doc = this.asJsoup()
        val mangas = doc.select("a[href^=/manga/]").map { a ->
            SManga.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                title = a.selectFirst("h3")!!.text()
                thumbnail_url = a.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga?sort=popular")
        return response.toMangasPage()
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val response = client.get("$baseUrl/manga")
        return response.toMangasPage()
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("search", query)
        }.build()
        return client.get(url).toMangasPage()
    }

    // ============================== Details ==============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        check(url.pathSegments.size >= 2) { "Unsupported URL" }
        val slug = url.pathSegments[1]
        val manga = SManga.create().apply {
            this.url = "/manga/$slug"
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
            this.url = manga.url
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get("$baseUrl${manga.url}")
        val document = response.asJsoup()

        val mangaDetail = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst("aside img")?.absUrl("src")
            genre = document.select("aside a[href*=?genres=]").joinToString { it.text() }
            status = document.select("aside div:contains(الحالة) span").text().let {
                when {
                    it.contains("مستمرة") -> SManga.ONGOING
                    it.contains("مكتملة") -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        }

        val chapterList = document.select("ol li a[href*=/manga/]").map { a ->
            SChapter.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                name = a.selectFirst("div[dir=rtl] span")!!.text()
                date_upload = runCatching {
                    LocalDateTime.parse(a.selectFirst("div[dir=rtl] span.tabular")?.text(), dateFormatter).toInstant(ZoneOffset.UTC).toEpochMilli()
                }.getOrDefault(0L)
            }
        }

        return SMangaUpdate(mangaDetail, chapterList)
    }

    // ============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$baseUrl/${chapter.url}")
        val doc = response.asJsoup()
        return doc.select("img[src*=/api/public/page/]").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }
}
