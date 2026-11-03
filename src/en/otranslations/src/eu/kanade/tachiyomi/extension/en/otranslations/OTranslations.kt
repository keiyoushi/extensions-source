package eu.kanade.tachiyomi.extension.en.otranslations

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class OTranslations : KeiSource() {

    private val siteUrl get() = baseUrl.toHttpUrl()

    override suspend fun getPopularManga(page: Int): MangasPage = coroutineScope {
        val url = siteUrl.newBuilder()
            .addPathSegments("api/trending")
            .addQueryParameter("window", "today")
            .build()
        val trending = async { client.get(url, Fetch.XHR).parseAs<Trending>().series.map { it.series } }
        val catalogue = async { fetchCatalogue() }
        catalogue.await().toMangasPage(trending.await())
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = coroutineScope {
        val updated = async {
            client.get(baseUrl, Fetch.DOCUMENT).asJsoup()
                .select("[data-latest-item] a[href*=/series/]")
                .map { it.absUrl("href").toHttpUrl().pathSegments[1] }
                .distinct()
        }
        val catalogue = async { fetchCatalogue() }
        catalogue.await().toMangasPage(updated.await())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val words = query.searchWords()
        if (words.isEmpty()) return fetchCatalogue().toMangasPage(emptyList())
        val entries = fetchCatalogue()
            .mapNotNull { entry ->
                val scores = words.map(entry::score)
                if (0 in scores) null else entry to scores.sum()
            }
            .sortedWith(compareByDescending<Pair<SeriesEntry, Int>> { it.second }.thenBy { it.first.title })
            .map { it.first.toSManga(siteUrl) }
        return MangasPage(entries, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != siteUrl.host) return null
        if (url.pathSegments.firstOrNull() !in setOf("series", "read")) return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        val manga = SManga.create().apply { this.url = slug }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}/"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return SMangaUpdate(parseDetails(manga, document), parseChapters(document))
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/read/${chapter.url}/"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val props = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
            .selectFirst("astro-island[component-url*=/Reader]")!!
            .attr("props")
            .parseAs<JsonElement>()
            .unwrapAstro()
            .parseAs<ReaderProps>()
        return props.chapter.pages.mapIndexed { index, path ->
            Page(index, imageUrl = siteUrl.resolve(path)!!.toString())
        }
    }

    private suspend fun fetchCatalogue(): List<SeriesEntry> = client.get("$baseUrl/search-index.json", Fetch.XHR).parseAs()

    private fun List<SeriesEntry>.toMangasPage(order: List<String>): MangasPage {
        val ranked = sortedBy { entry -> order.indexOf(entry.slug).takeIf { it >= 0 } ?: order.size }
        return MangasPage(ranked.map { it.toSManga(siteUrl) }, false)
    }

    private fun parseDetails(manga: SManga, document: Document): SManga = manga.apply {
        fun info(label: String) = document.selectFirst("dl div:has(> dt:containsOwn($label)) > dd")
        val heading = document.selectFirst("main h1")!!

        title = heading.text()
        author = info("Authors")?.textOrNull()
        artist = info("Artists")?.textOrNull()
        genre = buildList {
            info("Type")?.textOrNull()?.let(::add)
            addAll(document.select("main h1 ~ ul > li").eachText())
            info("Tags")?.select("span")?.eachText()?.let(::addAll)
        }.distinct().takeIf { it.isNotEmpty() }?.joinToString()
        status = when (info("Status")?.text()?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled", "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("[data-cover-open] img")?.absUrl("src")
        description = buildList {
            listOfNotNull(info("Year")?.textOrNull(), info("Content Rating")?.textOrNull())
                .takeIf { it.isNotEmpty() }
                ?.let { add(it.joinToString(" · ")) }
            document.selectFirst("[data-synopsis]")?.wholeText()?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
            heading.previousElementSibling()?.textOrNull()?.let {
                add("**Alternative title:**\n- $it")
            }
            info("Links")?.select("a[href]")?.takeIf { it.isNotEmpty() }?.let { links ->
                add(
                    links.joinToString("\n", prefix = "**Links:**\n") {
                        "- [${it.text().escapeLinkText()}](${it.absUrl("href")})"
                    },
                )
            }
        }.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("li[data-chapter-number]").map { element ->
        val number = element.attr("data-chapter-number")
        val title = element.attr("data-chapter-title")
        SChapter.create().apply {
            url = element.selectFirst("a[href]")!!.absUrl("href").toHttpUrl().pathSegments
                .filter { it.isNotEmpty() }
                .drop(1)
                .joinToString("/")
            name = when {
                title.isEmpty() -> "Chapter $number"
                title.startsWith("Chapter ") -> title
                else -> "Chapter $number: $title"
            }
            chapter_number = number.toFloatOrNull() ?: -1f
        }
    }.reversed()

    private fun String.escapeLinkText() = replace("[", "\\[").replace("]", "\\]")

    // Astro island props: every value is a [type, value] pair, [0] is undefined
    private fun JsonElement.unwrapAstro(): JsonElement = when (this) {
        is JsonArray -> when {
            size == 1 && (this[0] as? JsonPrimitive)?.intOrNull != null -> JsonNull
            size == 2 && (this[0] as? JsonPrimitive)?.intOrNull != null -> this[1].unwrapAstro()
            else -> JsonArray(map { it.unwrapAstro() })
        }
        is JsonObject -> JsonObject(mapValues { it.value.unwrapAstro() })
        else -> this
    }
}
