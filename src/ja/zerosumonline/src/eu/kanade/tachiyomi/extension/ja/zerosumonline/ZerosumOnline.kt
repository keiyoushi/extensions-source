package eu.kanade.tachiyomi.extension.ja.zerosumonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class ZerosumOnline : HttpSource() {
    override val name = "Zerosum Online"
    private val domain = "zerosumonline.com"
    override val baseUrl = "https://$domain"
    override val lang = "ja"
    override val supportsLatest = false

    private val apiUrl = "https://api.$domain/api/v1"

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("list")
            .addQueryParameter("category", "series")
            .addQueryParameter("sort", "date")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAsProto<TitleListView>()
        val mangas = result.titles.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder()

        if (query.isNotEmpty()) {
            url.addPathSegment("search")
                .addQueryParameter("keyword", query)
        } else {
            url.addPathSegment("list")
                .addQueryParameter("category", "series")

            val sort = filters.firstInstance<SelectFilter>().selectedValue
            url.addQueryParameter("sort", sort)
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("title")
            .addQueryParameter("tag", slug)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAsProto<TitleDetailView>()
        return result.title.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAsProto<TitleDetailView>()
        val slug = result.title.slug
        return result.chapters.map { it.toSChapter(slug) }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url.substringBeforeLast("/")

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("/")
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("viewer")
            .addQueryParameter("chapter_id", id)
            .build()
            .toString()

        val body = ViewerRequest(id.toInt()).toRequestBodyProto()
        return POST(url, headers, body)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAsProto<ViewerView>()
        return result.pages
            .filter { it.url.isNotEmpty() }
            .mapIndexed { i, img ->
                Page(i, imageUrl = img.url)
            }
    }

    // Helpers
    private inline fun <reified T> Response.parseAsProto(): T = ProtoBuf.decodeFromByteArray(body.bytes())

    private inline fun <reified T : Any> T.toRequestBodyProto(): RequestBody = ProtoBuf.encodeToByteArray(this)
        .toRequestBody("application/protobuf".toMediaType())

    // Filter
    override fun getFilterList() = FilterList(
        SelectFilter(
            "Sorting",
            arrayOf(
                Pair("更新日が新しい順", "date"),
                Pair("作品名順", "title"),
                Pair("著者名順", "author"),
            ),
        ),
    )

    private open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val selectedValue: String get() = vals[state].second
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
