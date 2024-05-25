package eu.kanade.tachiyomi.extension.fr.frdashscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
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

        return chapterPreloaded
            .parseAs<List<String>>()
            .mapIndexed { index, imageUrl -> Page(index, imageUrl = imageUrl) }
    }

    private inline fun <reified T> Element.parseAs(): T {
        return json.decodeFromString(
            CHAPTER_PAGES_REGEX.find(data())?.groups?.get("pages")!!.value,
        )
    }

    companion object {
        val CHAPTER_PAGES_REGEX = """=\s+(?<pages>\[.+\])""".toRegex()
    }
}
