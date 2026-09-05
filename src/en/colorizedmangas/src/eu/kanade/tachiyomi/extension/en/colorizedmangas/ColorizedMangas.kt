package eu.kanade.tachiyomi.extension.en.colorizedmangas

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
import keiyoushi.utils.textOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class ColorizedMangas : KeiSource() {

    override val supportsLatest = false

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = parseMangaList(document)
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = getPopularManga(page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val allMangas = parseMangaList(document)

        val filtered = if (query.isNotBlank()) {
            val cleanQuery = query.trim().lowercase()
            allMangas.filter { it.title.lowercase().contains(cleanQuery) }
        } else {
            allMangas
        }

        return MangasPage(filtered, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val pathSegments = url.pathSegments.filter { it.isNotEmpty() }
        if (pathSegments.isEmpty()) return null

        val slug = pathSegments[0]
        return SManga.create().apply {
            this.url = slug
            this.title = slug.replace("-", " ").replaceFirstChar { it.uppercase() }
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = parseMangaDetails(document, manga)
        val chapterList = parseChapterList(document)

        return SMangaUpdate(updatedManga, chapterList)
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga {
        return manga.apply {
            val aside = document.selectFirst("aside") ?: return@apply

            val titleEl = aside.selectFirst("h1")
            if (titleEl != null) {
                title = titleEl.text().removePrefix("Colorized").trim()
            }

            thumbnail_url = aside.selectFirst("img")?.absUrl("src")

            author = aside.selectFirst("dl > div dt:containsIgnoreCase(author) + dd")?.textOrNull()

            val genresBlock = aside.select("p.text-\\[11px\\]").firstOrNull()
            if (genresBlock != null) {
                genre = genresBlock.text()
                    .split("·")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString()
            }

            description = aside.selectFirst("p.border-t")?.textOrNull()
            status = SManga.UNKNOWN
        }
    }

    private fun parseChapterList(document: Document): List<SChapter> {
        val chapterLinks = document.select("div.space-y-4 section div.space-y-2 > a")

        val chapters = chapterLinks.mapNotNull { element ->
            val chNumText = element.selectFirst("span.font-bold")?.textOrNull()
            val chTitleText = element.selectFirst("div.truncate")?.textOrNull()

            if (chNumText == null && chTitleText == null) {
                return@mapNotNull null
            }

            val chapterName = buildString {
                if (chNumText != null) {
                    append(chNumText)
                    if (chTitleText != null && !chTitleText.equals(chNumText, true)) {
                        append(" — ")
                        append(chTitleText)
                    }
                } else if (chTitleText != null) {
                    append(chTitleText)
                }
            }

            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = chapterName
                val chNumMatch = CHAPTER_NUMBER_REGEX.find(chNumText ?: chTitleText ?: "")
                chapter_number = chNumMatch?.value?.toFloatOrNull() ?: -1f
            }
        }

        return chapters.sortedWith(
            compareByDescending<SChapter> { it.chapter_number }
                .thenByDescending { it.name },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = "$baseUrl${chapter.url}"
        val document = client.get(chapterUrl).asJsoup()

        return document.select("main img[src*=pages]").mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("src"))
        }
    }

    private fun parseMangaList(document: Document): List<SManga> {
        val elements = document.select("div.lib-color-view a[href], div.lib-bw-view a[href]")
        val seenUrls = mutableSetOf<String>()

        return elements.mapNotNull { element ->
            val href = element.attr("href").removeSuffix("/")
            val slug = href.substringAfterLast("/")
            if (slug.isBlank() || !seenUrls.add(slug)) return@mapNotNull null

            val titleText = element.selectFirst("h3")?.textOrNull()
                ?: return@mapNotNull null

            SManga.create().apply {
                url = slug
                title = titleText
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
    }

    companion object {
        private val CHAPTER_NUMBER_REGEX = Regex("""\d+(\.\d+)?""")
    }
}
