package eu.kanade.tachiyomi.extension.es.hibonblscanlation

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaEntryDto
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Source
abstract class HibonBlScanlation : ZeistManga() {

    // Popular lists the series newest first: the home page's popular widget has five, novels included
    override val supportsLatest = false

    // Novels, whose chapters are text, are left out
    override val includedCategories get() = listOf(MANGA_LABEL)

    override val chapterCategory = "Capitulo"

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments
        if (url.host != baseUrl.toHttpUrl().host || segments.size != 3 || segments[0] != "search" || segments[1] != "label") {
            return super.getMangasByUrl(url, page)
        }
        // A label page, such as a genre's: the series list narrowed to that label
        val listUrl = latestUpdatesUrl(page).toHttpUrl()
        val labelUrl = listUrl.newBuilder()
            .setQueryParameter("q", "${listUrl.queryParameter("q")} label:\"${segments[2]}\"")
            .build()
        return parseSearchManga(client.get(labelUrl, Fetch.XHR))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !POST_PATH_REGEX.matches(url.encodedPath)) return null
        val post = getPost(url.encodedPath)
        val series = if (SERIES_LABEL in post.labels()) {
            post
        } else {
            // A chapter post carries the label of its series
            val label = post.labels().singleOrNull { it != chapterCategory && it != "Project" } ?: return null
            val seriesUrl = apiUrl(SERIES_LABEL).addPathSegment(label).build()
            client.get(seriesUrl, Fetch.XHR).parseAs<ZeistMangaDto>().feed?.entry?.firstOrNull() ?: return null
        }
        if (MANGA_LABEL !in series.labels()) return null
        return getMangaUpdate(series.toSManga(baseUrl), emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga = { if (fetchDetails) mangaDetailsParse(client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()).apply { url = manga.url } else manga },
        chapters = { if (fetchChapters) getChapters(manga.url) else chapters },
    )

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val heading = document.selectFirst("h1[itemprop=name]")!!
        title = heading.text()
        thumbnail_url = document.selectFirst("#post-body .tab-content img")?.absUrl("src")
        val info = document.select("#extra-info dl").mapNotNull { row ->
            val name = row.selectFirst("dt")?.text()?.removeSuffix(":") ?: return@mapNotNull null
            row.selectFirst("dd")?.text()?.let { name to it }
        }.toMap()
        author = info["Autor"] ?: info["Mangaka"]
        artist = info["Artista"] ?: info["Mangaka"]
        genre = document.select("#append-info dt:containsOwn(Genero) + dd a").joinToString { it.text() }.ifEmpty { null }
        val state = document.selectFirst("#append-info dt:containsOwn(Estado) + dd")?.text()
        status = when (state?.lowercase()) {
            "activo" -> SManga.ONGOING
            "completado", "one-shot" -> SManga.COMPLETED
            "pausado" -> SManga.ON_HIATUS
            "cancelado" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
        if (state == "One-shot") update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        description = MarkdownDescription.Builder()
            .description(document.selectFirst("#synopsis"))
            .apply { info.filterKeys { it !in CREDITS }.forEach { (name, value) -> field(name, value) } }
            .altTitles(heading.previousElementSibling()?.selectFirst("span:not([data-bg])")?.text()?.split(",").orEmpty())
            .build()
    }

    private suspend fun getChapters(mangaUrl: String): List<SChapter> {
        val post = getPost(mangaUrl)
        val label = post.chapterLabel()
        if (label == null) {
            // One-shots show their pages on the series post itself
            val oneShot = Jsoup.parseBodyFragment(post.content!!.t).selectFirst("#capitulo-one-shot img") != null
            return if (oneShot) listOf(post.toSChapter(baseUrl, parseDate(post.getPublishedDate())).apply { name = "One-shot" }) else emptyList()
        }
        // Titles and dates are enough: the summary feed is a quarter of the size
        val feedUrl = "$baseUrl/feeds/posts/summary/-/".toHttpUrl().newBuilder()
            .addPathSegment(label)
            .addQueryParameter("alt", "json")
            .addQueryParameter("max-results", "500")
            .build()
        return client.get(feedUrl, Fetch.XHR).parseAs<ZeistMangaDto>().feed!!.entry.orEmpty()
            .filter { SERIES_LABEL !in it.labels() }
            .map { entry ->
                entry.toSChapter(baseUrl, parseDate(entry.getPublishedDate())).apply { name = name.replace(SPACES_REGEX, " ").trim() }
            }
    }

    // A few series posts name another series' label for their chapters: the site then takes the label matching the title
    private fun ZeistMangaEntryDto.chapterLabel(): String? {
        val labels = labels()
        chapterFeedRegex.find(content!!.t)?.groupValues?.get(1)?.takeIf { it in labels }?.let { return it }
        val titleWords = title!!.t.words()
        return labels.map { it to it.words().count(titleWords::contains) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    // The chapter page keeps the post in a textarea for its reader script; the feed has the same post, much smaller
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val post = Jsoup.parseBodyFragment(getPost(chapter.url).content!!.t, baseUrl)
        // One-shots show their pages under the series' cover
        val images = post.selectFirst("#capitulo-one-shot")?.select("img") ?: post.select("img")
        // Like the site's reader, which leaves out the hidden thumbnail some posts start with
        return images.filterNot { it.hasAttr("hidden") || it.hasClass("hidden") || HIDDEN_STYLE_REGEX.containsMatchIn(it.attr("style")) }
            .mapIndexed { i, img -> Page(i, imageUrl = img.absUrl("src")) }
    }

    override val hasFilters = true
    override val hasTypeFilter = false
    override val hasLanguageFilter = false

    override fun getStatusList() = listOf(
        Status(intl["all"], ""),
        Status(intl["status_ongoing"], "Activo"),
        Status(intl["status_completed"], "Completado"),
        Status(intl["status_hiatus"], "Pausado"),
        Status(intl["status_cancelled"], "Cancelado"),
        Status(intl["status_upcoming"], "Futuro"),
        Status("One-shot", "One-shot"),
    )

    override fun getGenreList() = listOf(
        "Accion", "Aventura", "Ciencia ficcion", "Comedia", "Deportes", "Drama", "Escolar", "Fantasia",
        "Policial", "Psicologico", "Recuentos de la vida", "Romance", "Sobrenatural", "Terror", "Tragedia",
    ).map { Genre(it, it) }

    private suspend fun getPost(path: String): ZeistMangaEntryDto {
        val postUrl = apiUrl().addQueryParameter("path", path).build()
        return client.get(postUrl, Fetch.XHR).parseAs<ZeistMangaDto>().feed!!.entry!!.single()
    }

    private fun ZeistMangaEntryDto.labels() = category.orEmpty().map { it.term }

    private fun String.words() = lowercase().split(WORD_SEPARATOR_REGEX).filter { it.isNotEmpty() }.toSet()

    companion object {
        private const val MANGA_LABEL = "Manga"
        private const val SERIES_LABEL = "Series"
        private val CREDITS = setOf("Autor", "Artista", "Mangaka")
        private val POST_PATH_REGEX = Regex("""/\d{4}/\d{2}/[^/]+\.html""")
        private val WORD_SEPARATOR_REGEX = Regex("""\W+""")
        private val SPACES_REGEX = Regex("""\s+""")
        private val HIDDEN_STYLE_REGEX = Regex("""display\s*:\s*none""", RegexOption.IGNORE_CASE)
    }
}
