package eu.kanade.tachiyomi.extension.it.mangaworld

import eu.kanade.tachiyomi.multisrc.mangaworld.MangaWorld
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import org.jsoup.nodes.Document

@Source
abstract class Mangaworld : MangaWorld() {
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val canonical = if (url.host == "www.mangaworld.mx") url.newBuilder().host("www.mangaworld.me").build() else url
        return super.getMangaByUrl(canonical)
    }

    override fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".archive-grid .card, .latest-grid .card").mapNotNull { card ->
            val link = card.selectFirst(".card-title a[href]") ?: return@mapNotNull null
            SManga.create().apply {
                title = link.text()
                setUrlWithoutDomain(link.absUrl("href"))
                thumbnail_url = card.selectFirst(".card-cover img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, document.selectFirst("a[rel=next]") != null)
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.manga-title")?.text() ?: error("Scheda manga non trovata")
        thumbnail_url = document.selectFirst(".manga-cover img")?.absUrl("src")
        author = document.select(".manga-facts a[href*=?author=]").joinToString { it.text() }
        artist = document.select(".manga-facts a[href*=?artist=]").joinToString { it.text() }
        genre = document.select(".hero-chips a[href*=?genre=]").joinToString { it.text() }
        val state = document.selectFirst(".manga-facts a[href*=?status=]")?.text()
        status = if (state.equals("Droppato", ignoreCase = true)) SManga.CANCELLED else parseStatus(state)
        description = listOfNotNull(
            document.selectFirst(".manga-plot")?.text(),
            document.selectFirst(".manga-alt")?.text(),
        ).joinToString("\n\n")
    }

    override fun chapterListParse(document: Document): List<SChapter> = document.chapterLinks().map { link ->
        SChapter.create().apply {
            setUrlWithoutDomain(fixChapterUrl(link.absUrl("href")))
            name = link.selectFirst(".ch-label")!!.text()
            date_upload = parseChapterDate(link.selectFirst(".ch-date")?.text())
            parseChapterNumber(name)?.let { chapter_number = it }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        return document.pageImages().mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }.ifEmpty { error("Nessuna pagina trovata nel capitolo") }
    }
}

internal fun Document.chapterLinks() = select("#capitoli a.ch[href]")

internal fun Document.pageImages() = select("img[data-reader-image][src]").map { it.absUrl("src") }
