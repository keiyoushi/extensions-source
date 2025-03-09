package eu.kanade.tachiyomi.extension.id.mangkomik

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class SirenKomik : MangaThemesia(
    "Siren Komik",
    "https://sirenkomik.my.id",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id")),
) {
    override val id = 8457447675410081142

    override val hasProjectPage = true

    override val seriesTitleSelector = "h1.judul-komik"
    override val seriesThumbnailSelector = ".gambar-kecil img"
    override val seriesGenreSelector = ".genre-komik a"
    override val seriesAuthorSelector = ".keterangan-komik:contains(author) span"
    override val seriesArtistSelector = ".keterangan-komik:contains(artist) span"

    override fun chapterListSelector() = ".list-chapter a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        name = element.selectFirst(".nomer-chapter")!!.text()
        date_upload = element.selectFirst(".tgl-chapter")?.text().parseChapterDate()
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        val postId = document.select("script").map(Element::data)
            .firstOrNull(postIdRegex::containsMatchIn)
            ?.let { postIdRegex.find(it)?.groups?.get(1)?.value }
            ?: throw IOException("Post ID not found")

        val pageUrl = "$baseUrl/wp-json/extras/v1/get-img-json".toHttpUrl().newBuilder()
            .addQueryParameter("post_id", postId)
            .build()

        val dto = client.newCall(GET(pageUrl, headers)).execute().use {
            json.decodeFromStream<SirenKomikDto>(it.body.byteStream())
        }

        return dto.pages.mapIndexed { index, imageUrl ->
            Page(index, document.location(), imageUrl)
        }
    }

    companion object {
        val postIdRegex = """postId.:(\d+)""".toRegex()
    }
}
