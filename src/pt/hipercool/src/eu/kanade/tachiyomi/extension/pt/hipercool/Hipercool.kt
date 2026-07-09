package eu.kanade.tachiyomi.extension.pt.hipercool

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.get
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Hipercool : HttpSource() {

    override val supportsLatest: Boolean = true

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(3, 1.seconds)
        .build()

    // ============================ Popular ====================================

    private val popularFilter = FilterList(OrderByFilter("", arrayOf("" to "popular")))

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", popularFilter)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================ Latest ====================================
    private val latestFilter = FilterList(OrderByFilter("", arrayOf("" to "newest")))

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", latestFilter)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ============================ Search ====================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val limit = 30
        val input = buildJsonObject {
            putJsonObject("0") {
                putJsonObject("json") {
                    put("q", query)
                    filters.filterIsInstance<OrderByFilter>().forEach { filter ->
                        put("sort", filter.selected())
                    }
                    putJsonObject("filters") {
                        put("genres", null)
                        put("type", null)
                        put("status", null)
                        put("contentRating", null)
                        put("author", null)
                        put("artist", null)
                        put("year", null)
                    }

                    put("limit", limit)
                    put("offset", (page - 1) * limit)
                    put("maxRating", "pornographic")
                }
                putJsonObject("meta") {
                    putJsonObject("values") {
                        put("filters.genres", buildJsonArray { add("undefined") })
                        put("filters.type", buildJsonArray { add("undefined") })
                        put("filters.status", buildJsonArray { add("undefined") })
                        put("filters.contentRating", buildJsonArray { add("undefined") })
                        put("filters.author", buildJsonArray { add("undefined") })
                        put("filters.artist", buildJsonArray { add("undefined") })
                        put("filters.year", buildJsonArray { add("undefined") })
                    }
                }
            }
        }.toString()

        val url = "$baseUrl/api/trpc/search.query".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val element = response.parseAs<List<JsonElement>>().first()
        val dto = element["result"]["data"]["json"]?.parseAs<WrapperContent>() ?: return MangasPage(emptyList(), false)
        return MangasPage(dto.hits.map(MangaDto::toSManga), dto.hits.isNotEmpty())
    }

    // ============================ Details ===================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url
            .substringAfterLast("$MANGA_SUBSTRING/")
            .substringBefore("#")
            .takeIf(String::isNotBlank)
            ?: throw IOException("Migre para própria extensão")

        val input = buildJsonObject {
            putJsonObject("0") {
                put("json", null)
                putJsonObject("meta") {
                    putJsonArray("values") {
                        add("undefined")
                    }
                }
            }
            putJsonObject("1") {
                putJsonObject("json") {
                    put("slug", slug)
                }
            }
        }

        val url = "$baseUrl/api/trpc/auth.me,series.bySlugWithGenres".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val element = response.parseAs<List<JsonElement>>().last()
        return element["result"]["data"]["json"]!!.parseAs<MangaDto>().toSManga()
    }

    // ============================ Chapters ==================================

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("#").toLongOrNull()
            ?: throw IOException("Migre para própria extensão")
        val input = buildJsonObject {
            putJsonObject("0") {
                putJsonObject("json") {
                    putJsonArray("values") {
                        add("undefined")
                    }
                }
            }

            putJsonObject("1") {
                putJsonObject("json") {
                    put("seriesId", mangaId)
                    put("chapterId", null)
                    put("sort", "best")
                    put("page", 1)
                    put("limit", 20)
                }
                putJsonObject("meta") {
                    putJsonObject("values") {
                        put("chapterId", buildJsonArray { add("undefined") })
                    }
                }
            }

            putJsonObject("2") {
                putJsonObject("json") {
                    put("seriesId", mangaId)
                }
            }
        }

        val url = "$baseUrl/api/trpc/auth.me,comments.list,series.chapters".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .fragment(manga.url)
            .build()
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val mangaPath = response.request.url.fragment!!
        val element = response.parseAs<List<JsonElement>>().last()
        val chaptersDTO = element["result"]["data"]["json"]!!.parseAs<List<ChapterDto>>()
        return chaptersDTO.map { it.toSChapter(mangaPath) }
    }

    // ============================ Pages =====================================

    override fun pageListRequest(chapter: SChapter): Request {
        val slug = chapter.url
            .substringAfterLast("$MANGA_SUBSTRING/")
            .substringBefore("#")
            .takeIf(String::isNotBlank)
            ?: throw IOException("Migre para própria extensão")

        val input = buildJsonObject {
            putJsonObject("0") {
                put("json", null)
                putJsonObject("meta") {
                    putJsonArray("values") {
                        add("undefined")
                    }
                }
            }
            putJsonObject("1") {
                putJsonObject("json") {
                    put("slug", slug)
                }
            }
            putJsonObject("2") {
                putJsonObject("json") {
                    put("seriesSlug", slug)
                    put("chapterNumber", chapter.chapter_number)
                }
            }
            putJsonObject("3") {
                putJsonObject("json") {
                    put("position", "footer_bottom")
                }
            }
        }

        val url = "$baseUrl/api/trpc/auth.me,series.bySlug,reader.chapterPages".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .build()

        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val element = response.parseAs<List<JsonElement>>().last()
        val pages = element["result"]["data"]["json"]?.parseAs<List<PageDto>>() ?: return emptyList()
        return pages.map(PageDto::toPage)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================ Filters ====================================

    open class OrderByFilter(displayName: String, private val vals: Array<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun selected() = vals[state].second
    }

    companion object {
        const val MANGA_SUBSTRING = "manga"
    }
}
