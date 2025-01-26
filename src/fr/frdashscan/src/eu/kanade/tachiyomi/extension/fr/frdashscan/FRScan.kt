package eu.kanade.tachiyomi.extension.fr.frdashscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class FRScan : Madara("FR-Scan", "https://fr-scan.com", "fr", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRANCE)) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    override val chapterUrlSuffix = ""

    override fun pageListParse(document: Document): List<Page> {
        val chapterPreloaded = document.selectFirst("#chapter_preloaded_images")
            ?: return super.pageListParse(document)

        val content = CHAPTER_PAGES_REGEX.find(chapterPreloaded.data())?.groups?.get(1)!!.value
        val pages = json.decodeFromString<List<String>>(content)

        return pages.mapIndexed { index, imageUrl ->
            Page(index, document.location(), imageUrl)
        }
    }

    companion object {
        val CHAPTER_PAGES_REGEX = """=\s+(\[.+\])""".toRegex()
    }
}
