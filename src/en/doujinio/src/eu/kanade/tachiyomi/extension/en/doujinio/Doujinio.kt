package eu.kanade.tachiyomi.extension.en.doujinio

import eu.kanade.tachiyomi.extension.en.doujinio.DoujinioHelper.deserialize
import eu.kanade.tachiyomi.extension.en.doujinio.DoujinioHelper.getIdFromUrl
import eu.kanade.tachiyomi.extension.en.doujinio.DoujinioHelper.getIdsFromUrl
import eu.kanade.tachiyomi.extension.en.doujinio.DoujinioHelper.tags
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

const val BASE_URL = "https://doujin.io"
const val LATEST_LIMIT = 20

class Doujinio : HttpSource() {
    override val name = "Doujin.io - J18"

    override val baseUrl = BASE_URL

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    // Latest

    override fun latestUpdatesRequest(page: Int) =
        POST(
            "$baseUrl/api/mangas/newest",
            headers,
            body = json.encodeToString(
                LatestRequest(
                    limit = LATEST_LIMIT,
                    offset = (page - 1) * LATEST_LIMIT,
                ),
            ).toRequestBody("application/json".toMediaType()),
        )

    override fun latestUpdatesParse(response: Response): MangasPage {
        val latest = deserialize(
            response.body.string(),
            ListSerializer(Manga.serializer()),
        ).data.map { it.toSManga() }

        return MangasPage(latest, hasNextPage = latest.size >= LATEST_LIMIT)
    }

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/mangas/popular", headers)

    override fun popularMangaParse(response: Response) = MangasPage(
        deserialize(
            response.body.string(),
            ListSerializer(Manga.serializer()),
        ).data.map { it.toSManga() },
        hasNextPage = false,
    )

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = POST(
        "$baseUrl/api/mangas/search",
        headers,
        body = json.encodeToString(
            SearchRequest(
                keyword = query,
                page = page,
                tags = filters.findInstance<TagGroup>()?.state?.filter { it.state }?.mapNotNull {
                    tags.find { tag -> tag.name == it.name }?.id
                } ?: emptyList(),
            ),
        ).toRequestBody("application/json".toMediaType()),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val result = deserialize(response.body.string(), SearchResponse.serializer()).data

        return MangasPage(result.data.map { it.toSManga() }, hasNextPage = result.to < result.total)
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga) =
        GET("https://doujin.io/api/mangas/${getIdFromUrl(manga.url)}", headers)

    override fun mangaDetailsParse(response: Response) =
        deserialize(response.body.string(), Manga.serializer()).data.toSManga()

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${getIdFromUrl(manga.url)}"

    // Chapter

    override fun chapterListRequest(manga: SManga) =
        GET("$baseUrl/api/chapters?manga_id=${getIdFromUrl(manga.url)}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterList =
            deserialize(response.body.string(), ListSerializer(Chapter.serializer())).data
        return chapterList.map { it.toSChapter() }.reversed()
    }

    // Page List

    override fun pageListRequest(chapter: SChapter) =
        GET(
            "$baseUrl/api/mangas/${getIdsFromUrl(chapter.url)}/manifest",
            headers.newBuilder().apply {
                add(
                    "referer",
                    "https://doujin.io/manga/${
                    getIdsFromUrl(chapter.url).split("/").joinToString("/chapter/")
                    }",
                )
            }.build(),
        )

    override fun pageListParse(response: Response) =
        if (response.headers["content-type"] == "text/html; charset=UTF-8") {
            throw Exception("You need to login first through the WebView to read the chapter.")
        } else {
            json.decodeFromString<ChapterManifest>(
                response.body.string(),
            ).toPageList()
        }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        TagGroup(),
    )

    private class TagFilter(name: String) : Filter.CheckBox(name, false)

    private class TagGroup : Filter.Group<TagFilter>(
        "Tags",
        tags.map { TagFilter(it.name) },
    )

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
