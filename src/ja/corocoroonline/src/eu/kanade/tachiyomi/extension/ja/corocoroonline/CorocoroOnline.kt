package eu.kanade.tachiyomi.extension.ja.corocoroonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.util.Calendar
import java.util.TimeZone

class CorocoroOnline : HttpSource() {
    override val name = "Corocoro Online"
    override val baseUrl = "https://www.corocoro.jp"
    override val lang = "ja"
    override val supportsLatest = true
    override val versionId = 2

    private val apiUrl = "$baseUrl/api/csr"

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/ranking", super.headersBuilder().add("rsc", "1").build())
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val category = response.request.url.fragment ?: "総合"
        val body = response.body.string()
        val rankingLine = body.lines().firstOrNull { it.contains("\"rankingList\"") }
        val jsonStart = rankingLine?.indexOf('[').takeIf { it != -1 }
            ?: return MangasPage(emptyList(), false)
        val jsonArray = rankingLine?.substring(jsonStart)?.parseAs<JsonArray>()
        val container = jsonArray?.last().toString().parseAs<RscRankingContainer>()

        val mangas = container.rankingList.firstOrNull { it.rankingTypeName == category }
            ?.titles?.map { it.toSManga() }
            ?: emptyList()

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val day = getLatestDay()
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "title/list/update_day")
            .addQueryParameter("day", day)
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val titleListView = response.parseAsProto<TitleListView>()
        val mangas = titleListView.list?.titles.orEmpty().map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstance<SelectFilter>()
        return when (val selection = filter.toUriPart()) {
            "mon", "tue", "wed", "thu", "fri", "sat", "sun" -> {
                val url = apiUrl.toHttpUrl().newBuilder()
                    .addQueryParameter("rq", "title/list/update_day")
                    .addQueryParameter("day", selection)
                    .build()
                GET(url, headers)
            }
            "completed" -> GET("$baseUrl/rensai/completed", headers)
            "one-shot" -> GET("$baseUrl/rensai/one-shot", headers)
            else -> {
                GET("$baseUrl/ranking#$selection", super.headersBuilder().add("rsc", "1").build())
            }
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val requestUrl = response.request.url

        return when {
            requestUrl.pathSegments.contains("search") ||
                requestUrl.pathSegments.contains("completed") ||
                requestUrl.pathSegments.contains("one-shot") -> {
                val document = response.asJsoup()
                val mangas = document.select("div.grid > a[href^='/title/']").map {
                    SManga.create().apply {
                        setUrlWithoutDomain(it.absUrl("href"))
                        title = it.selectFirst("p.text-black")?.text() ?: it.selectFirst("p")!!.text()
                        thumbnail_url = it.selectFirst("img")?.absUrl("src")
                    }
                }
                MangasPage(mangas, false)
            }

            requestUrl.pathSegments.contains("ranking") -> popularMangaParse(response)

            requestUrl.pathSegments.contains("api") -> latestUpdatesParse(response)

            else -> MangasPage(emptyList(), false)
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val titleId = manga.url.substringAfterLast("/")
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "title/detail")
            .addQueryParameter("title_id", titleId)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val detailView = response.parseAsProto<TitleDetailView>()
        return detailView.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun chapterListRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val detailView = response.parseAsProto<TitleDetailView>()
        val chapters = detailView.chapters.map { it.toSChapter() }
        val first = chapters.first()
        val last = chapters.last()

        val isAscending = when {
            first.date_upload < last.date_upload -> true
            first.date_upload > last.date_upload -> false
            first.chapter_number > -1 && last.chapter_number > -1 -> first.chapter_number < last.chapter_number
            else -> {
                val firstId = first.url.substringAfterLast("/").toLong()
                val lastId = last.url.substringAfterLast("/").toLong()
                firstId < lastId
            }
        }
        return if (isAscending) chapters.reversed() else chapters
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("/")
        val url = apiUrl.toHttpUrl().newBuilder()
            .addQueryParameter("rq", "chapter/viewer")
            .addQueryParameter("chapter_id", id)
            .build()

        return Request.Builder()
            .url(url)
            .headers(headers)
            .put(ViewerRequest().toRequestBodyProto())
            .build()
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = try {
            response.parseAsProto<ViewerView>()
        } catch (e: Exception) {
            throw Exception("Log in via WebView and purchase this chapter")
        }
        val key = result.aesKey
        val iv = result.aesIv

        return result.pages.mapIndexed { i, img ->
            val imageUrls = img.url.toHttpUrl().newBuilder()
                .fragment("key=$key#iv=$iv")
                .build()
                .toString()
            Page(i, imageUrl = imageUrls)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Helpers
    private inline fun <reified T> Response.parseAsProto(): T {
        return ProtoBuf.decodeFromByteArray(body.bytes())
    }

    private inline fun <reified T : Any> T.toRequestBodyProto(): RequestBody {
        return ProtoBuf.encodeToByteArray(this)
            .toRequestBody("application/protobuf".toMediaType())
    }

    private fun getLatestDay(): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tokyo"))
        val days = arrayOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
        return days[calendar.get(Calendar.DAY_OF_WEEK) - 1]
    }

    // Filter
    override fun getFilterList() = FilterList(
        SelectFilter(
            "Category",
            arrayOf(
                Pair("月", "mon"),
                Pair("火", "tue"),
                Pair("水", "wed"),
                Pair("木", "thu"),
                Pair("金", "fri"),
                Pair("土", "sat"),
                Pair("日", "sun"),
                Pair("完結", "completed"),
                Pair("無料", "one-shot"),
                Pair("(ランキング) 急上昇", "急上昇"),
                Pair("(ランキング) 総合", "総合"),
                Pair("(ランキング) 完結", "完結"),
                Pair("(ランキング) ギャグ・コメディ", "ギャグ・コメディ"),
                Pair("(ランキング) バトル", "バトル"),
                Pair("(ランキング) ホビー", "ホビー"),
                Pair("(ランキング) 異世界・ファンタジー", "異世界・ファンタジー"),
                Pair("(ランキング) デュエル・マスターズ", "デュエル・マスターズ"),
                Pair("(ランキング) ヒューマンドラマ", "ヒューマンドラマ"),
                Pair("(ランキング) ゲーム", "ゲーム"),
                Pair("(ランキング) アニメ化", "アニメ化"),
                Pair("(ランキング) ベイブレード", "ベイブレード"),
                Pair("(ランキング) スポーツ", "スポーツ"),
                Pair("(ランキング) ポケモン", "ポケモン"),
            ),
        ),
    )

    private open class SelectFilter(displayName: String, private val options: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, options.map { it.first }.toTypedArray()) {
        fun toUriPart() = options[state].second
    }
}
