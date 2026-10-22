package eu.kanade.tachiyomi.extension.all.rawkumame

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class RawkumaMe : KeiSource() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(TextOverlayInterceptor { baseUrl })

    override suspend fun getPopularManga(page: Int): MangasPage = browse(page, "popular")

    override suspend fun getLatestUpdates(page: Int): MangasPage = browse(page, "date")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val order = filters.firstInstanceOrNull<OrderFilter>()?.toUriPart() ?: "popular"
        return browse(page, order) {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            filters.forEach { filter ->
                val (name, value) = when (filter) {
                    is StatusFilter -> "status" to filter.toUriPart()
                    is TypeFilter -> "type" to filter.toUriPart()
                    is GenreFilter -> "genre" to filter.toUriPart()
                    else -> return@forEach
                }
                if (value.isNotEmpty()) addQueryParameter(name, value)
            }
        }
    }

    private suspend fun browse(page: Int, order: String, params: HttpUrl.Builder.() -> Unit = {}): MangasPage {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("lang", lang)
            .addQueryParameter("order", order)
            .addQueryParameter("page", page.toString())
            .apply(params)
            .build()
        val document = client.get(url).asJsoup()
        val mangas = document.extractNextJs<List<SeriesDto>>().orEmpty().map { it.toSManga(baseUrl) }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val index = url.pathSegments.indexOf("manga")
        if (index == -1) return null
        val slug = url.pathSegments.getOrNull(index + 1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder()
            .addQueryParameter("lang", lang)
            .addQueryParameter("slugs", manga.url)
            .build()
        val series = client.get(url).parseAs<List<SeriesDto>>().first()
        return SMangaUpdate(
            manga = series.toSMangaDetails(baseUrl),
            chapters = series.toSChapters(lang),
        )
    }

    override fun getMangaUrl(manga: SManga): String = "$siteUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$siteUrl/manga/${chapter.url}"

    private val siteUrl get() = if (lang == "es") "$baseUrl/es" else baseUrl

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // images are cleaned scans, the translated text for every language is an svg string in the chapter's rsc payload
        val reader = client.get("$baseUrl/manga/${chapter.url}").asJsoup().extractNextJs<ReaderChapterDto>()
            ?: return emptyList()
        return reader.pages.mapIndexed { index, page ->
            val svg = page.text[lang]
            val imageUrl = if (svg.isNullOrBlank()) {
                page.url
            } else {
                "${page.url}#${PageOverlay(page.width, page.height, svg).toJsonString()}"
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        OrderFilter(),
        StatusFilter(),
        TypeFilter(),
        GenreFilter(),
    )
}
