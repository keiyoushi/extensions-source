package eu.kanade.tachiyomi.extension.en.egscomics

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
import keiyoushi.utils.tryParseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ElGoonishShive : KeiSource() {
    override val supportsLatest = false

    private val dateFormat = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val manga = listOf(
            SManga.create().apply {
                title = name
                artist = "Dan Shive"
                author = artist
                status = SManga.ONGOING
                url = "/comic/archive"
                description = "El Goonish Shive is a comic about a group of teenagers who face " +
                    "both real life and bizarre, supernatural situations. \n\n" +
                    "It is a comedy mixed with drama and is recommended for audiences thirteen " +
                    "and older."
                thumbnail_url =
                    "https://static.tumblr.com/8cee5e83d26a8a96ad5e51b67f2e340e/j8ipbno/fXFoj0zh9/tumblr_static_1f2fhwjyya74gsgs888g8k880.png"
                initialized = true
            },
            SManga.create().apply {
                title = "$name: NewsPaper"
                artist = "Dan Shive"
                author = artist
                status = SManga.ONGOING
                url = "/egsnp/archive"
                description = "El Goonish Shive is a comic about a group of teenagers who face " +
                    "both real life and bizarre, supernatural situations. \n\n" +
                    "It is a comedy mixed with drama and is recommended for audiences thirteen " +
                    "and older. \n\n" +
                    "EGS:NP is a subsection with short stories that generally aren't canon " +
                    "unless stated"
                thumbnail_url =
                    "https://static.tumblr.com/8cee5e83d26a8a96ad5e51b67f2e340e/j8ipbno/fXFoj0zh9/tumblr_static_1f2fhwjyya74gsgs888g8k880.png"
                initialized = true
            },
            SManga.create().apply {
                title = "$name Sketchbook"
                artist = "Dan Shive"
                author = artist
                status = SManga.ONGOING
                url = "/sketchbook/archive"
                description = "El Goonish Shive is a comic about a group of teenagers who face " +
                    "both real life and bizarre, supernatural situations. \n\n" +
                    "It is a comedy mixed with drama and is recommended for audiences thirteen " +
                    "and older. \n\n" +
                    "The Sketchbook section is full of one-shot gags, sketches, comics that " +
                    "don't fit elsewhere."
                thumbnail_url =
                    "https://static.tumblr.com/8cee5e83d26a8a96ad5e51b67f2e340e/j8ipbno/fXFoj0zh9/tumblr_static_1f2fhwjyya74gsgs888g8k880.png"
                initialized = true
            },
        )

        return MangasPage(manga, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage = MangasPage(emptyList(), false)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) return SMangaUpdate(manga, chapters)

        val document = client.get(getMangaUrl(manga)).asJsoup()
        val chapterList = document.select("select[name=comic] option[value~=^(comic|egsnp|sketchbook)]").map { element ->
            SChapter.create().apply {
                chapter_number = element.previousElementSiblings().size.toFloat()
                setUrlWithoutDomain("/" + element.attr("value"))
                name = element.text().split(" - ", limit = 2).last()
                date_upload = dateFormat.tryParseDate(element.text().split(" - ", limit = 2).first())
            }
        }.reversed()

        return SMangaUpdate(manga, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#cc-comic").mapIndexed { i, element ->
            Page(i, imageUrl = element.absUrl("src"))
        }
    }
}
