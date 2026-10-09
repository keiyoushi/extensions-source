package eu.kanade.tachiyomi.extension.fr.astralmanga

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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParse
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import kotlin.time.Instant

@Source
abstract class AstralManga : KeiSource() {

    // ========================== Popular ==========================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(POPULAR))

    // ========================== Latest ==========================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(LATEST))

    // ========================== Search ==========================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.count(String::isNotBlank) < 2) return null
        return fetchMangaUpdate(
            SManga.create().apply { this.url = url.encodedPath },
            emptyList(),
            true,
            false,
        ).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/api/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("pageSize", "12")

            if (query.isNotBlank()) {
                addQueryParameter("query", query)
            }

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        val sortValue = filter.toUriPart()
                        val sortOrder = if (sortValue == "title") "asc" else "desc"
                        addQueryParameter("sortBy", sortValue)
                        addQueryParameter("sortOrder", sortOrder)
                    }

                    is StatusFilter -> {
                        val statusValue = filter.toUriPart()
                        if (statusValue.isNotBlank()) addQueryParameter("status", statusValue)
                    }

                    is TypeFilter -> {
                        val typeValue = filter.toUriPart()
                        if (typeValue.isNotBlank()) addQueryParameter("type", typeValue)
                    }

                    is GenreFilter -> {
                        filter.state.filter { it.state }.forEach { addQueryParameter("tags", it.name) }
                    }

                    else -> {}
                }
            }
        }.build()
        return parseMangaApiResponse(client.get(url))
    }

    // ========================= Filters =========================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData() = client.get(
        "$baseUrl/catalog",
    ).extractNextJs<Genres>()!!.genres.map {
        it.name
    }.toJsonElement()

    override fun getFilterList(data: JsonElement?) = getFilters(
        data?.parseAs<List<String>>().orEmpty(),
    )

    // ======================== MangaUpdate ======================

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val rscHeaders = headersBuilder()
            .set("RSC", "1")
            .set("Cache-Control", "no-cache")
            .build()
        val response = client.get(
            getMangaUrl(manga).cacheBust(),
            rscHeaders,
        )

        val mangaUuid = response.request.url.pathSegments[1]
        val rscData = response.body.string()
        val mangaDto = rscData.extractNextJsRsc<MangaDto> {
            it is JsonObject && it["urlId"]?.jsonPrimitive?.contentOrNull == mangaUuid
        } ?: throw Exception("Title not found")

        val chaptersDto = rscData.extractNextJsRsc<List<RscChapterDto>> {
            it is JsonArray && it.any { chapter ->
                chapter is JsonObject &&
                    chapter["orderId"] != null &&
                    chapter["publishDate"] != null &&
                    chapter["mangaId"]?.jsonPrimitive?.contentOrNull == mangaDto.id
            }
        } ?: emptyList()

        val seen = mutableSetOf<String>()
        val updatedChapters = chaptersDto
            .filter { seen.add(it.id) }
            .map { ch ->
                SChapter.create().apply {
                    url = "/manga/$mangaUuid/chapter/${ch.id}"
                    name = "Chapitre ${ch.orderIdString}"
                    chapter_number = ch.orderId
                    date_upload = Instant.tryParse(ch.publishDate)
                    scanlator = "Astral Manga"
                }
            }

        return SMangaUpdate(
            mangaDto.toSManga().presign(),
            updatedChapters,
        )
    }

    // ========================== Pages ==========================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(
            getChapterUrl(chapter),
        ).asJsoup()
        val images = document.extractNextJs<List<RscImageDto>>()
        if (images != null) {
            val imageUrls = images.sortedBy { it.orderId }.map { it.link }
            return imageUrls.presign().mapIndexed { index, imageUrl ->
                Page(index, imageUrl = imageUrl)
            }
        }

        return document.select("img[alt~=^Page \\d+]").mapIndexed { index, img ->
            val imageUrl = img.absUrl("src").ifEmpty {
                val src = img.attr("src")
                if (src.startsWith("http")) src else "$baseUrl$src"
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    // ========================== Parsing ==========================

    /**
     * Parse manga list from API JSON response.
     */
    private suspend fun parseMangaApiResponse(response: Response): MangasPage {
        val dto = response.parseAs<MangaResponseDto>()

        val mangas = dto.mangas
            .map(MangaDto::toSManga)
            .presign()

        val hasNextPage = (dto.mangas.size >= 12) && (mangas.size < dto.total)
        return MangasPage(mangas, hasNextPage)
    }

    /**
     * Presign an S3 key using the /api/s3/presign-get endpoint.
     */
    private suspend fun presignS3Key(s3Key: String): String? = try {
        val url = "$baseUrl/api/s3/presign-get".toHttpUrl().newBuilder()
            .addQueryParameter("key", s3Key)
            .build()
        client.get(url).parseAs<PresignResponseDto>().url
    } catch (_: Exception) {
        null
    }

    private suspend fun SManga.presign() = apply {
        thumbnail_url?.takeIf { it.startsWith("s3:") }?.let {
            thumbnail_url = presignS3Key(it.substringAfter("s3:"))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> List<T>.presign() = coroutineScope {
        map {
            async {
                when (it) {
                    is SManga -> it.presign()
                    is String -> if (it.startsWith("s3:")) {
                        presignS3Key(it.substringAfter("s3:"))
                    } else {
                        it
                    }
                    else -> {}
                } as T
            }
        }.awaitAll()
    }

    private fun String.cacheBust() = toHttpUrl().newBuilder()
        .addQueryParameter("_", System.currentTimeMillis().toString())
        .build()
}
