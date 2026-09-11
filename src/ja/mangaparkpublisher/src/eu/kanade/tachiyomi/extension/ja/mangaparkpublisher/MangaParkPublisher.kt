package eu.kanade.tachiyomi.extension.ja.mangaparkpublisher

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.IOException
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class MangaParkPublisher : KeiSource() {
    private val apiUrl get() = "$baseUrl/api/chapter"
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ROOT).withZone(ZoneId.of("Asia/Tokyo"))

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(ImageInterceptor())
        addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code == 401 && request.url.pathSegments.contains("api")) {
                throw IOException("Log in via WebView and purchase this chapter to read.")
            }

            response
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage = client.get("$baseUrl/ranking?target=all").toMangasPage()

    override suspend fun getLatestUpdates(page: Int): MangasPage = client.get("$baseUrl/series").toMangasPage()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val filter = filters.firstInstance<TypeFilter>()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegments("search/freeword")
                addQueryParameter("key", query)
            } else if (filter.type == "ranking") {
                addPathSegment("ranking")
                addQueryParameter("target", filter.value)
            } else {
                addPathSegment("series")
                addPathSegment(filter.value)
            }
        }.build()

        return client.get(url).toMangasPage()
    }

    private fun Response.toMangasPage(): MangasPage {
        val document = this.asJsoup()
        val mangas = document.select("div.list div.rankingHome ul.common-list li a, div.list div.series div.titles li a, div.list div.search-result ul.common-list li a").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst("div.info h3")!!.text()
                thumbnail_url = it.selectFirst("div.thumb > img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val titleInfo = document.selectFirst("div.titleMain div.titleInfo")!!
        val mangaDetails = SManga.create().apply {
            title = titleInfo.selectFirst("h1")!!.text()
            author = titleInfo.selectFirst("p.author")?.text()
            description = document.selectFirst("p.explanation")?.text()
            thumbnail_url = document.selectFirst("div.titleThumb img")?.absUrl("src")
            genre = titleInfo.select("div.titleCategory ul li a").joinToString { it.text() }
            val statusText = titleInfo.selectFirst("div.tag ul li a")?.text()
            status = when {
                statusText?.contains("完結") == true -> SManga.COMPLETED
                statusText?.contains("更新") == true -> SManga.ONGOING
                statusText?.contains("休載中") == true -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }

        val chapterList = document.select("div.chapter ul li[data-chapter-id]").map {
            SChapter.create().apply {
                url = it.attr("data-chapter-id")
                val title = it.selectFirst("p.chapterTitle")!!.text()
                val isFree = it.selectFirst("div.free-badge img") != null
                name = if (isFree) "\uD83C\uDD93 $title" else title
                date_upload = dateFormat.tryParseDate(it.selectFirst("div.date span")?.text())
                chapter_number = it.attr("data-chapter-name").toFloat()
                memo = buildJsonObject {
                    put("slug", manga.url)
                }
            }
        }.reversed()

        return SMangaUpdate(mangaDetails, chapterList)
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.memo["slug"]!!.string}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$apiUrl/${chapter.url}")
        val chapters = try {
            response.parseAs<ApiResponse>().data.chapter
        } catch (_: Exception) {
            throw Exception("You need to purchase this chapter.")
        }

        return chapters.flatMap { it.images }.mapIndexed { i, image ->
            val url = image.path.toHttpUrl().newBuilder()
                .fragment(image.key)
                .build()
            Page(i, imageUrl = url.toString())
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        TypeFilter(),
    )
}
