package eu.kanade.tachiyomi.extension.pt.montetai

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class MonteTai : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

    override val pageSize = 16

    override fun OkHttpClient.Builder.configureClient() = rateLimit(3, 1.seconds)

    override val mangaDetailsSelectorThumbnail = ".mtx-cover img"
    override val mangaDetailsSelectorAuthor = ".mtx-side-item:contains(Autor) .mtx-side-value"
    override val mangaDetailsSelectorArtist = ".mtx-side-item:contains(Artista) .mtx-side-value"
    override val mangaDetailsSelectorGenre = ".mtx-chip-list a"
    override val mangaDetailsSelectorDescription = ".mtx-synopsis"
    override val mangaDetailsSelectorStatus = ".mtx-pill-status"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document?): List<SChapter> {
        val script = mangaPage!!.selectFirst("#mt-header-js-js-extra")!!.data()
        val nonce = NONCE_REGEX.find(script)!!.groupValues.last()

        val body = FormBody.Builder()
            .add("action", "mt_get_summary_chapters")
            .add("nonce", nonce)
            .add("manga_id", id)
            .build()

        val chapterHeaders = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        val response = client.post("$baseUrl/wp-admin/admin-ajax.php", chapterHeaders, body)
        return response.body.string().parseAs<ChapterListDto>()
            .toSChapterList(::parseChapterDate)
    }

    companion object {
        val NONCE_REGEX = """(?:nonce"[^"]+")([^"]+)""".toRegex()
    }
}
