package eu.kanade.tachiyomi.extension.en.silentquill

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getArrayOrNull
import keiyoushi.utils.getString
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonRequestBody
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class SilentQuill : KeiSource() {
    private val apiUrl get() = "$baseUrl/api"
    private val rscHeaders get() = headersBuilder()
        .set("rsc", "1")
        .build()

    override suspend fun getPopularManga(page: Int): MangasPage = client.get(baseUrl, rscHeaders).toMangasPage { element ->
        element is JsonObject &&
            element.getStringOrNull("className")?.contains("grid-cols-4") == true &&
            element.firstObjectOrNull { it.getStringOrNull("href") != null } != null
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.value
        val searchUrl = "$baseUrl/search/".toHttpUrl().newBuilder().apply {
            if (!status.isNullOrEmpty()) addQueryParameter("status", status)
            if (!genre.isNullOrEmpty()) addQueryParameter("genre", genre)
            addQueryParameter("q", query)
            addQueryParameter("page", page.toString())
        }.build()

        return client.get(searchUrl, rscHeaders).toMangasPage(
            hasNextPage = { it.firstObjectOrNull { obj -> obj.getStringOrNull("href")?.contains("page=${page + 1}") == true } != null },
            cards = { it.firstObjectOrNull { obj -> obj.getStringOrNull("className")?.contains("grid-cols") == true }?.getArrayOrNull("children") },
        ) { element ->
            element is JsonObject && element.firstObjectOrNull { obj -> obj.getStringOrNull("src") != null && obj.getStringOrNull("alt") != null } != null
        }
    }

    private fun JsonElement.firstObjectOrNull(predicate: (JsonObject) -> Boolean): JsonObject? = when (this) {
        is JsonObject -> takeIf(predicate) ?: values.firstNotNullOfOrNull { it.firstObjectOrNull(predicate) }
        is JsonArray -> firstNotNullOfOrNull { it.firstObjectOrNull(predicate) }
        else -> null
    }

    private fun Response.toMangasPage(
        hasNextPage: (JsonObject) -> Boolean = { false },
        cards: (JsonObject) -> JsonArray? = { it.getArrayOrNull("children") },
        predicate: (JsonElement) -> Boolean,
    ): MangasPage {
        val container = extractNextJs<JsonObject>(predicate)
        val mangas = container?.let(cards).orEmpty().map { card ->
            val img = card.firstObjectOrNull { it.getStringOrNull("src") != null && it.getStringOrNull("alt") != null }!!
            val href = card.firstObjectOrNull { it.getStringOrNull("href") != null }!!.getString("href")

            SManga.create().apply {
                url = (baseUrl + href).toHttpUrl().pathSegments[1]
                title = img.getString("alt")
                thumbnail_url = baseUrl + img.getString("src")
            }
        }

        return MangasPage(mangas, container?.let(hasNextPage) ?: false)
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        StatusFilter(),
        GenreFilter(),
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val updatedChapters = document.extractNextJs<List<ChapterResponse>>().orEmpty().map { it.toSChapter() }.reversed()

        val authorLine = document.selectFirst("p.mt-2.text-sm.text-ink-dim")?.textOrNull()
        val state = document.selectFirst("dt:containsOwn(Status)")?.nextElementSibling()?.textOrNull()
        val mangas = SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            author = authorLine?.substringBefore(" · art by ")
            artist = authorLine?.substringAfter(" · art by ", "")?.takeIf(String::isNotEmpty)
            description = document.selectFirst("div.reader-content")?.textOrNull()
            genre = document.select("a[href^=/search/?genre=]").eachText().joinToString()
            status = when (state?.lowercase()) {
                "ongoing" -> SManga.ONGOING
                "completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst("main img[src^=/img/]")?.absUrl("src")
        }

        return SMangaUpdate(
            mangas,
            updatedChapters,
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val result = client.post("$apiUrl/lectura/", ViewerResponseBody(chapter.url.toInt()).toJsonRequestBody()).parseAs<ViewerResponse>()
        return result.paginas.mapIndexed { index, url ->
            Page(index, imageUrl = "$apiUrl/p/${url.pages}")
        }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}/"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.memo["slug"]!!.string}/"
}
