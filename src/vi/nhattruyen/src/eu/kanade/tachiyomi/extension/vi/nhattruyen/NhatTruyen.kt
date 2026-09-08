package eu.kanade.tachiyomi.extension.vi.nhattruyen

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class NhatTruyen : WPComics() {

    override val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.getDefault())

    override val gmtOffset = null

    override val searchPath = "tim-truyen"

    override val popularPath = "truyen-tranh-hot"

    /**
     * NetTruyen/NhatTruyen redirect back to catalog page if searching query is not found.
     * That makes both sites always return un-relevant results when searching should return empty.
     */
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        description = document.select("div.detail-content div.shortened").flatMap { it.children() }
            .joinToString("\n\n") { it.wholeText().trim() }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder().apply {
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> filter.toUriPart()?.let { addPathSegment(it) }
                    is StatusFilter -> filter.toUriPart()?.let { addQueryParameter("status", it) }
                    is OrderByFilter -> filter.toUriPart()?.let { addQueryParameter("sort", it) }
                    else -> {}
                }
            }

            addQueryParameter(queryParam, query)
            addQueryParameter("page", page.toString())
        }.build()

        return parseMangaPage(client.get(url), searchMangaSelector(), ::searchMangaFromElement)
    }

    private class OrderByFilter :
        UriPartFilter(
            "Sắp xếp theo",
            listOf(
                Pair("0", "Ngày cập nhật"),
                Pair("15", "Truyện mới"),
                Pair("10", "Top all"),
                Pair("11", "Top tháng"),
                Pair("12", "Top tuần"),
                Pair("13", "Top ngày"),
                Pair("20", "Top theo dõi"),
                Pair("25", "Bình luận"),
                Pair("30", "Số chapter"),
            ),
        )

    override fun getFilterList(genres: List<Pair<String?, String>>): FilterList = FilterList(
        buildList {
            add(StatusFilter(intl["STATUS"], getStatusList()))
            add(OrderByFilter())
            if (genres.isNotEmpty()) {
                add(GenreFilter(intl["GENRE"], genres))
            }
        },
    )

    override suspend fun mangaUpdateParse(response: Response, manga: SManga, chapters: List<SChapter>): SMangaUpdate {
        val slug = manga.url.substringAfterLast("/") // slug

        val updatedManga = mangaDetailsParse(response.asJsoup())

        val updatedChapters = run {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("Comic/Services/ComicService.asmx/ChapterList")
                .addQueryParameter("slug", slug)
                .build()
            val response = client.get(url)
            val json = response.parseAs<ChapterDTO>()
            json.data.map {
                SChapter.create().apply {
                    setUrlWithoutDomain("$baseUrl/truyen-tranh/$slug/${it.chapterSlug}")
                    name = it.chapterName
                    date_upload = dateFormatChapter.tryParseDateTime(it.updatedAt)
                }
            }
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    @Serializable
    class ChapterDTO(
        val data: List<Data> = emptyList(),
    )

    @Serializable
    class Data(
        @SerialName("chapter_name") val chapterName: String,
        @SerialName("chapter_slug") val chapterSlug: String,
        @SerialName("updated_at") val updatedAt: String,
    )

    private val dateFormatChapter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
}
