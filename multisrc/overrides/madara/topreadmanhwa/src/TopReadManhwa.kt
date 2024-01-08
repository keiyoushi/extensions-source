package eu.kanade.tachiyomi.extension.en.topreadmanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class TopReadManhwa : Madara(
    "TopReadManhwa",
    "https://topreadmanhwa.com",
    "en",
    SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override val useNewChapterEndpoint = true

    // Popular

    override val mangaEntrySelector = ""

    override val popularMangaUrlSelector = "div.post-title a:not([target])"

    // Details

    private val descriptionSelector = "div.description-summary div.summary__content h3:contains(description) + *"

    override val mangaDetailsSelectorDescription = "$descriptionSelector, ${super.mangaDetailsSelectorDescription}"

    override fun mangaDetailsParse(document: Document): SManga {
        return super.mangaDetailsParse(document).apply {
            document.selectFirst(descriptionSelector)?.run {
                description = "${text().trim()}\n\n$description"
            }

            // Attempt to filter out things that aren't part of the series description
            description = description?.run {
                split("\n\n").filterNot { block ->
                    block.contains("topreadmanhwa", true) ||
                        block.contains("topreadmanwha", true) ||
                        block.contains("Please share your thoughts", true) ||
                        block.contains("If you're a fan of", true) ||
                        block.contains("Happy reading", true)
                }
                    .distinct() // Edge case where the element in `descriptionSelector` can contain <p> tags
                    .joinToString("\n\n")
            }
        }
    }
}
