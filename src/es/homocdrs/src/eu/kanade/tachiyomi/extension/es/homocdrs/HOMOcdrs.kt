package eu.kanade.tachiyomi.extension.es.homocdrs

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaDto
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistMangaEntryDto
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Source
abstract class HOMOcdrs : ZeistManga() {

    override val supportsLatest = false

    override fun parseSearchManga(response: Response): MangasPage = super.parseSearchManga(response).apply {
        mangas.forEach { it.title = it.title.trim() }
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.pathSegments
        if (url.host == baseUrl.toHttpUrl().host && segments.size == 3 && segments[0] == "search" && segments[1] == "label") {
            return getSearchMangaList(page, "label:\"${segments[2]}\"", FilterList())
        }
        return super.getMangasByUrl(url, page)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || !POST_PATH_REGEX.matches(url.encodedPath)) return null
        val post = getPost(url.encodedPath)
        val labels = post.labels()
        val series = if (chapterCategory in labels) findSeries(labels) else post
        if (series == null || series.labels().any { it in excludedCategories }) return null
        return series.toSMangaDetails().apply { initialized = true }
    }

    // A chapter post carries the label that its series passes to clwd.run
    private suspend fun findSeries(chapterLabels: List<String>): ZeistMangaEntryDto? {
        val query = chapterLabels.filter { it != chapterCategory && it !in includedCategories }
            .joinToString(" ", "label:\"Series\" {", "}") { "label:\"$it\"" }
        val url = apiUrl().addQueryParameter("q", query).addQueryParameter("max-results", "150").build()
        return client.get(url, Fetch.XHR).parseAs<ZeistMangaDto>().feed?.entry.orEmpty()
            .firstOrNull { chapterFeedRegex.find(it.content!!.t)?.groupValues?.get(1) in chapterLabels }
    }

    // The post feed has the labels, unlike the series page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val post = getPost(manga.url)
        val document = post.document()
        return SMangaUpdate(
            manga = post.toSMangaDetails(document),
            chapters = if (fetchChapters) getChapterList(getChapterFeedUrl(document, manga.title)) else chapters,
        )
    }

    private fun ZeistMangaEntryDto.toSMangaDetails(document: Document = document()): SManga {
        val info = document.select("#extra-info dl").associate { dl ->
            dl.selectFirst("dt")!!.text().removeSuffix(":") to dl.selectFirst("dd")!!.text()
        }.filterValues { it.isNotEmpty() && it.lowercase() !in PLACEHOLDERS }
        val labels = labels().map { it.lowercase() }
        return toSManga(baseUrl).apply {
            title = title.trim()
            thumbnail_url = document.selectFirst("img")?.absUrl("src")?.toOriginalSize()
            author = info["Autor"]
            genre = info["Tags"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString()?.ifEmpty { null }
            description = MarkdownDescription.Builder()
                .description(document.selectFirst("#synopsis"))
                .field("Volume", info["Volume"])
                .field("Chapter", info["Chapter"])
                .altTitles(listOfNotNull(info["Nombre"]?.takeIf { it != title }))
                .build()
            // The labels follow the translation, the Estado field the original work
            status = when {
                labels.any { it in statusCompletedList } -> SManga.COMPLETED
                labels.any { it in statusOnGoingList } -> SManga.ONGOING
                else -> parseStatus(info["Estado"].orEmpty())
            }
            if ("one-shot" in labels) update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // Some chapter pages redirect to Blogger's sensitive content warning, their post feed doesn't
    override suspend fun getPageList(chapter: SChapter): List<Page> = getPost(chapter.url).document().select("img[src]").mapIndexed { i, img ->
        Page(i, imageUrl = img.absUrl("src").toOriginalSize())
    }

    private suspend fun getPost(path: String): ZeistMangaEntryDto {
        val url = apiUrl().addQueryParameter("path", path).build()
        return client.get(url, Fetch.XHR).parseAs<ZeistMangaDto>().feed!!.entry!!.single()
    }

    private fun ZeistMangaEntryDto.labels() = category.orEmpty().map { it.term }

    private fun ZeistMangaEntryDto.document() = Jsoup.parseBodyFragment(content!!.t, baseUrl)

    private fun String.toOriginalSize() = replace(IMAGE_SIZE_REGEX, "/s0/")

    companion object {
        private val POST_PATH_REGEX = Regex("""/\d{4}/\d{2}/[^/]+\.html""")
        private val IMAGE_SIZE_REGEX = Regex("""/[swh]\d+[^/]*/(?=[^/]+$)""")
        private val PLACEHOLDERS = setOf("--", "no definido")
    }
}
