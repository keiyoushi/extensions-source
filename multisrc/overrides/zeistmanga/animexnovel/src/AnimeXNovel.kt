package eu.kanade.tachiyomi.extension.pt.animexnovel

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

class AnimeXNovel : ZeistManga("AnimeXNovel", "https://www.animexnovel.com", "pt-BR") {

    override val mangaCategory: String = "Manga"

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.PopularPosts div.grid > figure").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
                title = element.selectFirst("figcaption > a")!!.text()
                setUrlWithoutDomain(element.selectFirst("figcaption > a")!!.attr("href"))
            }
        }.filter { it.title.contains("[Mangá]") }

        return MangasPage(mangas, false)
    }

    override val mangaDetailsSelectorDescription = "div.bc-fff.s1 > h3:contains(Sinopse) ~ div[style=text-align: justify;]"

    private val chapterListSelector = "div:has(> .list-judul:contains(Lista de Capítulos)) div#latest ul > li, div.tab:has(> label:contains(Lista de Capítulos)) div.tab-content ul > li"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(chapterListSelector)
        return chapters.map {
            SChapter.create().apply {
                name = it.select("a").text()
                setUrlWithoutDomain(it.select("a").attr("href"))
            }
        }
    }
}
