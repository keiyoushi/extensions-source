package eu.kanade.tachiyomi.extension.en.webtoonscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.post
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class WebtoonScan : Madara() {

    override val mangaSubString = "manhwa"
    override val genreDirectory = "genre"
    override val seriesTypeSelector = ".post-content_item:has(h5:matchesOwn(^\\s*Type\\s*$)) .summary-content"

    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH)

    override fun parseDetails(
        document: Document,
        id: String,
        preserveUrl: String?,
    ): SManga = super.parseDetails(document, id, preserveUrl).apply {
        val type = document.summaryRow("Type")?.textOrNull()
        genre = genre?.split(", ")?.filterNot { it == type }?.joinToString()?.ifBlank { null }

        description = MarkdownDescription.Builder().apply {
            val score = document.selectFirst("#averagerate")?.text()?.toDoubleOrNull()
            val votes = document.selectFirst("#countrate")?.text()?.toIntOrNull()
            val release = document.summaryRow("Release")?.textOrNull()
            val alts = document.summaryRow("Alternative")?.ownTextOrNull()
                ?.takeUnless(::isUpdating)
                ?.split(", ")
                .orEmpty()

            description(document.selectFirst(mangaDetailsSelectorDescription))
            rating(score, max = 5, votes = votes)
            info(release, type)
            altTitles(alts, label = "Alternative Name(s)")
        }.build()
    }

    private fun Document.summaryRow(label: String): Element? = select(".post-content_item")
        .firstOrNull { it.selectFirst("h5")?.text() == label }
        ?.selectFirst(".summary-content")

    override suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document?): List<SChapter> {
        val body = FormBody.Builder()
            .add("action", "wp-manga-get-chapters")
            .add("manga_id", id)
            .add("offset", "0")
            .build()
        val html = client.post("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, Fetch.XHR.from(mangaPath), body)
            .parseAs<ChaptersResponseDto>()
            .data.html
        return parseChapterList(Jsoup.parseBodyFragment(html, baseUrl), mangaPath)
    }
}

@Serializable
private class ChaptersResponseDto(
    val data: ChaptersDataDto,
)

@Serializable
private class ChaptersDataDto(
    val html: String,
)
