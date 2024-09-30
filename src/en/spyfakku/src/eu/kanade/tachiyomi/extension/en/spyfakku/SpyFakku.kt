package eu.kanade.tachiyomi.extension.en.spyfakku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SpyFakku : HttpSource() {

    override val name = "SpyFakku"

    override val baseUrl = "https://hentalk.pw"

    private val baseImageUrl = "$baseUrl/image"

    private val baseApiUrl = "$baseUrl/api"

    override val lang = "en"

    override val supportsLatest = false

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseApiUrl/library?sort=released_at&page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val library = response.parseAs<HentaiLib>()

        val mangas = library.archives.map { it.toSManga() }

        val hasNextPage = library.page * library.limit < library.total

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseApiUrl/library".toHttpUrl().newBuilder().apply {
            val terms = mutableListOf(query.trim())

            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", filter.getValue())
                        addQueryParameter("order", if (filter.state!!.ascending) "asc" else "desc")
                    }

                    is TextFilter -> {
                        if (filter.state.isNotEmpty()) {
                            terms += filter.state.split(",").filter { it.isNotBlank() }.map { tag ->
                                val trimmed = tag.trim().replace(" ", "_")
                                (if (trimmed.startsWith("-")) "-" else "") + filter.type + ":" + trimmed.removePrefix("-")
                            }
                        }
                    }

                    else -> {}
                }
            }
            addQueryParameter("q", terms.joinToString(" "))
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        manga.url = Regex("^/archive/(\\d+)/.*").replace(manga.url) { "/g/${it.groupValues[1]}" }
        return GET(baseUrl + manga.url.substringBefore("?") + "/__data.json", headers)
    }

    override fun getFilterList() = getFilters()

    // Details
    private val dateReformat = SimpleDateFormat("EEEE, d MMM yyyy HH:mm (z)", Locale.ENGLISH)
    private val releasedAtFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val createdAtFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun getAdditionals(data: List<JsonElement>): Triple<String?, Pair<String, String>, Long> {
        val hentaiIndexes = json.decodeFromJsonElement<HentaiIndexes>(data[1])

        val description = data[hentaiIndexes.description].jsonPrimitive.contentOrNull
        val released_at = data[hentaiIndexes.released_at].jsonPrimitive.content
        val created_at = data[hentaiIndexes.created_at].jsonPrimitive.content
        val size = data[hentaiIndexes.size].jsonPrimitive.long

        return Triple(description, Pair(released_at, created_at), size)
    }
    private fun <T> Collection<T>.emptyToNull(): Collection<T>? {
        return this.ifEmpty { null }
    }

    private fun Hentai.toSManga() = SManga.create().apply {
        title = this@toSManga.title
        url = "/g/$id?$pages&hash=$hash"
        author = (listOf(circles?.last()).emptyToNull() ?: artists)?.joinToString()
        artist = artists?.joinToString()
        genre = tags?.joinToString()
        thumbnail_url = "$baseImageUrl/$hash/$thumbnail?type=cover"
        description = buildString {
            circles?.emptyToNull()?.joinToString()?.let {
                append("Circles: ", it, "\n")
            }
            publishers?.emptyToNull()?.joinToString()?.let {
                append("Publishers: ", it, "\n")
            }
            magazines?.emptyToNull()?.joinToString()?.let {
                append("Magazines: ", it, "\n")
            }
            events?.emptyToNull()?.joinToString()?.let {
                append("Events: ", it, "\n\n")
            }
            parodies?.emptyToNull()?.joinToString()?.let {
                append("Parodies: ", it, "\n")
            }
            append("Pages: ", pages, "\n\n")
        }
        status = SManga.COMPLETED
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                val add = getAdditionals(response.parseAs<Nodes>().nodes.last().data)
                val desc = client.newCall(mangaDetailsRequest(manga))
                if (manga.description?.contains("size", true) == false) {
                    manga.apply {
                        description = buildString {
                            add.first?.let {
                                append(it, "\n\n")
                            }

                            append(manga.description)

                            try {
                                releasedAtFormat.parse(add.second.first)?.let {
                                    append("Released: ", dateReformat.format(it.time), "\n")
                                }
                            } catch (_: Exception) {}

                            try {
                                createdAtFormat.parse(add.second.second)?.let {
                                    append("Added: ", dateReformat.format(it.time), "\n")
                                }
                            } catch (_: Exception) {}

                            val size = add.third
                            append(
                                "Size: ",
                                when {
                                    size >= 300 * 1000 * 1000 -> "${"%.2f".format(size / (1000.0 * 1000.0 * 1000.0))} GB"
                                    size >= 100 * 1000 -> "${"%.2f".format(size / (1000.0 * 1000.0))} MB"
                                    size >= 1000 -> "${"%.2f".format(size / (1000.0))} kB"
                                    else -> "$size B"
                                },
                            )
                        }
                        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                        initialized = true
                    }
                } else {
                    manga
                }
            }
    }
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url.substringBefore("?")

    // Chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                val add = getAdditionals(response.parseAs<Nodes>().nodes.last().data)

                listOf(
                    SChapter.create().apply {
                        name = "Chapter"
                        url = manga.url
                        date_upload = try {
                            releasedAtFormat.parse(add.second.first)!!.time
                        } catch (e: Exception) {
                            0L
                        }
                    },
                )
            }
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBefore("?")
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // Pages
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val hash = chapter.url.substringAfter("hash=")
        val page = chapter.url.substringAfter("?").substringBefore("&")
        return Observable.just(
            List(page.toInt()) { index ->
                Page(index, imageUrl = "$baseImageUrl/$hash/${index + 1}")
            },
        )
    }

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    // Others
    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
}
