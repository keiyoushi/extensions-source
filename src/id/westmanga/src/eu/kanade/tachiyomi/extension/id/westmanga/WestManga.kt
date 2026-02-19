package eu.kanade.tachiyomi.extension.id.westmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WestManga : HttpSource() {
    override val name = "West Manga"
    override val baseUrl = "https://westmanga.me"
    private val apiUrl = "https://data.westmanga.me"
    override val lang = "id"
    override val id = 8883916630998758688
    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", SortFilter.popular)

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", SortFilter.latest)

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("api")
            addPathSegment("contents")
            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("per_page", "20")
            addQueryParameter("type", "Comic")
            filters.filterIsInstance<UrlFilter>().forEach {
                it.addToUrl(this)
            }
        }.build()

        return apiRequest(url)
    }

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        StatusFilter(),
        CountryFilter(),
        ColorFilter(),
        GenreFilter(),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<PaginatedData<BrowseManga>>()

        val entries = data.data.map {
            SManga.create().apply {
                // old urls compatibility
                setUrlWithoutDomain(
                    baseUrl.toHttpUrl().newBuilder()
                        .addPathSegment("manga")
                        .addPathSegment(it.slug)
                        .addPathSegment("")
                        .toString(),
                )
                title = it.title
                thumbnail_url = it.cover
            }
        }

        return MangasPage(entries, data.paginator.hasNextPage())
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val path = "$baseUrl${manga.url}".toHttpUrl().pathSegments
        assert(path.size == 3) { "Migrate from $name to $name" }
        val slug = path[1]

        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("comic")
            .addPathSegment(slug)
            .build()

        return apiRequest(url)
    }

    override fun getMangaUrl(manga: SManga): String {
        val slug = "$baseUrl${manga.url}".toHttpUrl().pathSegments[1]
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("comic")
            .addPathSegment(slug)
            .build()

        return url.toString()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = response.parseAs<Data<Manga>>().data

        return SManga.create().apply {
            // old urls compatibility
            setUrlWithoutDomain(
                baseUrl.toHttpUrl().newBuilder()
                    .addPathSegment("manga")
                    .addPathSegment(data.slug)
                    .addPathSegment("")
                    .toString(),
            )
            title = data.title
            thumbnail_url = data.cover
            author = data.author
            status = when (data.status) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                "hiatus" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            genre = buildList {
                when (data.country) {
                    "JP" -> add("Manga")
                    "CN" -> add("Manhua")
                    "KR" -> add("Manhwa")
                }
                if (data.color == true) {
                    add("Colored")
                }
                data.genres.forEach { add(it.name) }
            }.joinToString()
            description = buildString {
                data.synopsis?.let {
                    append(
                        Jsoup.parseBodyFragment(it).wholeText().trim(),
                    )
                }
                data.alternativeName?.let {
                    append("\n\n")
                    append("Alternative Name: ")
                    append(it.trim())
                }
            }
        }
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val data = response.parseAs<Data<Manga>>().data

        return data.chapters.map {
            SChapter.create().apply {
                setUrlWithoutDomain(
                    baseUrl.toHttpUrl().newBuilder()
                        .addPathSegment(it.slug)
                        .addPathSegment("")
                        .toString(),
                )
                name = "Chapter ${it.number}"
                date_upload = it.updatedAt.time * 1000
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val path = "$baseUrl${chapter.url}".toHttpUrl().pathSegments
        assert(path.size == 2) { "Refresh Chapter List" }
        val slug = path[0]

        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("v")
            .addPathSegment(slug)
            .build()

        return apiRequest(url)
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = "$baseUrl${chapter.url}".toHttpUrl().pathSegments[0]
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("view")
            .addPathSegment(slug)

        return url.toString()
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<Data<ImageList>>().data

        return data.images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    private fun apiRequest(url: HttpUrl): Request {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val message = "wm-api-request"
        val key = timestamp + "GET" + url.encodedPath + ACCESS_KEY + SECRET_KEY
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val hash = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        val signature = hash.joinToString("") { "%02x".format(it) }

        val apiHeaders = headersBuilder()
            .set("x-wm-request-time", timestamp)
            .set("x-wm-accses-key", ACCESS_KEY)
            .set("x-wm-request-signature", signature)
            .build()

        return GET(url, apiHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}

private const val ACCESS_KEY = "WM_WEB_FRONT_END"
private const val SECRET_KEY = "xxxoidj"
