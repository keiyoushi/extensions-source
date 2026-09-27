package eu.kanade.tachiyomi.extension.ja.comicboost

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.publus.PublusContent
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class ComicBoost : KeiSource() {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd").withZone(ZoneId.of("Asia/Tokyo"))

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(PublusInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/genre/".toHttpUrl().newBuilder()
            .addQueryParameter("p", page.toString())
            .build()
        return client.get(url).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("k", query)
            .addQueryParameter("p", page.toString())
            .build()
        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select(".book-list-item").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst(".book-list-item-thum-wrapper")!!.absUrl("href"))
                title = it.selectFirst(".title")!!.text()
                thumbnail_url = it.selectFirst("img.thum")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst(".pagination-list.right .to-next:not(.disabled) a") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        var document = client.get(getMangaUrl(manga)).asJsoup()
        val details = SManga.create().apply {
            title = document.selectFirst("h1.comic-title")!!.text()
            author = document.select(".comic-main-right .author-list .author").joinToString { it.text().replace(AUTHOR_ROLE_REGEX, "") }
            description = document.selectFirst(".comic-description-text")?.textOrNull()
            genre = document.select(".comic-main-right .tag-list .tag").joinToString { it.text() }
            thumbnail_url = document.selectFirst(".comic-main-thum-wrapper img")?.absUrl("src")
        }

        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val chapterList = buildList {
            while (true) {
                document.select(".book-product-list-item").mapTo(this) {
                    SChapter.create().apply {
                        val title = it.selectFirst(".title")!!.text()
                        url = it.attr("data-id")
                        name = if (it.selectFirst(".coin") != null) "🔒 $title" else title
                        date_upload = dateFormat.tryParseDate(it.selectFirst(".update-date")?.textOrNull())
                    }
                }
                val nextUrl = document.selectFirst(".pagination-list.right .to-next:not(.disabled) a")?.absUrl("href") ?: break
                document = client.get(nextUrl).asJsoup()
            }
        }

        return SMangaUpdate(
            details,
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/product/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val cid = client.get(getChapterUrl(chapter)).use { it.request.url.queryParameter("cid") }
            ?: throw Exception("Log in via WebView and purchase this chapter to read.")

        val url = "$baseUrl/pageapi/viewer/c.php".toHttpUrl().newBuilder()
            .addQueryParameter("cid", cid)
            .build()

        val contentUrl = client.get(url).parseAs<PublusContent>().url!!
        return client.fetchPages(contentUrl)
    }

    companion object {
        private val AUTHOR_ROLE_REGEX = Regex("^(原作|漫画|作画|キャラクター原案|原案)：")
    }
}
