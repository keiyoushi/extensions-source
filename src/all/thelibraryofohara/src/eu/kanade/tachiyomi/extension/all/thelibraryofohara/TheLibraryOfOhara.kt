package eu.kanade.tachiyomi.extension.all.thelibraryofohara

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
import keiyoushi.utils.tryParse
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class TheLibraryOfOhara : KeiSource() {

    private val siteLang: String
        get() = when (lang) {
            "id" -> "Indonesia"
            "en" -> "English"
            "es" -> "Spanish"
            "it" -> "Italian"
            "ar" -> "Arabic"
            "fr" -> "French"
            else -> lang
        }

    override val supportsLatest = false

    // Popular

    private fun popularMangaSelector() = when (lang) {
        "en" ->
            "#categories-7 ul li.cat-item-589813936," + // Chapter Secrets
                "#categories-7 ul li.cat-item-607613583, " + // Chapter Secrets Specials
                "#categories-7 ul li.cat-item-43972770, " + // Charlotte Family
                "#categories-7 ul li.cat-item-9363667, " + // Complete Guides
                "#categories-7 ul li.cat-item-634609261, " + // Parody Chapter
                "#categories-7 ul li.cat-item-699200615, " + // Return to the Reverie
                "#categories-7 ul li.cat-item-139757, " + // SBS
                "#categories-7 ul li.cat-item-22695, " + // Timeline
                "#categories-7 ul li.cat-item-648324575"

        // Vivre Card Databook
        "id" -> "#categories-7 ul li.cat-item-702404482, #categories-7 ul li.cat-item-699200615"

        // Chapter Secrets Bahasa Indonesia, Return to the Reverie
        "fr" -> "#categories-7 ul li.cat-item-699200615"

        // Return to the Reverie
        "ar" -> "#categories-7 ul li.cat-item-699200615"

        // Return to the Reverie
        "it" -> "#categories-7 ul li.cat-item-699200615"

        // Return to the Reverie
        else -> "#categories-7 ul li.cat-item-693784776, #categories-7 ul li.cat-item-699200615" // Chapter Secrets (multilingual), Return to the Reverie
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select(popularMangaSelector()).map { element ->
            SManga.create().apply {
                title = element.select("a").text()
                setUrlWithoutDomain(element.select("a").attr("abs:href"))
            }
        }
        return MangasPage(mangas, false)
    }

    // Latest - not supported

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(getPopularManga(1).mangas.filter { it.title.contains(query, ignoreCase = true) }, false)

    // Details

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val title = document.select("h1.page-title").text().replace("Category: ", "")

        val updatedManga = manga.apply {
            this.title = title
            thumbnail_url = chooseChapterThumbnail(document, title)
            description = ""
            status = SManga.ONGOING
        }

        return SMangaUpdate(
            updatedManga,
            if (fetchChapters) getChapterList(document) else chapters,
        )
    }

    // Use one of the chapter thumbnails as manga thumbnail
    // Some thumbnails have a flag on them which indicates the Language.
    // Try to choose a thumbnail with a matching flag
    private fun chooseChapterThumbnail(document: Document, mangaTitle: String): String? {
        var imgElement: Element? = null

        // Reverie
        if (mangaTitle.contains("Reverie")) {
            imgElement = document.select("article").firstOrNull { element ->
                val chapterTitle = element.select("h2.entry-title a").text()
                chapterTitle.contains(siteLang) || (lang == "en" && !chapterTitle.contains(reverieLangRegex))
            }
        }
        // Chapter Secrets (multilingual)
        if (mangaTitle.contains("Chapter Secrets") && lang != "en") {
            imgElement = document.select("article").firstOrNull {
                val chapterTitle = it.select("h2.entry-title a").text()
                (lang == "id" && chapterTitle.contains("Indonesia")) || (lang == "es" && !chapterTitle.contains("Indonesia"))
            }
        }

        // Fallback
        imgElement = imgElement ?: document.select("article:first-of-type").firstOrNull()
        return imgElement?.select("img")?.attr("abs:src")
    }

    // Chapters

    private fun chapterNextPageSelector() = "div.nav-previous a"

    private suspend fun getChapterList(firstPage: Document): List<SChapter> {
        val allChapters = mutableListOf<SChapter>()
        var document = firstPage

        while (true) {
            val pageChapters = document.select("article").map { element ->
                SChapter.create().apply {
                    setUrlWithoutDomain(element.select("a.entry-thumbnail").attr("abs:href"))
                    name = element.select("h2.entry-title a").text()
                    date_upload = Instant.tryParse(element.select("span.posted-on time").attr("datetime"))
                }
            }
            if (pageChapters.isEmpty()) {
                break
            }

            allChapters += pageChapters

            val nextLink = document.select(chapterNextPageSelector())
            if (nextLink.isEmpty()) {
                break
            }

            val nextUrl = nextLink.attr("abs:href")
            document = client.get(nextUrl).asJsoup()
        }

        if (allChapters.isNotEmpty() && allChapters[0].name.contains("Reverie")) {
            return when (lang) {
                "fr" -> allChapters.filter { it.name.contains("French") }
                "ar" -> allChapters.filter { it.name.contains("Arabic") }
                "it" -> allChapters.filter { it.name.contains("Italian") }
                "id" -> allChapters.filter { it.name.contains("Indonesia") }
                "es" -> allChapters.filter { it.name.contains("Spanish") }
                else -> allChapters.filter {
                    !it.name.contains("French") &&
                        !it.name.contains("Arabic") &&
                        !it.name.contains("Italian") &&
                        !it.name.contains("Indonesia") &&
                        !it.name.contains("Spanish")
                }
            }
        }

        // Remove Indonesian posts if lang is spanish
        if (lang == "es") {
            return allChapters.filter { !it.name.contains("Indonesia") }
        }

        return allChapters
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("div.entry-content").select("a img, img.size-full").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("data-orig-file"))
        }
    }

    companion object {
        private val reverieLangRegex = Regex("""(French|Arabic|Italian|Indonesia|Spanish)""")
    }
}
