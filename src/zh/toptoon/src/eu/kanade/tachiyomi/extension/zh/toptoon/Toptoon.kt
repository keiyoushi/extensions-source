package eu.kanade.tachiyomi.extension.zh.toptoon

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.MarkdownDescription
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Source
abstract class Toptoon : KeiSource() {
    // Set by the site's 18+ toggle
    override fun OkHttpClient.Builder.configureClient() = addCookie("adultStatus" to "1")

    override suspend fun getPopularManga(page: Int): MangasPage {
        val jsonUrl = client.get("$baseUrl/ranking", Fetch.DOCUMENT).body.string()
            .substringAfter("jsonFileUrl: [\"")
            .substringBefore("\"")
            .replace("\\/", "/")
        val mangas = client.get("https:$jsonUrl", Fetch.XHR).parseAs<PopularResponseDto>().adult.map {
            it.toSManga()
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = fetchAllManga()
            .sortedByDescending { it.pubDate }
            .map {
                it.toSManga()
            }
        return MangasPage(mangas, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = fetchAllManga()
            .map {
                it.toSManga()
            }
            .filter { it.title.contains(query, true) || it.author?.contains(query, true) == true }
        return MangasPage(mangas, false)
    }

    private suspend fun fetchAllManga(): Collection<MangaDto> {
        val jsonUrl = client.get("$baseUrl/search", Fetch.DOCUMENT).body.string()
            .substringAfter("var jsonFileUrl = '")
            .substringBefore("'")
        return client.get("https:$jsonUrl", Fetch.XHR).parseAs<Map<String, MangaDto>>().values
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null
        if (segments.getOrNull(0) != "comic" || segments.getOrNull(1) !in listOf("epList", "epView")) return null
        val id = segments.getOrNull(2)?.toIntOrNull() ?: return null
        val manga = SManga.create().apply { this.url = "/comic/epList/$id" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()

        val details = SManga.create().apply {
            url = manga.url
            title = document.selectFirst("section.infoContent div.title")!!.text()
            thumbnail_url = document.selectFirst("div.comicThumb img")?.absUrl("src")
            author = document.selectFirst("section.infoContent div.etc")?.text()
                ?.substringAfter("作家 :")?.substringBefore("|")?.trim()
            description = MarkdownDescription.Builder()
                .description(document.selectFirst("div.comic_story div.desc"))
                .build()
            genre = document.select("section.infoContent div.hashTag a")
                .map { it.text().removePrefix("#") }
                .distinct()
                .joinToString()
                .ifEmpty { null }
            if (document.selectFirst("div.etc span.comicDayBox") != null) {
                status = SManga.ONGOING
            }
        }

        val chapterList = document.select("section.episode_area ul.list_area li.episodeBox").map {
            SChapter.create().apply {
                setUrlWithoutDomain(it.selectFirst("a")!!.absUrl("href"))
                name = if (it.selectFirst("button.coin, button.gift, button.waitFree") != null) {
                    "\uD83D\uDD12"
                } else {
                    ""
                } + it.selectFirst("div.title")!!.text() + " " +
                    it.selectFirst("div.subTitle")!!.text()
                date_upload = dateFormat.tryParseDate(it.selectFirst("div.pubDate")?.text(), ZoneId.of("Asia/Taipei"))
            }
        }.asReversed()

        return SMangaUpdate(details, chapterList)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter), Fetch.DOCUMENT).asJsoup()
        val images = document.select("article.epContent section.imgWrap div.cImg img")
        return images.mapIndexed { index, img ->
            Page(index, imageUrl = img.absUrl("data-src"))
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")
