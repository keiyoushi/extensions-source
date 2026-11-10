package eu.kanade.tachiyomi.extension.all.sexkomix2

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter

@Source
abstract class SexKomix2 : KeiSource() {

    override val supportsFilterFetching = true

    override val supportsRelatedMangas = true

    // Comic pages live under a folder per language; Russian is the site's original one
    private val comicFolder get() = if (lang == "ru") "comicsx" else "comicsx_$lang"

    override suspend fun getPopularManga(page: Int) = fetchList(listUrl("home", page, SORT_VIEWS))

    override suspend fun getLatestUpdates(page: Int) = fetchList(listUrl("home", page, SORT_DATE))

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val sort = filters.firstInstance<SortFilter>().value
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.selected
        val tag = filters.firstInstance<TagFilter>().state.trim()
        val studio = filters.firstInstance<StudioFilter>().state.trim()

        val url = when {
            query.isNotBlank() -> listUrl("search", page, sort) { addQueryParameter("q", query.trim()) }
            category != null -> listUrl("categories", page, sort) { addQueryParameter("cat", category) }
            tag.isNotEmpty() -> listUrl("tag_pagex", page, sort) { addQueryParameter("t", tag) }
            studio.isNotEmpty() -> listUrl("studios", page, sort) { addQueryParameter("cat", studio) }
            filters.firstInstance<ListFilter>().best -> listUrl("zone", page, sort) { addQueryParameter("go", "best") }
            else -> listUrl("home", page, sort)
        }
        return fetchList(url)
    }

    private fun listUrl(path: String, page: Int, sort: String, block: HttpUrl.Builder.() -> Unit = {}) = baseUrl.toHttpUrl().newBuilder().apply {
        addPathSegment(path)
        addPathSegment("")
        addQueryParameter("lang", lang)
        block()
        addQueryParameter("sort", sort)
        addQueryParameter("page", page.toString())
    }.build()

    private suspend fun fetchList(url: HttpUrl): MangasPage {
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // The grid after the sort menu; pinned VIP comics come before it and random picks after
        val mangas = document.select("#sorting_block_box ~ ul#comix_directory > li.comix:has(> a)").map { it.toSManga() }
        return MangasPage(mangas, document.selectFirst("a.pstr-next") != null)
    }

    private fun Element.toSManga() = SManga.create().apply {
        setUrlWithoutDomain(selectFirst("> a")!!.absUrl("href"))
        title = selectFirst(".comix_title")!!.text().cleanTitle()
        thumbnail_url = selectFirst("img.comix_img")?.absUrl("data-src")
        genre = select("ul.tags_ul a").joinToString { it.text() }.ifEmpty { null }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.firstOrNull() != comicFolder) return null
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val link = document.selectFirst("#comix_description h1 a") ?: return null
        return document.toDetailsSManga().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        val chapter = SChapter.create().apply {
            url = manga.url
            name = "Chapter"
            scanlator = document.select("#comix_description a[href*=/translator/]").joinToString { it.text() }.ifEmpty { null }
            date_upload = DateTimeFormatter.ISO_LOCAL_DATE.tryParseDate(
                document.selectFirst(".right_box li:has(img[data-src$=/date.svg]) .cifra-info-set")?.text(),
            )
        }
        return SMangaUpdate(document.toDetailsSManga().apply { url = manga.url }, listOf(chapter))
    }

    private fun Document.toDetailsSManga() = SManga.create().apply {
        val info = selectFirst("#comix_description .right_box")!!
        title = info.selectFirst("h1")!!.text().cleanTitle()
        thumbnail_url = selectFirst("meta[property=og:image]")?.absUrl("content")
        author = info.select("a[href*=/studios/]").joinToString { it.text() }.ifEmpty { null }
        genre = (info.select("a[href*=/categories/]") + info.select("a[href*=/tag_pagex/]"))
            .map { it.text() }
            .distinctBy { it.lowercase() }
            .joinToString()
            .ifEmpty { null }
        description = MarkdownDescription.Builder()
            .description(info.selectFirst(".info_box > p:not(#image-count)")?.text())
            .description(selectFirst("#caution_box:has(a[href*=/vip/]) p")?.text())
            .build()
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        return document.select("#more_comics_directory_1 li.comix:has(> a)")
            // Russian links use the /comics/ alias of /comicsx/
            .map { it.toSManga().apply { url = url.replaceFirst(RU_ALIAS_REGEX, "/comicsx/") } }
            // The block lists the comic itself first and can link other languages
            .filter { it.url != manga.url && it.url.startsWith("/$comicFolder/") }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        // The cover opens the site's reader, followed by the pages
        return document.select("ul#comix_pages_ul > li img[data-src]").mapIndexed { index, image ->
            Page(index, imageUrl = image.absUrl("data-src"))
        }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(listUrl("home", 1, SORT_DATE), Fetch.DOCUMENT).asJsoup()
        return document.select("a[href*=/categories/]")
            .mapNotNull { it.absUrl("href").toHttpUrl().queryParameter("cat")?.takeIf(String::isNotBlank) }
            .distinct()
            .toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val categories = data?.parseAs<List<String>>().orEmpty()
        return FilterList(
            buildList {
                add(Filter.Header("Text search ignores category, tag, studio and list; of those only the first one set is used"))
                add(SortFilter())
                if (categories.isNotEmpty()) add(CategoryFilter(categories))
                add(TagFilter())
                add(StudioFilter())
                add(ListFilter())
            },
        )
    }

    private fun String.cleanTitle() = replace(TITLE_PREFIX_REGEX, "")

    companion object {
        private val RU_ALIAS_REGEX = """^/comics/""".toRegex()

        // Every title starts with the site's "Porn comic" label in the page's language
        private val TITLE_PREFIX_REGEX = """^(?:porn comic|comic porno|porno comic|порно комикс)\s+""".toRegex(RegexOption.IGNORE_CASE)
    }
}
