package eu.kanade.tachiyomi.extension.tr.averisfansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class AverisFansub : Madara() {

    override val altNameSelector = ".post-content_item:contains(Diğer isimler) .summary-content"

    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT)

    override fun processThumbnail(url: String?, fromSearch: Boolean): String? = url?.replace(THUMBNAIL_SIZE_REGEX, "")

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val segments = url.siteSegments() ?: return MangasPage(emptyList(), false)
        if (segments.size < 2 || segments[0] !in LIST_DIRECTORIES) return super.getMangasByUrl(url, page)
        val listUrl = baseUrl.toHttpUrl().newBuilder()
            .addEncodedPathSegments("${segments[0]}/${segments[1]}/")
            .apply { if (page > 1) addPathSegments("page/$page/") }
            .build()
        val document = client.get(listUrl, Fetch.DOCUMENT).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst("div.nav-previous a") != null)
    }

    // Chapter links lead to their series
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.siteSegments() ?: return null
        if (segments.size < 2 || segments[0] != mangaSubString) return null
        return super.getMangaByUrl(baseUrl.toHttpUrl().newBuilder().addEncodedPathSegments("$mangaSubString/${segments[1]}/").build())
    }

    // www redirects to the bare domain
    private fun HttpUrl.siteSegments(): List<String>? = encodedPathSegments.filter(String::isNotEmpty)
        .takeIf { host.removePrefix("www.") == baseUrl.toHttpUrl().host }

    companion object {
        private val LIST_DIRECTORIES = setOf("manga-genre", "manga-tag", "manga-author", "manga-artist", "manga-release")

        // WordPress resizes ("-175x238") of the original cover
        private val THUMBNAIL_SIZE_REGEX = Regex("""-\d+x\d+(?=\.\w+$)""")
    }
}
