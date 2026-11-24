package eu.kanade.tachiyomi.extension.all.fastestmanga

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class FastestManga : MadaraNoAjax() {

    // admin-ajax.php answers 404 on every language site
    override val sendViewCount = false

    // "-" when a series has no type
    override val seriesTypeSelector = ".post-content_item:contains(Type) .summary-content:matches(\\p{L})"

    override val chapterDateFormat: DateTimeFormatter
        get() = when (lang) {
            "tr" -> TR_DATE_FORMAT
            "ar" -> AR_DATE_FORMAT
            else -> EN_DATE_FORMAT
        }

    // Search results carry the post id on their cover only
    override fun Element.postId(): String? = selectFirst("[data-post-id]")?.attr("data-post-id")
        ?: selectFirst("img[post-id]")?.attr("post-id")

    // Covers go through Jetpack's resizing proxy (i0.wp.com/<host>/<path>?resize=...)
    override fun processThumbnail(url: String?, fromSearch: Boolean): String? {
        val proxied = url?.toHttpUrlOrNull()?.takeIf { PHOTON_HOST_REGEX.matches(it.host) } ?: return url
        return "https://" + proxied.encodedPath.removePrefix("/")
    }

    // Each language is a WordPress site in its own folder: paths read from the site include it, the theme's own do not
    override fun archiveUrlBuilder(page: Int, order: String, path: String, query: String): HttpUrl.Builder = super.archiveUrlBuilder(page, order, if (path.startsWith("$folder/")) path else folder + path, query)

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaPath = chapter.memo["mangaPath"]!!.jsonPrimitive.content
        return baseUrl.toHttpUrl().resolve("${mangaPath.trimEnd('/')}/${chapter.url}/")!!.toString()
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.siteSegments() ?: return MangasPage(emptyList(), false)
        if (segments.size < 3 || segments[1] !in LIST_DIRECTORIES) return super.getMangasByUrl(url, page)
        return archivePage(page, "", "$folder/${segments[1]}/${segments[2]}/")
    }

    // Chapter links lead to their series
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.siteSegments() ?: return null
        if (segments.size < 3 || segments[1] != mangaSubString) return null
        return super.getMangaByUrl(baseUrl.toHttpUrl().resolve("$folder/$mangaSubString/${segments[2]}/")!!)
    }

    // The bare domain redirects to www, and the language sites share the host
    private fun HttpUrl.siteSegments(): List<String>? = encodedPathSegments.filter(String::isNotEmpty)
        .takeIf { host.removePrefix("www.") == baseUrl.toHttpUrl().host.removePrefix("www.") && it.firstOrNull() == folder.removePrefix("/") }

    private val folder get() = baseUrl.toHttpUrl().encodedPath

    companion object {
        private val EN_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
        private val TR_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("tr"))
        private val AR_DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ar"))

        private val PHOTON_HOST_REGEX = Regex("""i\d\.wp\.com""")
        private val LIST_DIRECTORIES = setOf("manga-genre", "manga-tag")
    }
}
