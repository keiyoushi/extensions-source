package eu.kanade.tachiyomi.extension.en.philiascans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class PhiliaScans : Madara(
    "Philia Scans",
    "https://philiascans.org",
    "en",
    SimpleDateFormat("dd/MMM", Locale.US),
) {
    override val versionId: Int = 2
    override val useNewChapterEndpoint = true

    override fun popularMangaSelector(): String = ".manga__item"
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun searchMangaSelector(): String = popularMangaSelector()

    override val mangaDetailsSelectorTitle: String = "h1.post-title"
    override val mangaDetailsSelectorStatus: String = "div.summary-heading:contains(Status) + div"

    override fun popularMangaRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("post_type", "wp-manga")
            addQueryParameter("m_orderby", "views")
        }.build()
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("post_type", "wp-manga")
            addQueryParameter("m_orderby", "latest")
        }.build()
        return GET(url, headers)
    }

    // needed to exclude paid chapters
    override fun chapterListSelector(): String = """li.wp-manga-chapter:not(:has(a[href="#"]))"""
}
