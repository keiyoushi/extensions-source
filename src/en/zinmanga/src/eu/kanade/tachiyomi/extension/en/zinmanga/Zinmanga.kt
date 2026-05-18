package eu.kanade.tachiyomi.extension.en.zinmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Response
import org.jsoup.nodes.Element

class Zinmanga : Madara("Zinmanga", "https://mangazin.org", "en") {

    // The website does not flag the content consistently.
    override val filterNonMangaItems = false

    // ============================= Chapters ==============================

    // Zinmanga breaks and fails to load pages when ?style=list is appended
    override val chapterUrlSuffix = ""

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        launchIO { countViews(document) }

        var chapterElements = document.select(chapterListSelector())

        if (chapterElements.isEmpty()) {
            val mangaUrl = document.location().removeSuffix("/")

            chapterElements = client.newCall(POST("$mangaUrl/ajax/chapters", xhrHeaders))
                .execute()
                .use { it.asJsoup().select(chapterListSelector()) }

            if (chapterElements.isEmpty()) {
                val mangaId = document.selectFirst("input.rating-post-id")?.attr("value")
                if (mangaId != null) {
                    val oldXhrRequest = POST(
                        "$baseUrl/wp-admin/admin-ajax.php",
                        xhrHeaders,
                        FormBody.Builder()
                            .add("action", "manga_get_chapters")
                            .add("manga", mangaId)
                            .build(),
                    )
                    chapterElements = client.newCall(oldXhrRequest)
                        .execute()
                        .use { it.asJsoup().select(chapterListSelector()) }
                }
            }
        }

        return chapterElements.map(::chapterFromElement)
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = super.chapterFromElement(element)
        // Prefer storing relative URLs to follow contributing rules and avoid domain/slug mismatch issues
        chapter.setUrlWithoutDomain(chapter.url)
        return chapter
    }
}
