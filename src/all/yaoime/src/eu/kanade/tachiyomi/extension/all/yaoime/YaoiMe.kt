package eu.kanade.tachiyomi.extension.all.yaoime

import android.util.Base64
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.asJsoup
import keiyoushi.utils.decodeHex
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

@Source
abstract class YaoiMe :
    KeiSource(),
    ConfigurableSource {

    override val supportsRelatedMangas = true

    override val supportsFilterFetching = true

    private val preferences by getPreferencesLazy()

    private val siteLang get() = lang.lowercase()

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", SortFilter.POPULAR)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", SortFilter.LATEST)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) addQueryParameter("q", query.trim())
            addQueryParameter("langs", siteLang)
            filters.firstInstanceOrNull<SortFilter>()?.let {
                addQueryParameter("sort", it.sort)
                addQueryParameter("dir", it.dir)
            }
            filters.firstInstanceOrNull<GenreFilter>()?.let {
                addListParameter("genre", it.included)
                addListParameter("without", it.excluded)
            }
            filters.firstInstanceOrNull<TagFilter>()?.let {
                addListParameter("tags", it.included)
                addListParameter("withoutTags", it.excluded)
            }
            filters.firstInstanceOrNull<TypeFilter>()?.selected?.let { addQueryParameter("type", it) }
            filters.firstInstanceOrNull<StatusFilter>()?.selected?.let { addQueryParameter("status", it) }
            filters.firstInstanceOrNull<ContentFilter>()?.selected?.let { addQueryParameter("content", it) }
            filters.firstInstanceOrNull<DemographicFilter>()?.selected?.let { addQueryParameter("demographic", it) }
            if (page > 1) addQueryParameter("page", page.toString())
        }.build()

        val document = client.get(url).asJsoup()
        val covers = document.select("a.tile").associate { it.attr("href") to it.selectFirst("img")?.coverUrl() }
        val mangas = document.select("a.card-title").map { link ->
            SManga.create().apply {
                this.url = link.absUrl("href").toSeriesId()
                title = link.attr("title")
                thumbnail_url = covers[link.attr("href")]
            }
        }
        return MangasPage(mangas, document.selectFirst("nav a:containsOwn(Next)") != null)
    }

    private fun HttpUrl.Builder.addListParameter(name: String, values: List<String>) {
        if (values.isNotEmpty()) addQueryParameter(name, values.joinToString(","))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.firstOrNull() !in setOf("series", "chapter")) return null
        val id = url.pathSegments.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return null
        return client.get("$baseUrl/series/$id").asJsoup().toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        if (!fetchChapters) return SMangaUpdate(document.toSManga(), chapters)

        val chapterList = document.extractNextJs<ChapterListDto>()?.let { list ->
            // Only the reader API has the display names
            val names = list.anyVariant?.let { readerChapter(it.s, it.id).sources }.orEmpty()
            list.toSChapterList(siteLang, preferences.getBoolean(PREF_DEDUPE, false), names)
        }.orEmpty()
        return SMangaUpdate(document.toSManga(), chapterList)
    }

    private fun Document.toSManga() = SManga.create().apply {
        val series = select("script[type=application/ld+json]")
            .flatMap { it.data().parseAs<List<SeriesLdDto>>() }
            .first { it.type == "ComicSeries" }
        url = series.url!!.toSeriesId()
        title = series.name!!
        thumbnail_url = series.image?.withoutQuery()

        val authors = mutableListOf<String>()
        val artists = mutableListOf<String>()
        selectFirst("h1 ~ div:has(> a[href^=/author/])")?.let { credits ->
            var role = ""
            for (node in credits.childNodes()) {
                if (node is TextNode && node.text().isNotBlank()) {
                    role = node.text().lowercase()
                } else if (node is Element && node.tagName() == "a") {
                    if ("story" in role) authors += node.text()
                    if ("art" in role) artists += node.text()
                }
            }
        }
        author = authors.joinToString().ifEmpty { null }
        artist = artists.joinToString().ifEmpty { null }

        genre = select("a[href^=/genre/], a[href^=/tag/]").joinToString { it.text() }
        status = when (selectFirst("dt:containsOwn(Status) + dd")?.text()?.lowercase()) {
            "completed" -> SManga.COMPLETED
            "releasing" -> SManga.ONGOING
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }

        val altTitles = series.alternateName
        val links = select("section:has(> h2:containsOwn(Read or buy)) a[href]").map { "[${it.text()}](${it.absUrl("href")})" }
        description = buildString {
            selectFirst("section:has(> h2:containsOwn(Synopsis)) .prose-text")?.let { append(it.wholeText().trim()) }
            if (altTitles.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Alternative titles:\n")
                altTitles.joinTo(this, "\n") { "- $it" }
            }
            if (links.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append("Links:\n")
                links.joinTo(this, "\n") { "- $it" }
            }
        }.ifEmpty { null }
        initialized = true
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = client.get(getMangaUrl(manga)).asJsoup()
        .select("main a[title][href^=/series/]")
        .map { link ->
            SManga.create().apply {
                url = link.absUrl("href").toSeriesId()
                title = link.attr("title")
                thumbnail_url = link.selectFirst("img")?.coverUrl()
            }
        }
        .filter { it.url != manga.url }
        .distinctBy { it.url }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val reader = readerChapter(chapter.memo[MEMO_SOURCE]!!.int, chapter.url.toLong())
        val hashes = reader.sealed?.let(::unseal) ?: reader.pages.map { it.hash }

        return reader.pages.mapIndexed { i, page ->
            // pages up to 1400px wide are served resized (WebP), larger ones as the original.
            val width = page.w?.takeIf { it > 0 }?.let { w -> IMAGE_WIDTHS.firstOrNull { it >= w } }
            Page(i, imageUrl = "$baseUrl/i/p/${hashes[i]}" + (width?.let { "?w=$it" } ?: ""))
        }
    }

    private suspend fun readerChapter(seriesId: Int, chapterId: Long): ReaderChapterDto {
        val path = "/api/reader/chapter?s=$seriesId&c=$chapterId"
        val time = System.currentTimeMillis().toString()
        val signature = "$path $time".hmacSha256(SIGN_KEY.decodeHex())
        val signedHeaders = headersBuilder()
            .set("x-yf-t", time)
            .set("x-yf-s", Base64.encodeToString(signature, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            .build()
        return client.get("$baseUrl$path", signedHeaders).parseAs<ReaderDto>().chapter
    }

    private fun unseal(sealed: String): List<String> {
        val data = Base64.decode(sealed, Base64.URL_SAFE)
        val salt = data.copyOfRange(0, 16)
        val iv = data.copyOfRange(16, 28)
        val key = (SEAL_KEY.decodeHex() + salt).sha256()
        return data.copyOfRange(28, data.size)
            .aesDecrypt(aesGcm(key, iv))
            .decodeToString()
            .parseAs<SealedPagesDto>()
            .p
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/chapter/${chapter.memo[MEMO_SOURCE]!!.int}/${chapter.url}"

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/browse").asJsoup()
        .extractNextJs<FilterDataDto>()!!
        .toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SortFilter())
            data?.parseAs<FilterDataDto>()?.let { filters ->
                add(TypeFilter(filters.types.map { it.value }))
                add(StatusFilter(filters.statuses.map { it.value }))
                add(ContentFilter(filters.content.map { it.value }))
                add(DemographicFilter(filters.demographics.map { it.value }))
                add(GenreFilter(filters.genres.map { it.name }))
                add(TagFilter(filters.tags.map { it.name }))
            }
        },
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_DEDUPE
            title = "Deduplicate chapters"
            summary = "Show each chapter once, like the site's \"Deduplicate chapters & clean order\". Extras and unnumbered chapters are hidden."
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private fun String.toSeriesId() = toHttpUrl().pathSegments[1]

    private fun Element.coverUrl() = absUrl("src").withoutQuery()

    private fun String.withoutQuery() = toHttpUrl().newBuilder().query(null).build().toString()

    companion object {
        private val IMAGE_WIDTHS = listOf(90, 140, 200, 260, 360, 500, 720, 1000, 1400)

        private const val SIGN_KEY = "85cedf4fed3602c3a35c18010b502584dee9594708ba673052d562c7f2b1a9de"
        private const val SEAL_KEY = "10dd8dff9911000f9286d3dfd8b9f72b3f763c2f783f89f5c384cff4a11988ac"

        private const val PREF_DEDUPE = "pref_dedupe_chapters"
    }
}
