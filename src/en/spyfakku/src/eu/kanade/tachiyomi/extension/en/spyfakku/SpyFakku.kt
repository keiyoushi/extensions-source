package eu.kanade.tachiyomi.extension.en.spyfakku

import eu.kanade.tachiyomi.network.GET
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
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

    private fun getAdditionals(data: List<JsonElement>): ShortHentai {
        fun Collection<JsonElement>.getTags(): List<String> = this.map {
            data[it.jsonPrimitive.int + 2].jsonPrimitive.content
        }
        val hentaiIndexes = json.decodeFromJsonElement<HentaiIndexes>(data[1])

        val hash = data[hentaiIndexes.hash].jsonPrimitive.content
        val thumbnail = data[hentaiIndexes.thumbnail].jsonPrimitive.int

        val description = data[hentaiIndexes.description].jsonPrimitive.contentOrNull
        val released_at = data[hentaiIndexes.released_at].jsonPrimitive.content
        val created_at = data[hentaiIndexes.created_at].jsonPrimitive.content
        val size = data[hentaiIndexes.size].jsonPrimitive.long
        val pages = data[hentaiIndexes.pages].jsonPrimitive.int

        val circles = data[hentaiIndexes.circles].jsonArray.emptyToNull()?.getTags()
        val publishers = data[hentaiIndexes.publishers].jsonArray.emptyToNull()?.getTags()
        val magazines = data[hentaiIndexes.magazines].jsonArray.emptyToNull()?.getTags()
        val events = data[hentaiIndexes.events].jsonArray.emptyToNull()?.getTags()
        val parodies = data[hentaiIndexes.parodies].jsonArray.emptyToNull()?.getTags()
        return ShortHentai(
            hash = hash,
            thumbnail = thumbnail,
            description = description,
            released_at = released_at,
            created_at = created_at,
            publishers = publishers,
            circles = circles,
            magazines = magazines,
            parodies = parodies,
            events = events,
            size = size,
            pages = pages,
        )
    }
    private fun <T> Collection<T>.emptyToNull(): Collection<T>? {
        return this.ifEmpty { null }
    }

    private fun Hentai.toSManga() = SManga.create().apply {
        title = this@toSManga.title
        url = "/g/$id?$pages&hash=$hash"
        artist = artists?.joinToString()
        genre = tags?.joinToString()
        thumbnail_url = "$baseImageUrl/$hash/$thumbnail?type=cover"
        status = SManga.COMPLETED
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        var response: Response = client.newCall(mangaDetailsRequest(manga)).execute()
        var attempts = 0
        while (attempts < 3 && response.code != 200) {
            try {
                response = client.newCall(mangaDetailsRequest(manga)).execute()
            } catch (_: Exception) {
            } finally {
                attempts++
            }
        }
        val add = getAdditionals(response.parseAs<Nodes>().nodes.last().data)
        return Observable.just(
            manga.apply {
                with(add) {
                    url = "/g/$id?$pages&hash=$hash"
                    author = (circles ?: listOf(manga.artist)).joinToString()
                    thumbnail_url = "$baseImageUrl/$hash/$thumbnail?type=cover"
                    this@apply.description = buildString {
                        description?.let {
                            append(it, "\n\n")
                        }

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

                        try {
                            releasedAtFormat.parse(released_at)?.let {
                                append("Released: ", dateReformat.format(it.time), "\n")
                            }
                        } catch (_: Exception) {
                        }

                        try {
                            createdAtFormat.parse(created_at)?.let {
                                append("Added: ", dateReformat.format(it.time), "\n")
                            }
                        } catch (_: Exception) {
                        }

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
            },
        )
    }
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException()
    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url.substringBefore("?")

    // Chapters
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        var response: Response = client.newCall(chapterListRequest(manga)).execute()
        var attempts = 0
        while (attempts < 3 && response.code != 200) {
            try {
                response = client.newCall(chapterListRequest(manga)).execute()
            } catch (_: Exception) {
            } finally {
                attempts++
            }
        }
        val add = getAdditionals(response.parseAs<Nodes>().nodes.last().data)
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    name = "Chapter"
                    url = manga.url
                    date_upload = try {
                        releasedAtFormat.parse(add.released_at)!!.time
                    } catch (e: Exception) {
                        0L
                    }
                },
            ),
        )
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url.substringBefore("?")
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // Pages
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        if (!chapter.url.contains("&hash=") && !chapter.url.contains("?")) {
            val response = client.newCall(pageListRequest(chapter)).execute()
            val add = getAdditionals(response.parseAs<Nodes>().nodes.last().data)
            return Observable.just(
                List(add.pages) { index ->
                    Page(index, imageUrl = "$baseImageUrl/${add.hash}/${index + 1}")
                },
            )
        }
        val hash: String = chapter.url.substringAfter("hash=")
        val pages: Int = chapter.url.substringAfter("?").substringBefore("&").toInt()

        return Observable.just(
            List(pages) { index ->
                Page(index, imageUrl = "$baseImageUrl/$hash/${index + 1}")
            },
        )
    }

    override fun pageListRequest(chapter: SChapter): Request {
        chapter.url = Regex("^/archive/(\\d+)/.*").replace(chapter.url) { "/g/${it.groupValues[1]}" }
        return GET(baseUrl + chapter.url.substringBefore("?") + "/__data.json", headers)
    }
    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    // Others
    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
}
