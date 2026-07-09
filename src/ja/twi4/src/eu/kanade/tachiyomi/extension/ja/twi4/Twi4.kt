package eu.kanade.tachiyomi.extension.ja.twi4

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable

@Source
abstract class Twi4 : HttpSource() {
    override val supportsLatest: Boolean = false

    companion object {
        const val SEARCH_PREFIX_SLUG = "SLUG:"
        private val TITLE_REGEX = Regex("『(.+)』.+ \\| ツイ４ \\| 最前線")
        private val CHAPTER_REGEX = Regex(".+『(.+)』 #(\\d+)")
    }

    private val hostRoot by lazy { baseUrl.toHttpUrl().let { "${it.scheme}://${it.host}" } }

    // Both latest and popular only lists 4 manga in total
    // As the full catalog is consists of less than 50 manga, it is not worth implementing
    // We'll just list all manga in the catalog instead
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        // Manga that are recently updated don't show up on the full catalog
        // So we'll need to parse the recent updates section as well
        val mangas = doc.select("#lineup_recent > div > section, #lineup > div > section:not(.zadankai):not([id])")

        val ret = mangas.mapNotNull { manga ->
            val a = manga.selectFirst("div.hgroup > h3 > a") ?: return@mapNotNull null
            SManga.create().apply {
                thumbnail_url = manga.selectFirst("div.figgroup > figure > a > img")?.attr("abs:src")
                setUrlWithoutDomain(a.attr("abs:href"))
                title = a.text()
                author = manga.selectFirst("div.hgroup > p")?.text()
                status = if (manga.selectFirst("ul > li:last-child > em.is-completed") == null) {
                    SManga.ONGOING
                } else {
                    SManga.COMPLETED
                }
            }
        }
        return MangasPage(ret, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // There is no search functionality in the site
    // It is possible to implement something rudimentary for search to function
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrlOrNull() ?: throw Exception("Invalid URL")
            if (url.host != baseUrl.toHttpUrl().host) {
                throw Exception("Unsupported url")
            }
            val slug = url.pathSegments.getOrNull(2) ?: throw Exception("Unsupported url structure")
            return fetchSearchManga(page, "$SEARCH_PREFIX_SLUG$slug", filters)
        }

        if (query.startsWith(SEARCH_PREFIX_SLUG)) {
            val slug = query.drop(SEARCH_PREFIX_SLUG.length)
            // Explicitly ignore anything that ends with .html or starts with zadankai
            // These will include the completed manga page, about page and zadankai submissions
            // For reasons to exclude zadankai, see parsePopularMangaRequest()

            // There will still be some urls that would accidentally activate the intent (like the news page),
            // but there's no way to avoid it.
            if (slug.endsWith("html") || slug.startsWith("zadankai") || slug.startsWith("others")) {
                return Observable.just(MangasPage(emptyList(), false))
            }

            val searchUrl = if (baseUrl.endsWith("/")) baseUrl + slug else "$baseUrl/$slug/"
            return client.newCall(GET(searchUrl, headers))
                .asObservableSuccess()
                .map { response -> searchMangaSlug(response, searchUrl) }
        }

        return fetchPopularManga(page).map { mp ->
            mp.copy(
                mangas = mp.mangas.filter {
                    it.title.contains(query, true)
                },
            )
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    private fun searchMangaSlug(response: Response, searchUrl: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.setUrlWithoutDomain(searchUrl)
        return MangasPage(listOf(details), false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(hostRoot + manga.url, headers)

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val details = client.newCall(mangaDetailsRequest(manga)).asObservableSuccess().map { mangaDetailsParse(it) }
        val statusObservable = client.newCall(GET(baseUrl, headers)).asObservableSuccess().map { parseStatus(it, manga.url) }

        return Observable.zip(details, statusObservable) { d, s -> d.apply { status = s } }
    }

    private fun parseStatus(response: Response, mangaUrl: String): Int {
        val doc = response.asJsoup()
        val mangas = doc.select("#lineup_recent > div > section, #lineup > div > section:not(.zadankai):not([id])")
        val entry = mangas.firstOrNull { it.selectFirst("div.hgroup > h3 > a")?.attr("href") == mangaUrl }

        return if (entry?.selectFirst("ul > li:last-child > em.is-completed") == null) {
            SManga.ONGOING
        } else {
            SManga.COMPLETED
        }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            // We need to get the title and thumbnail again.
            // This is only needed if you search by slug, as we have no information about the them.
            // Interestingly the page body has no mention of the title at all. It only exists in <title>
            val match = TITLE_REGEX.matchEntire(document.title())
            title = match?.groups?.get(1)?.value ?: document.title()
            // Twi4 uses the exact same thumbnail at both the main page and manga details
            thumbnail_url = document.selectFirst("#introduction > header > div > h2 > img")?.attr("abs:src")
            description = document.selectFirst("#introduction > div > div > p")?.text()

            // Determine who are the authors and artists
            // 作者, 原作 -> Author (Also the artist) / Original author (Such as light novel adaptation)
            // 漫画 -> Artist only
            // 提供, etc, etc -> Sponsors, irrelevant stuff
            val staffs = document.select("#introduction > div > section > header > div > h3")
            for (staff in staffs) {
                val role = staff.selectFirst("small")?.text()?.replace("：", "")?.trim() ?: continue
                val name = staff.selectFirst("span")?.text() ?: continue

                when (role) {
                    "作者" -> {
                        author = name
                        artist = name
                    }
                    // If 作者 and 原作 appear at the same time, 原作 will overwrite the author field
                    "原作" -> author = name
                    "漫画" -> artist = name
                }
            }
            // While the status can be obtained at the home page, there is no such info at the details page
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(hostRoot + manga.url, headers)

    // They have a <noscript> layout! This is surprising
    // Though their manga pages fails to load as it relies on JS
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()
        val allChapters = doc.select("#backnumbers > div > ul > li")

        val ret = allChapters.mapNotNull { chapter ->
            val a = chapter.selectFirst("a") ?: return@mapNotNull null
            val match = CHAPTER_REGEX.matchEntire(a.text())
            val chapNumber = match?.groups?.get(2)?.value

            SChapter.create().apply {
                if (chapNumber != null) {
                    this.chapter_number = chapNumber.toFloat()
                    this.name = "${chapNumber.toInt()} - ${match.groups[1]?.value}"
                    // We can't determine the upload date from the website
                    // Leaving date_upload unset
                } else {
                    this.name = a.text()
                }
                setUrlWithoutDomain(a.attr("abs:href"))
            }
        }

        return ret.sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(hostRoot + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        // The site interprets 1 page == 1 chapter
        // There should only be 1 article in the document
        val page = doc.selectFirst("article.comic") ?: return emptyList()
        val img = page.selectFirst("div > div > p > img") ?: return emptyList()

        return listOf(Page(0, imageUrl = img.attr("abs:src")))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
