package eu.kanade.tachiyomi.extension.en.webtoonclub

import android.content.SharedPreferences
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.int
import keiyoushi.utils.intOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import kotlin.math.roundToInt

@Source
abstract class WebtoonClub :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchManga(page, "", FilterList(SortFilter("popular"), RatingFilter(preferences.rating)))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchManga(page, "", FilterList(SortFilter("latest"), RatingFilter(preferences.rating)))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/webtoons".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("search", query.trim())
            filters.filterIsInstance<UrlFilter>().forEach { it.addToUrl(this) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        return getCards(url)
    }

    private suspend fun getCards(listUrl: HttpUrl): MangasPage {
        val document = client.get(listUrl).asJsoup()
        val mangas = document.select("a[href^=/webtoon/]:has(img[data-cover])").map { element ->
            SManga.create().apply {
                this.url = element.absUrl("href").toHttpUrl().pathSegments[1]
                title = element.selectFirst("h3")!!.text()
                // The srcset's last entry is the full-size cover the series page shows
                thumbnail_url = element.selectFirst("img[data-cover]")?.attr("srcset")?.substringAfterLast(", ")?.substringBefore(' ')
                    ?.let { listUrl.resolve(it)?.toString() }
            }
        }
        val hasNextPage = document.selectFirst("a[aria-label=Go to next page]") != null

        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != baseUrl.toHttpUrl().host) return MangasPage(emptyList(), false)
        val list = when (url.pathSegments[0]) {
            "webtoons" -> "$baseUrl/webtoons".toHttpUrl().newBuilder().apply {
                url.queryParameterNames.filter { it != "page" }.forEach { name -> url.queryParameterValues(name).forEach { addQueryParameter(name, it) } }
            }
            "genre", "author" -> {
                val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return MangasPage(emptyList(), false)
                "$baseUrl/webtoons".toHttpUrl().newBuilder().addQueryParameter(if (url.pathSegments[0] == "genre") "genres" else "author", slug)
            }
            else -> return super.getMangasByUrl(url, page)
        }
        return getCards(list.setQueryParameter("page", page.toString()).build())
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "webtoon") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf(String::isNotEmpty) ?: return null

        return getMangaUpdate(
            manga = SManga.create().apply { this.url = slug },
            chapters = emptyList(),
            fetchDetails = true,
            fetchChapters = false,
        ).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/webtoon/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        // The chapter list API takes the numeric id, found only on the series page
        val knownId = manga.memo[COMIC_ID]?.intOrNull
        val details = async {
            if (fetchDetails || knownId == null) parseDetails(manga, client.get(getMangaUrl(manga)).asJsoup()) else manga
        }
        val chapterList = async {
            if (!fetchChapters) return@async chapters
            val comicId = knownId ?: details.await().memo[COMIC_ID]!!.int
            client.get("$baseUrl/api/comics/$comicId/chapters").parseAs<ChaptersDto>().chapters.map { it.toSChapter(manga.url) }
        }

        SMangaUpdate(details.await(), chapterList.await())
    }

    private fun parseDetails(manga: SManga, document: Document): SManga = manga.apply {
        val book = document.selectFirst("script[type=application/ld+json]")!!.data().parseAs<LdGraphDto>().graph.first { it.type == "Book" }
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = book.image
        author = book.author.joinToString { it.name }.ifEmpty { null }
        genre = (listOfNotNull(document.fact("Type")) + book.genre).distinct().joinToString().ifEmpty { null }
        status = when (document.fact("Status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        // The page's rating widget; JSON-LD only carries ratings with more votes
        val rating = document.extractNextJs<RatingDto> { it is JsonObject && "ratingAvg" in it && "ratingCount" in it }
            ?.takeIf { it.ratingCount > 0 }
            ?.let {
                val stars = it.ratingAvg.roundToInt()
                "★".repeat(stars) + "☆".repeat(5 - stars) + " ${it.ratingAvg} (${it.ratingCount})"
            }
        // "None" is a placeholder in the site's alt title data
        val altTitles = book.alternateName.filterNot { it == "None" || it == title }.distinct()
        description = listOfNotNull(
            listOfNotNull(rating, document.fact("Released"), document.fact("Origin")).joinToString(" · ").ifEmpty { null },
            // The page's synopsis; JSON-LD has a reworded one, and none where the page shows a placeholder
            book.description?.let { document.selectFirst("p.whitespace-pre-line")?.wholeText()?.trim() },
            listOfNotNull(
                altTitles.joinToString(" · ").ifEmpty { null }?.let { "**Alt titles:** $it" },
                book.character.joinToString(" · ") { it.name }.ifEmpty { null }?.let { "**Characters:** $it" },
            ).joinToString("\n").ifEmpty { null },
        ).joinToString("\n\n").ifEmpty { null }
        memo = buildJsonObject { put(COMIC_ID, document.selectFirst("[data-comic-id]")!!.attr("data-comic-id").toInt()) }
    }

    private fun Document.fact(name: String) = selectFirst("dt:matchesOwn(^$name$) + dd")?.textOrNull()

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/webtoon/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reader = client.get(getChapterUrl(chapter)).asJsoup().extractNextJs<ReaderChapterDto> {
            it is JsonObject && "images" in it && "comicId" in it
        }!!

        return reader.images.sortedBy { it.sortOrder }.mapIndexed { index, image ->
            Page(index, imageUrl = "$baseUrl/uploads/${image.filename}")
        }
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/genres").asJsoup()
        .select("a[href^=/genre/]:has(span[title])")
        .map { it.selectFirst("span")!!.text() to it.absUrl("href").toHttpUrl().pathSegments[1] }
        .sortedBy { it.first.lowercase() }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<Pair<String, String>>>().orEmpty()

        return FilterList(
            buildList {
                add(SortFilter())
                add(StatusFilter())
                add(RatingFilter(preferences.rating))
                add(TypeFilter())
                if (genres.isNotEmpty()) add(GenreFilter(genres))
            },
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_RATING
            title = "Content rating"
            summary = "Used for popular, latest and search. The Content rating filter overrides it.\nCurrent: %s"
            entries = RATING_OPTIONS.map { it.first }.toTypedArray()
            entryValues = RATING_OPTIONS.map { it.second }.toTypedArray()
            setDefaultValue("")
        }.let(screen::addPreference)
    }

    private val SharedPreferences.rating
        get() = getString(PREF_RATING, "")!!

    companion object {
        private const val PREF_RATING = "pref_rating"
        private const val COMIC_ID = "comicId"
    }
}
