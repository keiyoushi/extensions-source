package eu.kanade.tachiyomi.extension.es.manhuaonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class SamuraiScan : Madara(
    "SamuraiScan",
    "https://samurai.wordoco.com",
    "es",
    SimpleDateFormat("dd MMMM, yyyy", Locale("es")),
) {
    override val id = 5713083996691468192

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaSubString = "leer"

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val mangaDetailsSelectorDescription = "div.summary_content div.manga-summary"

    override fun pageListParse(document: Document) = super.pageListParse(document).map {
        it.apply {
            imageUrl = imageUrl
                ?.replace(SSL_REGEX, "https")
                ?.replace(WWW_REGEX, "")
        }
    }

    companion object {
        val SSL_REGEX = """https?""".toRegex()
        val WWW_REGEX = """[wW]{3}\.""".toRegex()
    }
}
