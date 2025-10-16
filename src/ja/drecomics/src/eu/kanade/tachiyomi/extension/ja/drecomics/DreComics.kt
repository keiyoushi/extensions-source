package eu.kanade.tachiyomi.extension.ja.drecomics

import eu.kanade.tachiyomi.multisrc.clipstudioreader.ClipStudioReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class DreComics : ClipStudioReader(
    "DRE Comics",
    "https://drecom-media.jp",
    "ja",
) {
    private val dateFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.JAPAN)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/drecomics/series", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".seriesList__item").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a")!!.attr("href"))
                title = it.selectFirst(".seriesList__text")!!.text()
                thumbnail_url = it.selectFirst("img")?.attr("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".detailComics_title span")!!.text()
            author = document.select(".detailComics_authorsItem").eachText().joinToString(", ")
            artist = author
            description = document.selectFirst(".detailComics_synopsis")?.text()
            genre = document.select(".detailComics_genreListItem").eachText().joinToString(", ")
            thumbnail_url = document.selectFirst(".detailComicsSection .img-fluid")?.attr("src")
            status = SManga.ONGOING
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.ebookListItem:not(.disabled) a.ebookListItem_title").map { chapter ->
            SChapter.create().apply {
                name = chapter.selectFirst(".ebookListItem_title")!!.text()
                setUrlWithoutDomain(chapter.selectFirst("a")!!.attr("href"))
                date_upload = dateFormat.tryParse(chapter.selectFirst(".ebookListItem_publishDate span")?.text()?.substringAfter("公開："))
            }
        }
    }
}
