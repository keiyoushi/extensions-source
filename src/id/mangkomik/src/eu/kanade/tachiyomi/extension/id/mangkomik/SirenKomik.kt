package eu.kanade.tachiyomi.extension.id.mangkomik

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class SirenKomik :
    MangaThemesia(
        "Siren Komik",
        "https://sirenkomik.xyz",
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

        val payload = FormBody.Builder()
            .add("action", "get_image_json")
            .add("post_id", postId)
            .build()

        val response = client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", headers, payload))
            .execute()

        if (response.isSuccessful.not()) {
            throw IOException("Pages not found")
        }

        val dto = response.use {
            json.decodeFromStream<SirenKomikDto>(it.body.byteStream())
        }

        return dto.pages.mapIndexed { index, imageUrl ->
            Page(index, document.location(), imageUrl)
        }
    }

    companion object {
        val postIdRegex = """chapter_id\s*=\s*(\d+)""".toRegex()
    }
}
