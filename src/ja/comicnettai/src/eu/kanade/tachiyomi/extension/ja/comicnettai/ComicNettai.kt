package eu.kanade.tachiyomi.extension.ja.comicnettai

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
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class ComicNettai : KeiSource() {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.of("Asia/Tokyo"))

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(PublusInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url).toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select(".full--comic__list .full--comic__item").map {
            SManga.create().apply {
                title = it.selectFirst(".full--comic__title")!!.text()
                setUrlWithoutDomain(it.absUrl("href"))
                thumbnail_url = it.selectFirst("img.full--comic__thum")?.absUrl("data-src")
            }
        }
        val hasNextPage = document.selectFirst(".pagenation__item:not(.is-hidde) .pagenation__item__link--next") != null
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
            title = document.selectFirst(".detail--title")!!.text()
            author = document.select(".detail__author__item").joinToString { it.text() }
            description = document.selectFirst(".detail--discription")?.textOrNull()
            thumbnail_url = document.selectFirst(".detail-catch__img")?.absUrl("src")
        }

        if (!fetchChapters) return SMangaUpdate(details, chapters)

        val chapterList = buildList {
            while (true) {
                document.select(".detail--product__list a.detail--product__item").mapTo(this) {
                    SChapter.create().apply {
                        url = it.selectFirst(".detail--product__thum")!!.absUrl("data-src").toHttpUrl().pathSegments[2]
                        name = it.selectFirst(".detail--product__item__title")!!.text()
                        date_upload = dateFormat.tryParseDate(it.selectFirst(".detail--product__item__sdate")?.textOrNull())
                        memo = buildJsonObject {
                            put("cid", it.absUrl("href").toHttpUrl().queryParameter("cid"))
                        }
                    }
                }
                val nextUrl = document.selectFirst(".pagenation__item:not(.is-hidde) .pagenation__item__link--next")?.absUrl("href") ?: break
                document = client.get(nextUrl).asJsoup()
            }
        }

        return SMangaUpdate(
            details,
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/publus/viewer.html?cid=${chapter.memo["cid"]!!.string}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val url = "$baseUrl/api/viewer/c".toHttpUrl().newBuilder()
            .addQueryParameter("cid", chapter.memo["cid"]!!.string)
            .build()

        val contentUrl = client.get(url).parseAs<PublusContent>().url!!
        return client.fetchPages(contentUrl)
    }
}
