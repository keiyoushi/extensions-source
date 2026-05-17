package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MGKomik :
    Madara(
        "MG Komik",
        "https://id.mgkomik.cc",
        "id",
        SimpleDateFormat("dd MMM yy", Locale.US),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
    override val mangaSubString = "komik"
    override val chapterUrlSuffix = ""

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/$mangaSubString${if (page > 1) "/page/$page/" else "/"}".toHttpUrl().newBuilder()
            .addQueryParameter("m_orderby", "trending")
            .build()
        return GET(url, navHeaders())
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val formBody = FormBody.Builder()
            .add("action", "madara_load_more")
            .add("template", "madara-core/content/content-archive")
            .add("page", (page - 1).toString())
            .add("vars[paged]", "1")
            .add("vars[orderby]", "meta_value_num")
            .add("vars[meta_key]", "_latest_update")
            .add("vars[post_type]", "wp-manga")
            .add("vars[posts_per_page]", "20")
            .build()
        return POST(
            "$baseUrl/wp-admin/admin-ajax.php",
            xhrHeaders,
            formBody,
        )
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup(response.body.string().trim())
        val mangas = document.select("div.page-item-detail.manga").map { element: Element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = mangas.size == 20
        return MangasPage(mangas, hasNextPage)
    }

    override fun headersBuilder() = super.headersBuilder().apply {
        set("User-Agent", USER_AGENT)
        set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
        set("Accept-Language", "id-ID,id;q=0.9")
        set("Sec-CH-UA", "\"Chromium\";v=\"$CH_VERSION\", \"Not.A/Brand\";v=\"8\"")
        set("Sec-CH-UA-Arch", "\"\"")
        set("Sec-CH-UA-Bitness", "\"\"")
        set("Sec-CH-UA-Full-Version", "\"$CH_VERSION.0.7727.93\"")
        set("Sec-CH-UA-Full-Version-List", "\"Chromium\";v=\"$CH_VERSION.0.7727.93\", \"Not.A/Brand\";v=\"8.0.0.0\"")
        set("Sec-CH-UA-Mobile", "?1")
        set("Sec-CH-UA-Model", "\"\"")
        set("Sec-CH-UA-Platform", "\"Android\"")
        set("Sec-CH-UA-Platform-Version", "\"11.0.0\"")
        set("Upgrade-Insecure-Requests", "1")
        set("Priority", "u=0, i")
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val isAjax = path.contains("admin-ajax.php") ||
                path.contains("wp-json") ||
                path.contains("/ajax/")

            if (isAjax) {
                chain.proceed(
                    request.newBuilder()
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "same-origin")
                        .header("Origin", baseUrl)
                        .header("Priority", "u=1, i")
                        .removeHeader("Sec-Fetch-User")
                        .removeHeader("Upgrade-Insecure-Requests")
                        .build(),
                )
            } else {
                chain.proceed(request.newBuilder().removeHeader("X-Requested-With").build())
            }
        }
        .rateLimit(3)
        .build()

    override fun xhrChaptersRequest(mangaUrl: String): Request = POST("$mangaUrl/ajax/chapters/", xhrHeaders)

    override fun pageListRequest(chapter: SChapter): Request {
        val path = chapter.url
            .removePrefix(baseUrl)
            .let { if (it.startsWith("/")) it else "/$it" }

        val cleanUrl = "$baseUrl$path".substringBefore("?")
        val mangaUrl = cleanUrl.trimEnd('/')
            .substringBeforeLast("/")
            .trimEnd('/') + "/"

        return GET(cleanUrl, navHeaders(referer = mangaUrl))
    }

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl ?: page.url
        return GET(
            imageUrl,
            headersBuilder()
                .set("Referer", "$baseUrl/")
                .set("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .build(),
        )
    }

    private fun navHeaders(referer: String = "$baseUrl/") = headers.newBuilder()
        .set("Referer", referer)
        .set("Cache-Control", "max-age=0")
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "same-origin")
        .set("Sec-Fetch-User", "?1")
        .build()

    private fun Request.addSameOriginNavHeaders(): Request = newBuilder()
        .headers(navHeaders(referer = "$baseUrl/$mangaSubString/"))
        .build()

    override fun popularMangaSelector() = "div.page-item-detail.manga"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleLink = element.selectFirst(".post-title a")
        title = titleLink?.text()?.trim() ?: element.selectFirst("img")?.attr("alt")?.trim() ?: ""
        setUrlWithoutDomain(titleLink?.attr("abs:href").orEmpty())
        thumbnail_url = element.selectFirst("img")?.let { imageFromElement(it) }
    }

    override fun popularMangaNextPageSelector() = "div.wp-pagenavi a.page, div.wp-pagenavi a.last"

    override val mangaDetailsSelectorTitle = "div.post-title h1, div.post-title h3"
    override val mangaDetailsSelectorAuthor = "div.author-content > a"
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"
    override val mangaDetailsSelectorDescription = "div.description-summary div.summary__content p"
    override val mangaDetailsSelectorThumbnail = "div.summary_image img"
    override val mangaDetailsSelectorGenre = "div.genres-content a"

    override fun chapterListSelector() = "li.wp-manga-chapter"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = super.searchMangaRequest(page, query, filters).addSameOriginNavHeaders()

    override fun mangaDetailsRequest(manga: SManga): Request = super.mangaDetailsRequest(manga).addSameOriginNavHeaders()

    override fun chapterListRequest(manga: SManga): Request = super.chapterListRequest(manga).addSameOriginNavHeaders()

    override fun genresRequest(): Request = super.genresRequest().addSameOriginNavHeaders()

    override fun parseChapterDate(date: String?): Long {
        date ?: return 0L
        val trimmed = date.trim()

        if (trimmed.contains("yang lalu", ignoreCase = true)) {
            return parseRelativeDate(trimmed)
        }

        return super.parseChapterDate(trimmed)
    }

    override fun parseGenres(document: Document): List<Genre> = listOf(
        Genre("Action", "action"),
        Genre("Adaptation", "adaptation"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Animals", "animals"),
        Genre("Apocalypse", "apocalypse"),
        Genre("Comedy", "comedy"),
        Genre("Cooking", "cooking"),
        Genre("Crime", "crime"),
        Genre("Demons", "demons"),
        Genre("Drama", "drama"),
        Genre("Dungeons", "dungeons"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Fighting", "fighting"),
        Genre("Full Color", "full-color"),
        Genre("Game", "game"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Gore", "gore"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Kids", "kids"),
        Genre("Magic", "magic"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Military", "military"),
        Genre("Monsters", "monsters"),
        Genre("Murim", "murim"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("Office Workers", "office-workers"),
        Genre("One-Shot", "one-shot"),
        Genre("Overpowered", "overpowered"),
        Genre("Psychological", "psychological"),
        Genre("Regression", "regression"),
        Genre("Reincarnation", "reincarnation"),
        Genre("Revenge", "revenge"),
        Genre("Reverse Harem", "reverse-harem"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo AI", "shouai"),
        Genre("Shounen", "shounen"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Superhero", "superhero"),
        Genre("Supernatural", "supernatural"),
        Genre("Survival", "survival"),
        Genre("System", "system"),
        Genre("Thriller", "thriller"),
        Genre("Time Travel", "time-travel"),
        Genre("Tragedy", "tragedy"),
        Genre("Transmigration", "transmigration"),
        Genre("Vampire", "vampire"),
        Genre("Violence", "violence"),
        Genre("War", "war"),
        Genre("Webtoon", "webtoon"),
        Genre("Wuxia", "wuxia"),
        Genre("Yuri", "yuri"),
    )

    companion object {
        private const val CH_VERSION = "147"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CH_VERSION.0.0.0 Mobile Safari/537.36"
    }
}
