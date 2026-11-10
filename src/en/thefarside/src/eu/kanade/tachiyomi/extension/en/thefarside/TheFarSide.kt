package eu.kanade.tachiyomi.extension.en.thefarside

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
import keiyoushi.utils.attrOrNull
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class TheFarSide : KeiSource() {

    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(CaptionInterceptor())

    override suspend fun getPopularManga(page: Int) = MangasPage(sections, false)

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = MangasPage(sections.filter { it.title.contains(query, ignoreCase = true) }, false)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        // The Daily Dose has a page per day, /2026/11/09
        val section = url.pathSegments.first().takeUnless { it.all(Char::isDigit) }.orEmpty()
        return sections.firstOrNull { it.url == "/$section" }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        manga.apply {
            description = MarkdownDescription.Builder()
                .description(document.selectFirst("main p.lead"))
                .build()
            thumbnail_url = document.selectFirst("main img[data-src]")?.absUrl("data-src")
                ?: document.selectFirst("meta[property=og:image]")?.attrOrNull("content")
        }

        val chapters = if (fetchChapters) {
            when (manga.url) {
                "/" -> dailyDoses(document)
                "/new-stuff" -> newStuff(document)
                // Newest first, by id: the sketchbooks are listed oldest first
                else -> document.select("a.tfs-page-preview__header[href^=${manga.url}/]")
                    .sortedByDescending { it.attr("href").split('/')[2].toInt() }
                    .map { link ->
                        SChapter.create().apply {
                            setUrlWithoutDomain(link.absUrl("href"))
                            name = link.text()
                        }
                    }
            }
        } else {
            chapters
        }

        return SMangaUpdate(manga, chapters)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        // A New Stuff page holds every post, each in its own slide
        val cartoons = if (chapter.url.startsWith("/new-stuff/")) {
            document.select(".swiper-slide[data-path=\"${chapter.url}\"]")
        } else {
            document.select(".js-comic, .tfs-post__sketch-image")
        }
        return cartoons.mapIndexed { idx, cartoon ->
            val image = cartoon.selectFirst("img[data-src]")!!.absUrl("data-src").toHttpUrl()
            val caption = cartoon.selectFirst("figcaption")?.text()?.ifEmpty { null }
            Page(idx, imageUrl = image.newBuilder().fragment(caption).build().toString())
        }
    }

    // Only the last few days are online: follow them back until there is no previous day
    private suspend fun dailyDoses(today: Document): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document: Document? = today
        while (document != null) {
            val name = document.selectFirst(".tfs-section-header h3")!!.text()
            val day = LocalDate.parse(name, dayFormat)
            chapters += SChapter.create().apply {
                url = day.format(dayPathFormat)
                this.name = name
                date_upload = day.atStartOfDay(zone).toInstant().toEpochMilli()
                // Below 1, so that the days don't read as chapter numbers with missing chapters before them
                chapter_number = day.toEpochDay() / 100_000f
            }
            document = document.selectFirst("a.tfs-button:contains(Previous Day)")
                ?.let { client.get(it.absUrl("href"), Fetch.DOCUMENT).asJsoup() }
        }
        return chapters
    }

    private suspend fun newStuff(landing: Document): List<SChapter> {
        val lab = landing.selectFirst("a.tfs-new-stuff-letter__door")!!.absUrl("href")
        return client.get(lab, Fetch.DOCUMENT).asJsoup().select(".swiper-slide[data-path]").map { slide ->
            SChapter.create().apply {
                url = slide.attr("data-path")
                name = url.substringAfterLast('/').split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                date_upload = postDateFormat.tryParseDate(slide.selectFirst(".tfs-comic-new__meta > span")?.ownText(), zone)
            }
        }.reversed()
    }

    private val sections: List<SManga>
        get() = listOf(
            section("/", "The Daily Dose"),
            section("/new-stuff", "New Stuff"),
            section("/comic-collections", "Comic Collections"),
            section("/sketchbooks", "Sketchbooks"),
        )

    private fun section(url: String, title: String) = SManga.create().apply {
        this.url = url
        this.title = title
        author = "Gary Larson"
        artist = author
    }

    companion object {
        private val zone = ZoneId.of("America/Chicago")
        private val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)
        private val dayPathFormat = DateTimeFormatter.ofPattern("'/'yyyy'/'MM'/'dd", Locale.ENGLISH)
        private val postDateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    }
}
