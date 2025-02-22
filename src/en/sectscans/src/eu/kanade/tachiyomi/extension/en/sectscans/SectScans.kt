package eu.kanade.tachiyomi.extension.en.sectscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

class SectScans : Madara("SectScans", "https://sectscans.com", "en") {

    override val client = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val mangaSubString = "comics"

    override val useNewChapterEndpoint = true

    // =========================== Manga Details ============================

    override val mangaDetailsSelectorTitle = ".post-title"
    override val mangaDetailsSelectorAuthor = ".item_authors .summary-content"
    override val mangaDetailsSelectorArtist = ".item_artists .summary-content"
    override val mangaDetailsSelectorThumbnail = "img"
    override val mangaDetailsSelectorGenre = ".genres-content a"

    override fun mangaDetailsParse(document: Document): SManga {
        val postId = document.selectFirst("script:containsData(manga_id)")
            ?.data()
            ?.substringAfter("manga_id\":\"")
            ?.substringBefore("\"")
            ?: return super.mangaDetailsParse(document)

        val formBody = FormBody.Builder().apply {
            add("action", "madara_hover_load_post")
            add("postid", postId)
        }.build()

        val formHeaders = headersBuilder().apply {
            add("Accept", "text/html, */*; q=0.01")
            add("Host", baseUrl.toHttpUrl().host)
            add("Origin", baseUrl)
            add("X-Requested-With", "XMLHttpRequest")
        }.build()

        val resp = client.newCall(
            POST("$baseUrl/wp-admin/admin-ajax.php", formHeaders, formBody),
        ).execute()

        return super.mangaDetailsParse(resp.asJsoup()).apply {
            description = buildString {
                append(document.selectFirst(".manga-summary")?.text())
                append("\n\n")
                append(description)
            }
        }
    }
}
