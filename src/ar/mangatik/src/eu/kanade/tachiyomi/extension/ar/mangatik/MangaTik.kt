package eu.kanade.tachiyomi.extension.ar.mangatik

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
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response

@Source
abstract class MangaTik : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        rateLimit(3)
    }

    private fun Response.toMangasPage(): MangasPage {
        val doc = this.asJsoup()

        val mangas = doc.select("article.manga-card").map { article ->
            SManga.create().apply {
                article.selectFirst("h2.manga-title > a")?.let { a ->
                    title = a.text().trim()
                    setUrlWithoutDomain(a.attr("abs:href"))
                }
                thumbnail_url = article.selectFirst("img.manga-poster")?.let {
                    it.attr("abs:data-src").ifEmpty { it.attr("abs:src") }
                }
            }
        }

        val hasNextPage = doc.select("a.btn").any { el ->
            el.text().contains("التالي") && el.attr("href").startsWith("?page=")
        }

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", "views")
            addQueryParameter("page", page.toString())
        }.build()
        return client.get(url).toMangasPage()
    }

    // ============================== Latest ==============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/latest".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
        }.build()
        return client.get(url).toMangasPage()
    }

    // ============================== Search ==============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("q", query)
            addQueryParameter("page", page.toString())
        }.build()
        return client.get(url).toMangasPage()
    }

    // ========================= Details & Chapters =========================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        check(url.pathSegments.size >= 2) { "Unsupported URL" }
        val slug = url.pathSegments[1]
        val manga = SManga.create().apply {
            this.url = "/manga/$slug"
        }
        return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get("$baseUrl${manga.url}".toHttpUrl()).asJsoup()
        val data: MangaJsonLd = doc.selectFirst("script[type=application/ld+json]")
            ?.data()
            ?.parseAs()
            ?: throw Exception("Unable to find manga data")

        val slug = manga.url.removePrefix("/manga/")

        val chapterList = doc.select("a.chapter-item-card").map { a ->
            val chapterNumber = a.attr("data-chapter")
            SChapter.create().apply {
                name = a.select("span").firstOrNull { it.text().startsWith("الفصل") }?.text()
                    ?: "الفصل $chapterNumber"
                url = "/manga/$slug/$chapterNumber"
            }
        }

        val info = SManga.create().apply {
            this.url = manga.url
            title = data.name
            description = data.description
            genre = data.genre?.joinToString()
            thumbnail_url = data.image?.url
            author = data.author?.name?.takeUnless {
                it.isEmpty() || it.equals("unknown", true)
            }
            status = SManga.UNKNOWN
        }

        return SMangaUpdate(info, chapterList)
    }

    // ============================== Pages ==============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get("$baseUrl${chapter.url}".toHttpUrl()).asJsoup()
        return doc.select("img.webtoon-image").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }
}
