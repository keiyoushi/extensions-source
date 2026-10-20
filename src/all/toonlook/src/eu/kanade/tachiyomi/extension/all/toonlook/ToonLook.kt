package eu.kanade.tachiyomi.extension.all.toonlook

import android.util.Base64
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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ToonLook : KeiSource() {
    private val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT)
    private val chapterDataRegex = """chapterData\s*=\s*(\{.*?\})\s*;""".toRegex()

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(ScrambleInterceptor())

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter.POPULAR))

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", FilterList(SortFilter.LATEST))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("s", query)
            } else {
                val genre = filters.firstInstanceOrNull<GenreFilter>()?.toSlug()
                val status = filters.firstInstanceOrNull<StatusFilter>()?.toStatus()
                val sortFilter = filters.firstInstanceOrNull<SortFilter>() ?: SortFilter()
                val sortBy = sortFilter.sortBy()
                val ascending = sortFilter.state?.ascending ?: false

                addPathSegment("manhwa")
                if (genre != null) addQueryParameter("genre", genre)
                if (status != null) addQueryParameter("status", status)
                addQueryParameter("sortby", sortBy)
                if (ascending) addQueryParameter("order", "asc")
            }
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
                addPathSegment("")
            }
        }.build()

        val document = client.get(url).asJsoup()
        return parseMangasPage(document)
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".manga-item")
            .filterNot { lang == "zh" && it.selectFirst("img.mangora-flag--card[alt=中文]") == null }
            .map(::mangaFromElement)
        return MangasPage(mangas, hasNextPage = document.selectFirst("a.next") != null)
    }

    private fun mangaFromElement(element: Element): SManga {
        val link = element.selectFirst("a.manga-item__link")!!
        val href = link.absUrl("href")

        return SManga.create().apply {
            url = href.toHttpUrl().pathSegments.last { it.isNotEmpty() }
            title = link.text()
            thumbnail_url = element.selectFirst("img.manga-item__img-inner")?.absUrl("src")
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) {
            return null
        }
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[0] != "manhwa") {
            return null
        }
        return fetchMangaDetails(segments[1]).apply { initialized = true }
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manhwa/${manga.url}/"

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterUrl = "$baseUrl/${chapter.url}".toHttpUrl()
        val chapterLang = if (lang == "all") chapterUrl.queryParameter("lang")!! else lang
        return chapterUrl.newBuilder().apply {
            setQueryParameter("lang", chapterLang)
        }.build().toString()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            manga = parseMangaDetails(document, slug),
            chapters = parseChapterList(document),
        )
    }

    private suspend fun fetchMangaDetails(slug: String): SManga {
        val document = client.get("$baseUrl/manhwa/$slug/").asJsoup()
        return parseMangaDetails(document, slug)
    }

    private fun parseMangaDetails(document: Document, slug: String): SManga = SManga.create().apply {
        url = slug
        title = document.selectFirst(".manga-titles h1")!!.text()
        thumbnail_url = document.selectFirst("img[src*='/img/cover/']")?.absUrl("src")

        description = buildString {
            document.selectFirst(".manga-desc__content")?.text()?.let { append(it) }

            document.selectFirst(".manga-titles h2")?.textOrNull()?.let {
                if (isNotEmpty()) {
                    appendLine()
                    appendLine()
                }
                appendLine("Alternative Titles:")
                it.split(", ").forEach { title -> appendLine("- $title") }
            }
        }

        genre = document.select(".manga-genres a").joinToString { it.text() }.ifEmpty { null }
        author = document.select("div.items-start a[rel=tag][href*='/manga-author/'] > span:first-child").joinToString { it.text() }.ifEmpty { null }
        artist = document.select("div.items-start a[rel=tag][href*='/artist/'] > span:first-child").joinToString { it.text() }.ifEmpty { null }

        status = when (document.selectFirst(".manga-status[data-status]")?.attr("data-status")?.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "dropped" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        val relatedMangas = document.select("#relatedMangaSection .manga-item")
            .map(::mangaFromElement)
            .map { it.toRelatedMangaDto() }
        memo = buildJsonObject {
            put("relatedMangas", relatedMangas.toJsonElement())
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val chapterItems = document.select("a.chapter-item")
        if (lang != "all") {
            return chapterItems.filter { lang in it.attr("data-langs").split(" ") }
                .map { chapterFromElement(it, lang) }
        }

        return chapterItems.flatMap { anchor ->
            val chapterTitles = anchor.attr("data-langs").split(" ").associateBy { "data-title-$it" }
            val titleAttributes = anchor.attributes().asList().filter { it.key in chapterTitles }

            titleAttributes.asReversed().map { attribute ->
                chapterFromElement(anchor, chapterTitles.getValue(attribute.key))
            }
        }
    }

    private fun chapterFromElement(element: Element, chapterLang: String): SChapter {
        val chapterSlug = element.absUrl("href").toHttpUrl().pathSegments.last { it.isNotEmpty() }
        val name = element.attrOrNull("data-title-$chapterLang") ?: element.selectFirst(".chapter-item__name")!!.textOrNull()!!

        return SChapter.create().apply {
            url = "$chapterSlug?lang=$chapterLang"
            this.name = name
            date_upload = chapterDateFormat.tryParseDate(element.selectFirst(".chapter-item__date")?.text())
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val script = document.select("script").firstOrNull { it.data().contains("chapterData") }?.data()
            ?: throw Exception("Could not find chapter data")
        val json = chapterDataRegex.find(script)?.groupValues?.get(1)
            ?: throw Exception("Could not parse chapter data")
        val data = json.parseAs<ChapterData>()
        val pages = String(Base64.decode(data.data, Base64.DEFAULT)).parseAs<List<PageItem>>()
        val scramble = data.imageScramble?.takeIf { it.enabled }

        return pages.mapIndexed { index, item ->
            val fragment = scramble
                ?.let { "#scramble=${data.lang},${it.stripHeight},${it.salt}" }
                .orEmpty()
            Page(index, imageUrl = "${data.base}/${item.src}$fragment")
        }
    }

    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = manga.memo["relatedMangas"]
        ?.parseAs<List<RelatedMangaDto>>()
        ?.map { it.toSManga() }
        .orEmpty()

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get(
        baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("wp-json/wp/v2/genre")
            .addQueryParameter("per_page", "100")
            .build(),
    ).parseAs()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreDto>>().orEmpty()
        return FilterList(
            SortFilter(),
            StatusFilter(),
            GenreFilter(genres),
        )
    }
}
