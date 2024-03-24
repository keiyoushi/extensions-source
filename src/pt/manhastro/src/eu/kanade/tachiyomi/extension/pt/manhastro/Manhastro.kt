package eu.kanade.tachiyomi.extension.pt.manhastro

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Manhastro : Madara(
    "Manhastro",
    "https://manhastro.com",
    "pt-BR",
    SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(1, 2, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorTitle = "div.summary_content h2"

    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override fun pageListParse(document: Document): List<Page> {
        return document.selectFirst("script:containsData(imageLinks)")?.data()
            ?.let { imageLinksPattern.find(it)?.groups?.get(1)?.value }
            ?.let { json.decodeFromString<List<String>>(it) }
            ?.mapIndexed { i, imageUrlEncoded ->
                val imageUrl = String(Base64.decode(imageUrlEncoded, Base64.DEFAULT))
                Page(i, document.location(), imageUrl)
            } ?: emptyList()
    }

    private val imageLinksPattern = """var\s+?imageLinks\s*?=\s*?(\[.*]);""".toRegex()
}
