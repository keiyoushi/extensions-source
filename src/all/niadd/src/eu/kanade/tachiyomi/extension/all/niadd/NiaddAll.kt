package eu.kanade.tachiyomi.extension.all.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class NiaddAll : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NiaddEn(),
        NiaddPtBr(),
        NiaddEs(),
        NiaddDe(),
        NiaddFr(),
        NiaddIt(),
        NiaddRu()
    )
}

class NiaddAllSource : ParsedHttpSource() {

    override val name = "Niadd"
    override val baseUrl = "https://www.niadd.com"
    override val lang = "all"
    override val supportsLatest = true

    private val sources = listOf(
        NiaddEn(),
        NiaddPtBr(),
        NiaddEs(),
        NiaddDe(),
        NiaddFr(),
        NiaddIt(),
        NiaddRu()
    )

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return sources.first().popularMangaRequest(page)
    }

    override fun popularMangaSelector(): String = sources.first().popularMangaSelector()
    override fun popularMangaFromElement(element: Element): SManga =
        sources.first().popularMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? =
        sources.first().popularMangaNextPageSelector()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        sources.first().latestUpdatesRequest(page)

    override fun latestUpdatesSelector(): String = sources.first().latestUpdatesSelector()
    override fun latestUpdatesFromElement(element: Element): SManga =
        sources.first().latestUpdatesFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? =
        sources.first().latestUpdatesNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return sources.first().searchMangaRequest(page, query, filters)
    }

    override fun searchMangaSelector(): String = sources.first().searchMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga =
        sources.first().searchMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String? =
        sources.first().searchMangaNextPageSelector()

    // Details, Chapters e Pages
    override fun mangaDetailsParse(document: Document): SManga =
        sources.first().mangaDetailsParse(document)

    override fun chapterListRequest(manga: SManga): Request =
        sources.first().chapterListRequest(manga)

    override fun chapterListSelector(): String = sources.first().chapterListSelector()
    override fun chapterFromElement(element: Element): SChapter =
        sources.first().chapterFromElement(element)

    override fun pageListParse(document: Document): List<Page> =
        sources.first().pageListParse(document)

    override fun imageUrlParse(document: Document): String =
        sources.first().imageUrlParse(document)
}
