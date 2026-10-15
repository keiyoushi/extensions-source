package eu.kanade.tachiyomi.extension.es.mangatv

import android.util.Base64
import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.lib.unpacker.Unpacker
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class MangaTV : MangaThemesia() {
    override val mangaUrlDirectory = "/lista"
    override val datePattern = "yyyy-MM-dd"

    override val seriesDescriptionSelector = "b:contains(Sinopsis) + span"
    override fun chapterListSelector() = "#chapterlist ul.clstyle li:has(.dt a)"

    override fun pageListParse(document: Document): List<Page> {
        val unpackedScript = document.selectFirst("script:containsData(eval)")!!.data()
            .let(Unpacker::unpack)

        val imageListJson = JSON_IMAGE_LIST_REGEX.find(unpackedScript)?.destructured?.toList()?.get(0).orEmpty()
        val imageList = runCatching {
            imageListJson.replace(TRAILING_COMMA_REGEX, "]").parseAs<List<String>>()
        }.getOrElse { emptyList() }
        return imageList.mapIndexed { i, url ->
            val decodedLink = String(Base64.decode(url, Base64.DEFAULT))
            Page(i, imageUrl = "https:$decodedLink")
        }
    }

    override fun searchMangaUrl(page: Int, query: String) = baseUrl.toHttpUrl().newBuilder()
        .addPathSegment(mangaUrlDirectory.substring(1))
        .addQueryParameter("s", query)
        .addQueryParameter("page", page.toString())

    companion object {
        val TRAILING_COMMA_REGEX = """,\s+]""".toRegex()
    }
}
