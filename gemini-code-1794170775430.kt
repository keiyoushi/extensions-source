package eu.kanade.tachiyomi.extension.fr.punkrecords

// Ajouter ici les imports standards de tachiyomi-extensions-lib
// (GET, ParsedHttpSource, SManga, SChapter, Page, MangasPage, FilterList, etc.)
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class PunkRecords : ParsedHttpSource() {

    override val name = "Punk Records"
    override val baseUrl = "https://punkrecordz.com"
    override val lang = "fr"
    override val supportsLatest = true

    // --- 1. Mangas populaires & Dernières sorties ---
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/catalogue", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/catalogue?sort=updated", headers)

    override fun popularMangaSelector(): String = "main a[href^='/manga/']"
    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.selectFirst("h2, h3, span")?.text()?.trim() ?: element.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesNextPageSelector(): String? = null

    // --- 2. Recherche (filtrage direct sur le catalogue) ---
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList
    ): Observable<MangasPage> {
        return fetchPopularManga(page).map { mangasPage ->
            val filtered = mangasPage.mangas.filter {
                it.title.contains(query.trim(), ignoreCase = true)
            }
            MangasPage(filtered, false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        popularMangaRequest(page)
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String? = null

    // --- 3. Détails d'un manga (/manga/<slug>) ---
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("main h1")?.text()?.trim() ?: ""
        description = document.selectFirst("main p")?.text()?.trim()
        thumbnail_url = document.selectFirst("main img")?.absUrl("src")

        // Ligne d'infos du type : "EIICHIRO ODA · 1997 · 1180 CHAPITRES · SHŌNEN"
        val metaText = document.select("main div, main p")
            .firstOrNull { it.text().contains("CHAPITRES", ignoreCase = true) }
            ?.text()

        if (metaText != null) {
            val parts = metaText.split("·").map { it.trim() }
            author = parts.firstOrNull()
            genre = parts.getOrNull(3)
        }
    }

    // --- 4. Liste des chapitres ---
    override fun chapterListSelector(): String = "main a[href]"

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val slug = response.request.url.encodedPath.removePrefix("/manga/").trim('/')
        val chapterRegex = Regex("^/$slug/([0-9.]+)$")

        return doc.select(chapterListSelector())
            .mapNotNull { el ->
                val href = el.attr("href")
                val match = chapterRegex.find(href) ?: return@mapNotNull null
                val chapNum = match.groupValues[1]

                SChapter.create().apply {
                    url = href
                    name = "Chapitre $chapNum"
                    chapter_number = chapNum.toFloatOrNull() ?: -1f
                }
            }
            .distinctBy { it.url }
    }

    override fun chapterFromElement(element: Element): SChapter =
        throw UnsupportedOperationException("Non utilisé")

    // --- 5. Pages du chapitre (Images & Payload Next.js) ---
    override fun pageListParse(document: Document): List<Page> {
        // 1. Extraction depuis les balises <img> classiques
        val domImages = document.select("main img[src*='/api/media/']")
            .map { it.absUrl("src") }

        if (domImages.isNotEmpty()) {
            return domImages.mapIndexed { index, imgUrl -> Page(index, "", imgUrl) }
        }

        // 2. Fallback : extraction depuis les scripts Next.js si les pages sont chargées dynamiquement
        val html = document.html()
        val mediaRegex = Regex("""(/api/media/[^"'\s\\]+)""")
        return mediaRegex.findAll(html)
            .map { baseUrl + it.groupValues[1].replace("\\u0026", "&") }
            .distinct()
            .mapIndexed { index, imgUrl -> Page(index, "", imgUrl) }
            .toList()
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Non utilisé")
}