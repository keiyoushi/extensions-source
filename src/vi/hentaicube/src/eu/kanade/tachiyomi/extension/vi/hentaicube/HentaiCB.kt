package eu.kanade.tachiyomi.extension.vi.hentaicube

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiCB : Madara("CBHentai", "https://hentaicube.xyz", "vi", SimpleDateFormat("dd/MM/yyyy", Locale("vi"))) {

    override val id: Long = 823638192569572166

    override val filterNonMangaItems = false

    // Changed from 'manga' to 'read'
    override val mangaSubString = "read"

    override val altNameSelector = ".post-content_item:contains(Tên khác) .summary-content"

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            // Changed from 'read' to 'manga'
            val mangaUrl = "/manga/${query.substringAfter(URL_SEARCH_PREFIX)}/"
            return client.newCall(GET("$baseUrl$mangaUrl", headers))
                .asObservableSuccess().map { response ->
                    val manga = mangaDetailsParse(response).apply {
                        url = mangaUrl
                    }

                    MangasPage(listOf(manga), false)
                }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    private val oldMangaUrlRegex by lazy { Regex("""^$baseUrl/read/""") }

    // Change old entries from 'read' to 'manga'
    override fun getMangaUrl(manga: SManga): String {
        return super.getMangaUrl(manga)
            .replace(oldMangaUrlRegex, "$baseUrl/manga/")
    }

    override fun pageListParse(document: Document): List<Page> {
        return super.pageListParse(document).distinctBy { it.imageUrl }
    }
}
