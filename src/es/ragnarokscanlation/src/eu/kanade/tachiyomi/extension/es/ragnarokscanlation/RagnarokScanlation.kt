package eu.kanade.tachiyomi.extension.es.ragnarokscanlation

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class RagnarokScanlation :
    Madara(
        "Ragnarok Scanlation",
        "https://ragnarokscanlation.org",
        "es",
        SimpleDateFormat("MMMM d, yyyy", Locale("en")),
    ) {
    override val versionId = 2

    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true

    override val mangaSubString = "series"

    override fun pageListParse(document: Document): List<Page> {
        val rkScript = document.selectFirst("script:containsData(var RK =)")?.data()
        val rkNonce = rkScript?.substringAfter("\"nonce\":\"", "")?.substringBefore("\"")

        // Fallback to standard Madara processing if the Reader Knight plugin isn't detected
        if (rkNonce.isNullOrEmpty()) {
            return super.pageListParse(document)
        }

        val chapterId = document.selectFirst("input#wp-manga-current-chap")?.attr("data-id") ?: ""
        val mangaId = document.selectFirst(".chapter-selection")?.attr("data-manga") ?: ""

        if (chapterId.isEmpty() || mangaId.isEmpty()) {
            return super.pageListParse(document)
        }

        val tokenRequest = POST(
            "$baseUrl/wp-admin/admin-ajax.php",
            headersBuilder().add("X-Requested-With", "XMLHttpRequest").build(),
            FormBody.Builder()
                .add("action", "rk_get_token")
                .add("nonce", rkNonce)
                .add("chapter_id", chapterId)
                .add("manga_id", mangaId)
                .build(),
        )

        val tokenResponse = client.newCall(tokenRequest).execute().parseAs<ReaderKnightResponse>()

        if (!tokenResponse.success) {
            throw Exception("Failed to get reader token")
        }

        val token = tokenResponse.token ?: throw Exception("No token found")
        val readerUrl = tokenResponse.readerUrl ?: throw Exception("No reader url found")

        val readerRequest = POST(
            readerUrl,
            headersBuilder()
                .add("Origin", baseUrl)
                .add("Referer", document.location())
                .build(),
            FormBody.Builder()
                .add("rt", token)
                .add("chapter_id", chapterId)
                .add("manga_id", mangaId)
                .build(),
        )

        // OkHttp automatically follows the 303 Redirect emitted by the POST request here
        val readerDoc = client.newCall(readerRequest).execute().asJsoup()

        return readerDoc.select("img.rk-img").mapIndexed { i, img ->
            Page(i, document.location(), imageUrl = img.attr("abs:src"))
        }
    }
}

@Serializable
class ReaderKnightResponse(
    val success: Boolean = false,
    private val data: ReaderKnightData? = null,
) {
    val token get() = data?.token
    val readerUrl get() = data?.readerUrl
}

@Serializable
class ReaderKnightData(
    val token: String? = null,
    @SerialName("reader_url") val readerUrl: String? = null,
)
