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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SpyFakku : HttpSource() {

    override val name = "SpyFakku"

    override val baseUrl = "https://hentalk.pw"

    private val baseImageUrl = "https://cdn.fakku.cc/image"

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

        val mangas = library.archives.map(::popularManga)

        val hasNextPage = library.archives.isNotEmpty()

        return MangasPage(mangas, hasNextPage)
    }

    private fun popularManga(hentai: ShortHentai) = SManga.create().apply {
        setUrlWithoutDomain("$baseUrl/g/${hentai.id}")
        title = hentai.title
        thumbnail_url = "$baseImageUrl/${hentai.hash}/1/c"
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
        manga.url = Regex("^/archive/(\\d+)/.*").replace(manga.url) { "/g/${it.groupValues[1]}" }.replace("/__data.json", "")
        return GET(baseApiUrl + manga.url, headers)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        chapter.url = Regex("^/archive/(\\d+)/.*").replace(chapter.url) { "/g/${it.groupValues[1]}" }.replace("/__data.json", "")
        return GET(baseApiUrl + chapter.url, headers)
    }

    override fun getFilterList() = getFilters()

    private val dateReformat = SimpleDateFormat("EEEE, d MMM yyyy HH:mm (z)", Locale.ENGLISH)
    private val releasedAtFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val createdAtFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun Hentai.toSManga() = SManga.create().apply {
        title = this@toSManga.title
        url = "/g/$id"
        author = (circles?.emptyToNull() ?: artists)?.joinToString { it.name }
        artist = artists?.joinToString { it.name }
        genre = tags?.joinToString { it.name }
        thumbnail_url = "$baseImageUrl/$hash/1/c"
        description = buildString {
            this@toSManga.description?.let {
                append(this@toSManga.description, "\n\n")
            }
            circles?.emptyToNull()?.joinToString { it.name }?.let {
                append("Circles: ", it, "\n")
            }
            publishers?.emptyToNull()?.joinToString { it.name }?.let {
                append("Publishers: ", it, "\n")
            }
            magazines?.emptyToNull()?.joinToString { it.name }?.let {
                append("Magazines: ", it, "\n")
            }
            events?.emptyToNull()?.joinToString { it.name }?.let {
                append("Events: ", it, "\n\n")
            }
            parodies?.emptyToNull()?.joinToString { it.name }?.let {
                append("Parodies: ", it, "\n")
            }
            append("Pages: ", pages, "\n\n")

            try {
                releasedAtFormat.parse(released_at)?.let {
                    append("Released: ", dateReformat.format(it.time), "\n")
                }
            } catch (_: Exception) {}

            try {
                createdAtFormat.parse(created_at)?.let {
                    append("Added: ", dateReformat.format(it.time), "\n")
                }
            } catch (_: Exception) {}
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
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<Hentai>().toSManga()
    }

    private fun <T> Collection<T>.emptyToNull(): Collection<T>? {
        return this.ifEmpty { null }
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val hentai = response.parseAs<Hentai>()

        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                url = "/g/${hentai.id}"
                date_upload = try {
                    releasedAtFormat.parse(hentai.released_at)!!.time
                } catch (e: Exception) {
                    0L
                }
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun pageListParse(response: Response): List<Page> {
        val hentai = response.parseAs<Hentai>()
        val images = hentai.images
        return images.mapIndexed { index, it ->
            Page(index, imageUrl = "$baseImageUrl/${hentai.hash}/${it.filename}")
        }
    }
    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()
}
