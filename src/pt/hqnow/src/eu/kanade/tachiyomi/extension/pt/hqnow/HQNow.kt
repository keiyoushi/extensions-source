package eu.kanade.tachiyomi.extension.pt.hqnow

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.graphQLBody
import keiyoushi.utils.parseGraphQLAs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import java.text.Normalizer
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class HQNow : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(1, 2.seconds)
    }

    private fun genericComicBookFromObject(comicBook: HqNowComicBookDto): SManga = SManga.create().apply {
        title = comicBook.name
        url = "/hq/${comicBook.id}/${comicBook.name.toSlug()}"
        thumbnail_url = comicBook.cover
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val query = $$"""
            query getHqsByFilters(
                $orderByViews: Boolean,
                $limit: Int,
                $publisherId: Int,
                $loadCovers: Boolean
            ) {
                getHqsByFilters(
                    orderByViews: $orderByViews,
                    limit: $limit,
                    publisherId: $publisherId,
                    loadCovers: $loadCovers
                ) {
                    id
                    name
                    editoraId
                    status
                    publisherName
                    hqCover
                    synopsis
                    updatedAt
                }
            }
        """.trimIndent()

        val comicList = graphQLRequest<HqsByFiltersDto>(
            query = query,
            operationName = "getHqsByFilters",
            variables = buildJsonObject {
                put("orderByViews", true)
                put("loadCovers", true)
                put("limit", 300)
            },
        ).getHqsByFilters.map(::genericComicBookFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val query = """
            query getRecentlyUpdatedHqs {
                getRecentlyUpdatedHqs {
                    name
                    hqCover
                    synopsis
                    id
                    updatedAt
                    updatedChapters
                }
            }
        """.trimIndent()

        val comicList = graphQLRequest<RecentlyUpdatedHqsDto>(query = query, operationName = "getRecentlyUpdatedHqs")
            .getRecentlyUpdatedHqs
            .map(::genericComicBookFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val queryStr = $$"""
            query getHqsByName($name: String!) {
                getHqsByName(name: $name) {
                    id
                    name
                    editoraId
                    status
                    publisherName
                    impressionsCount
                }
            }
        """.trimIndent()

        val comicList = graphQLRequest<HqsByNameDto>(
            query = queryStr,
            operationName = "getHqsByName",
            variables = buildJsonObject {
                put("name", query)
            },
        ).getHqsByName.map(::genericComicBookFromObject)

        return MangasPage(comicList, hasNextPage = false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val comicBookId = manga.url.substringAfter("/hq/").substringBefore("/")

        val query = $$"""
            query getHqsById($id: Int!) {
                getHqsById(id: $id) {
                    id
                    name
                    synopsis
                    editoraId
                    status
                    publisherName
                    hqCover
                    impressionsCount
                    capitulos {
                        name
                        id
                        number
                    }
                }
            }
        """.trimIndent()

        val comicBook = graphQLRequest<HqsByIdDto>(
            query = query,
            operationName = "getHqsById",
            variables = buildJsonObject {
                put("id", comicBookId.toInt())
            },
        ).getHqsById[0]

        manga.apply {
            title = comicBook.name
            thumbnail_url = comicBook.cover
            description = comicBook.synopsis.orEmpty()
            author = comicBook.publisherName.orEmpty()
            status = comicBook.status.orEmpty().toStatus()
        }

        val chapterList = comicBook.chapters
            .map { chapter -> chapterFromObject(chapter, comicBook) }
            .reversed()

        return SMangaUpdate(manga, chapterList)
    }

    private fun chapterFromObject(chapter: HqNowChapterDto, comicBook: HqNowComicBookDto): SChapter = SChapter.create().apply {
        name = "#" + chapter.number +
            (if (chapter.name.isNotEmpty()) " - " + chapter.name else "")
        url = "/hq-reader/${comicBook.id}/${comicBook.name.toSlug()}" +
            "/chapter/${chapter.id}/page/1"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterId = chapter.url.substringAfter("/chapter/").substringBefore("/")

        val query = $$"""
            query getChapterById($chapterId: Int!) {
                getChapterById(chapterId: $chapterId) {
                    name
                    number
                    oneshot
                    pictures {
                        pictureUrl
                    }
                }
            }
        """.trimIndent()

        val chapterDto = graphQLRequest<ChapterByIdDto>(
            query = query,
            operationName = "getChapterById",
            variables = buildJsonObject {
                put("chapterId", chapterId.toInt())
            },
        ).getChapterById

        return chapterDto.pictures.mapIndexed { i, page ->
            Page(i, imageUrl = page.pictureUrl)
        }
    }

    private suspend inline fun <reified T> graphQLRequest(query: String, operationName: String, variables: JsonObject? = null): T {
        val body = graphQLBody(query = query, operationName = operationName, variables = variables)

        return client.post(GRAPHQL_URL, body = body).parseGraphQLAs<T>()
    }

    private fun String.toSlug(): String = Normalizer
        .normalize(this, Normalizer.Form.NFD)
        .replace("[^\\p{ASCII}]".toRegex(), "")
        .replace("[^a-zA-Z0-9\\s]+".toRegex(), "").trim()
        .replace("\\s+".toRegex(), "-")
        .lowercase(Locale("pt", "BR"))

    private fun String.toStatus(): Int = when (this) {
        "Concluído" -> SManga.COMPLETED
        "Em Andamento" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val GRAPHQL_URL = "https://admin.hq-now.com/graphql"
    }
}
