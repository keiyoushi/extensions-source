package eu.kanade.tachiyomi.extension.en.clonemanga

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup

@Source
abstract class CloneManga : KeiSource() {

    override val supportsLatest = false

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/viewer_landing.php").asJsoup()
        val mangas = document.getElementsByClass("comicPreviewContainer").map { element ->
            val attr = element.getElementsByClass("comicPreview").attr("style")
            SManga.create().apply {
                title = element.select("h3").first()!!.text()
                artist = "Dan Kim"
                author = "Dan Kim"
                status = SManga.UNKNOWN
                url = "/" + element.select("a").first()!!.attr("href").removePrefix("/")
                description = element.select("h4").first()?.text() ?: ""
                thumbnail_url = "$baseUrl/" + attr.substring(
                    attr.indexOf("site/themes"),
                    attr.indexOf(")"),
                )
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = getPopularManga(1).mangas.filter { it.title.contains(query, ignoreCase = true) }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val seriesPath = document.location().removePrefix(baseUrl)
        val scriptContent = document.getElementsByTag("script")[3].toString()
        val numChapters = PAGE_REGEX.findAll(scriptContent)
            .elementAt(3).destructured.component1()
            .toInt()

        val chapterList = ArrayList<SChapter>(numChapters)
        for (i in 1..numChapters) {
            chapterList.add(
                SChapter.create().apply {
                    url = "$seriesPath&page=$i"
                    name = "Chapter $i"
                    chapter_number = i.toFloat()
                },
            )
        }
        return SMangaUpdate(manga, chapterList.reversed())
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val imgAbsoluteUrl = document.getElementsByClass("subsectionContainer").first()!!
            .select("img").first()!!.absUrl("src")
        return listOf(Page(1, imageUrl = imgAbsoluteUrl))
    }

    companion object {
        private val PAGE_REGEX = Regex("""&page=(.*)&lang=""")
    }
}
