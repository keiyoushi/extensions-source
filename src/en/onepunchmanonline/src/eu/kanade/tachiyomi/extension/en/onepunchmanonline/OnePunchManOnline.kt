package eu.kanade.tachiyomi.extension.en.onepunchmanonline

import eu.kanade.tachiyomi.multisrc.solomanga.SoloManga
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import okhttp3.Request
import org.jsoup.nodes.Document

@Source
abstract class OnePunchManOnline : SoloManga() {

    override val mangaTitle = "One Punch Man"

    override val chapterSlug = "one-punch-man-chapter-"

    // WordPress appends -1/-2 to duplicate slugs, so chapter-244-1 is Chapter 244
    override val chapterRegex = Regex("""one-punch-man-chapter-(\d+)(?:-\d+)?/?$""")

    override val imageHosts = listOf("images.mangafreak.me")

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        author = "ONE"
        artist = "Murata Yusuke"
        status = SManga.ONGOING
        genre = "Action, Comedy, Superhero, Seinen"
        description = "One-Punch Man is a superhero who has trained so hard that his hair has fallen out, and who can overcome any enemy with one punch."
    }

    // Image host (mangafreak) returns 403 when a Referer from this site is sent
    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .removeHeader("Referer")
        .build()
}
