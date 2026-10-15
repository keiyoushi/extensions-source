package eu.kanade.tachiyomi.extension.zh.toptoon

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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import java.time.format.DateTimeFormatter

@Source
abstract class Toptoon : KeiSource() {

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val jsonUrl = client.get("$baseUrl/ranking").use { it.body.string() }
            .substringAfter("jsonFileUrl: [\"")
            .substringBefore("\"")
            .replace("\\/", "/")
        val mangas = client.get("https:$jsonUrl").parseAs<PopularResponseDto>().adult.map {
            it.toSManga()
        }
        return MangasPage(mangas, false)
    }

    // Latest

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = fetchAllManga()
            .sortedByDescending { it.pubDate }
            .map {
                it.toSManga()
            }
        return MangasPage(mangas, false)
    }

    // Search

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = fetchAllManga()
            .map {
                it.toSManga()
            }
            .filter { it.title.contains(query, true) || it.author!!.contains(query, true) }
        return MangasPage(mangas, false)
    }

    private suspend fun fetchAllManga(): Collection<MangaDto> {
        val jsonUrl = client.get("$baseUrl/search").use { it.body.string() }
            .substringAfter("var jsonFileUrl = '")
            .substringBefore("'")
        return client.get("https:$jsonUrl").parseAs<Map<String, MangaDto>>().values
    }

    // Details & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        if (response.request.url.pathSegments[0].isEmpty()) {
            response.close()
            throw Exception("请到WebView确认年满18岁")
        }
        val document = response.asJsoup()

        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("section.infoContent div.title")!!.text()
            thumbnail_url = document.selectFirst("div.comicThumb img")!!.absUrl("src")
            author = document.selectFirst("section.infoContent div.etc")!!.text()
                .substringAfter("作家 : ").substringBefore("|")
            description = document.selectFirst("div.comic_story div.desc")!!.text()
            genre = document.selectFirst("section.infoContent div.hashTag")?.text()
                ?.replace("#", ", ")
            if (document.selectFirst("div.etc span.comicDayBox") != null) {
                status = SManga.ONGOING
            } else if (document.selectFirst("div.hashTag a[href=/search/keyword/79]") != null) {
                status = SManga.COMPLETED
            }
        }

        val chapterList = document.select("section.episode_area ul.list_area li.episodeBox").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst("a")!!.absUrl("href"))
                name = if (it.selectFirst("button.coin, button.gift, button.waitFree") != null) {
                    "\uD83D\uDD12" // lock emoji
                } else {
                    ""
                } + it.selectFirst("div.title")!!.text() + " " +
                    it.selectFirst("div.subTitle")!!.text()
                date_upload = dateFormat.tryParseDate(it.selectFirst("div.pubDate")?.text())
            }
        }.asReversed()

        return SMangaUpdate(details, chapterList)
    }

    // Pages

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val pathSegments = response.request.url.pathSegments
        if (pathSegments[0].isEmpty()) {
            response.close()
            throw Exception("请到WebView确认年满18岁")
        } else if (pathSegments.size < 2 || pathSegments[1] != "epView") {
            response.close()
            throw Exception("请确认是否已登录解锁")
        }
        val document = response.asJsoup()
        val images = document.select("article.epContent section.imgWrap div.cImg img")
        return images.mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("data-src"))
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
