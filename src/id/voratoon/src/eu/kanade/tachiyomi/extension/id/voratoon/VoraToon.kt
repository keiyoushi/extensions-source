package eu.kanade.tachiyomi.extension.id.voratoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class VoraToon : KeiSource() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    private val rscHeaders: Headers
        get() = headers.newBuilder().set("RSC", "1").build()

    override suspend fun getPopularManga(page: Int): MangasPage = parseSeriesList(
        "$baseUrl/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "popular")
            .build(),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseSeriesList(
        "$baseUrl/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("sort", "new")
            .build(),
    )

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotEmpty()) {
                addQueryParameter("q", query)
            }
            filters.filterIsInstance<UriQueryFilter>().forEach {
                it.addToQuery(this)
            }
        }.build()

        return parseSeriesList(url)
    }

    private suspend fun parseSeriesList(url: HttpUrl): MangasPage {
        val dto = client.get(url, rscHeaders, Fetch.XHR).extractNextJs<BrowseDto> {
            it is JsonObject && "series" in it && "seriesMeta" in it
        } ?: return MangasPage(emptyList(), false)

        val mangas = dto.series.map { it.toSManga() }
        val hasNextPage = dto.seriesMeta?.let { (it.page ?: 0) < (it.lastPage ?: 0) } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.getSlug(baseUrl)
        val dto = client.get("$baseUrl/series/$slug", rscHeaders, Fetch.XHR).extractNextJs<SeriesDetailsDto> {
            it is JsonObject && "series" in it && "chapters" in it
        } ?: return SMangaUpdate(manga, chapters)

        return SMangaUpdate(
            dto.series.firstOrNull()?.toSManga() ?: manga,
            dto.chapters.map { it.toSChapter(slug) },
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "series") return null
        val slug = url.pathSegments.getOrNull(1) ?: return null
        val manga = SManga.create().apply {
            this.url = "/series/$slug"
        }
        return getMangaUpdate(
            manga,
            emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (slug, chapterIndex) = chapter.getSlugAndIndex(baseUrl)
        val chapterUrl = "$baseUrl/series/$slug/chapter/$chapterIndex"

        val directDto = runCatching {
            client.get(chapterUrl, rscHeaders, Fetch.XHR).extractNextJs<ChapterPageDto> {
                it is JsonObject && "chapter" in it
            }
        }.getOrNull()

        val directPages = directDto?.chapter?.toPageList(chapterUrl)
        if (!directPages.isNullOrEmpty()) {
            return directPages
        }

        val images = try {
            runWebView<List<String>>(timeout = 25.seconds) {
                userAgent = headers["User-Agent"] ?: ""
                poll(500.milliseconds) {
                    evaluateJs(
                        """
                        (() => {
                            const imgs = Array.from(document.querySelectorAll('img[data-chapter-page], [data-reader-chapter-id] img'))
                                .map(img => img.getAttribute('data-chapter-page') || img.src)
                                .filter(src => src && (src.startsWith('http://') || src.startsWith('https://')));
                            return imgs.length > 0 ? Array.from(new Set(imgs)) : null;
                        })()
                        """.trimIndent(),
                    ) { output ->
                        val list = runCatching {
                            output.trim().takeIf { it != "null" && it != "undefined" }?.let { raw ->
                                runCatching { raw.parseAs<List<String>>() }.getOrElse {
                                    raw.parseAs<String>().parseAs<List<String>>()
                                }
                            }
                        }.getOrNull()

                        if (!list.isNullOrEmpty()) {
                            resolve(list)
                        }
                    }
                }
                loadUrl(chapterUrl)
            }
        } catch (_: WebViewTimeoutException) {
            throw IOException("Selesaikan verifikasi di WebView lalu coba lagi")
        }

        return images.mapIndexed { index, imageUrl ->
            Page(index, url = chapterUrl, imageUrl = imageUrl)
        }
    }

    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .fetch(Fetch.IMAGE.from(page.url))
        .build()

    override val supportsFilterFetching: Boolean get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val dto = client.get("$baseUrl/browse", rscHeaders, Fetch.XHR).extractNextJs<FilterPayloadDto> {
            it is JsonObject && "genres" in it
        } ?: error("Failed to fetch filter data")
        return dto.genres.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreItem>>()?.map { it.toPair() }
        return FilterList(
            buildList {
                add(SortFilter())
                add(StatusFilter())
                add(FormatFilter())
                add(TypeFilter())
                add(AdaptationFilter())
                add(MinChapterFilter())
                if (!genres.isNullOrEmpty()) {
                    add(GenreFilter(genres.toTypedArray()))
                }
            },
        )
    }
}
