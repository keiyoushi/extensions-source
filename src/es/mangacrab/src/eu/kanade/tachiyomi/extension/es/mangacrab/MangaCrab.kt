package eu.kanade.tachiyomi.extension.es.mangacrab

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.time.Instant

@Source
abstract class MangaCrab : KeiSource() {

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    private suspend fun getMangasPage(
        page: Int,
        query: String = "",
        genres: String = "",
        feed: String = "discover",
        ranking: String = "",
        status: String = "",
        origin: String = "",
    ): MangasPage {
        val url = "$baseUrl/api/mv/mangas"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE.toString())
            .addQueryParameter("search", query)
            .addQueryParameter("genres", genres)
            .addQueryParameter("status", status)
            .addQueryParameter("origin", origin)
            .addQueryParameter("ranking", ranking)
            .addQueryParameter("feed", feed)
            .addQueryParameter("nsfw", "false")
            .addQueryParameter("nsfw_only", "false")
            .build()

        val dto = client
            .get(url)
            .parseAs<MangaCrabMangasDto>()

        return MangasPage(
            mangas = dto.items.map { it.asSManga() },
            hasNextPage = dto.pagination.has_next,
        )
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangasPage(
        page = page,
        feed = "discover",
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangasPage(
        page = page,
        feed = "updated",
    )

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val genres = filters
            .filterIsInstance<GenreFilter>()
            .firstOrNull()
            ?.selectedValue
            .orEmpty()

        val feed = filters
            .filterIsInstance<FeedFilter>()
            .firstOrNull()
            ?.selectedValue
            ?: "discover"

        val ranking = filters
            .filterIsInstance<RankingFilter>()
            .firstOrNull()
            ?.selectedValue
            .orEmpty()

        val status = filters
            .filterIsInstance<StatusFilter>()
            .firstOrNull()
            ?.selectedValue
            .orEmpty()

        val origin = filters
            .filterIsInstance<OriginFilter>()
            .firstOrNull()
            ?.selectedValue
            .orEmpty()

        return getMangasPage(
            page = page,
            query = query,
            genres = genres,
            feed = feed,
            ranking = ranking,
            status = status,
            origin = origin,
        )
    }

    override fun getFilterList(
        data: kotlinx.serialization.json.JsonElement?,
    ) = FilterList(
        Filter.Header("Usa los filtros para limitar el catálogo"),
        Filter.Separator(),
        GenreFilter(),
        FeedFilter(),
        RankingFilter(),
        StatusFilter(),
        OriginFilter(),
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url
            .removePrefix("/series/")
            .removeSuffix("/")
            .substringBefore("/")

        val savedMangaId = manga.memo["mangaId"]
            ?.jsonPrimitive
            ?.content
            ?.toLongOrNull()

        val detailsDto = if (savedMangaId == null) {
            client
                .get("$baseUrl/api/mv/mangas/by-slug/$slug")
                .parseAs<MangaCrabMangaDto>()
        } else {
            null
        }

        val mangaId = detailsDto?.id ?: savedMangaId

        val detailsDeferred = if (savedMangaId != null && fetchDetails) {
            async {
                client
                    .get("$baseUrl/api/mv/mangas/by-slug/$slug")
                    .parseAs<MangaCrabMangaDto>()
            }
        } else {
            null
        }

        val chaptersDeferred = if (fetchChapters) {
            async {
                client
                    .get(
                        "$baseUrl/api/mv/mangas/$mangaId/chapters?per_page=$CHAPTERS_PER_PAGE",
                    )
                    .parseAs<MangaCrabChaptersDto>()
            }
        } else {
            null
        }

        val updatedDetailsDto = detailsDeferred?.await() ?: detailsDto
        val chaptersDto = chaptersDeferred?.await()

        SMangaUpdate(
            manga = if (updatedDetailsDto != null) {
                if (fetchDetails) {
                    updatedDetailsDto.asSManga().apply {
                        memo = JsonObject(
                            mapOf(
                                "mangaId" to JsonPrimitive(updatedDetailsDto.id),
                            ),
                        )
                    }
                } else {
                    manga.apply {
                        memo = JsonObject(
                            mapOf(
                                "mangaId" to JsonPrimitive(updatedDetailsDto.id),
                            ),
                        )
                    }
                }
            } else {
                manga
            },
            chapters = if (fetchChapters) {
                chaptersDto
                    ?.items
                    ?.map { chapter ->
                        chapter.asSChapter(requireNotNull(mangaId))
                    }
                    .orEmpty()
            } else {
                chapters
            },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val mangaId = chapter.memo["mangaId"]
            ?.jsonPrimitive
            ?.content
            ?: throw Exception("No se pudo obtener la información del capítulo")

        val chapterIndex = chapter.memo["chapterIndex"]
            ?.jsonPrimitive
            ?.content
            ?: throw Exception("No se pudo obtener la información del capítulo")

        val chapterDto = client
            .get(
                "$baseUrl/api/mv/mangas/$mangaId/chapter?cap_index=$chapterIndex",
            )
            .parseAs<MangaCrabChapterDto>()

        if (chapterDto.is_locked || chapterDto.is_vip_chapter || !chapterDto.can_download) {
            throw Exception("Este capítulo está bloqueado o requiere VIP")
        }

        val securityHeader = chapterDto.security
            ?.takeIf { it.enabled == 1 }
            ?.header
            .orEmpty()

        return chapterDto.content
            ?.pages
            ?.mapIndexed { index, imageUrl ->
                val imageUrlWithHeader = if (securityHeader.isBlank()) {
                    imageUrl
                } else {
                    "$imageUrl#nodeHeader=$securityHeader"
                }

                Page(
                    index = index,
                    imageUrl = imageUrlWithHeader,
                )
            }
            .orEmpty()
    }

    override fun imageRequest(page: Page): Request {
        val pageUrl = page.imageUrl.orEmpty()
        val imageUrl = pageUrl.substringBefore("#nodeHeader=")
        val securityHeader = pageUrl.substringAfter("#nodeHeader=", "")

        return Request.Builder()
            .url(imageUrl)
            .apply {
                if (securityHeader.isNotBlank()) {
                    header("fansy", securityHeader)
                }
            }
            .build()
    }

    private fun MangaCrabMangaDto.asSManga(): SManga = SManga.create().apply {
        title = this@asSManga.title
        setUrlWithoutDomain(this@asSManga.permalink)
        thumbnail_url = this@asSManga.cover

        description = this@asSManga.description
            .replace("\r\n", "\n")
            .replace("&quot;", "\"")

        genre = this@asSManga.genres
            .joinToString(", ") { it.name }

        status = when (this@asSManga.status?.raw) {
            "on-going", "en curso" -> SManga.ONGOING
            "end", "finalizado" -> SManga.COMPLETED
            "canceled" -> SManga.CANCELLED
            "on-hold", "hiato" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun MangaCrabChapterDto.asSChapter(mangaId: Long): SChapter = SChapter.create().apply {
        name = buildString {
            if (is_locked || is_vip_chapter || !can_download) {
                append("🔒 ")
            }
            append(title.ifBlank { label })
        }
        setUrlWithoutDomain(link)
        memo = JsonObject(
            mapOf(
                "mangaId" to JsonPrimitive(mangaId),
                "chapterIndex" to JsonPrimitive(index),
            ),
        )
        date_upload = Instant
            .parseOrNull("${date.replace(" ", "T")}Z")
            ?.toEpochMilliseconds()
            ?: 0L
    }

    companion object {
        private const val PER_PAGE = 24
        private const val CHAPTERS_PER_PAGE = 5000
    }
}
