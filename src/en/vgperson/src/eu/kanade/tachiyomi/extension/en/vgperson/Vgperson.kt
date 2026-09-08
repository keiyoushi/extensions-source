package eu.kanade.tachiyomi.extension.en.vgperson

import android.os.Build.VERSION
import eu.kanade.tachiyomi.AppInfo
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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

@Source
abstract class Vgperson : KeiSource() {

    override val supportsLatest = false

    private val homeUrl = "$baseUrl/other/mangaviewer.php"

    private val userAgent =
        "Mozilla/5.0 (Android ${VERSION.RELEASE}; Mobile) Tachiyomi/${AppInfo.getVersionName()}"

    override fun Headers.Builder.configureHeaders() = apply {
        set("User-Agent", userAgent)
    }

    override fun getHomeUrl(): String = homeUrl

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(homeUrl).asJsoup()

        val mangas = document.select(".content a[href^=?m]").map { element ->
            SManga.create().apply {
                title = element.text()
                url = element.attr("href")
                thumbnail_url = getCover(title)
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(homeUrl + manga.url).asJsoup()

        val manga = SManga.create().apply {
            title = document.selectFirst(".title")!!.text()
            thumbnail_url = getCover(title)
            status = when (document.select("div.content .complete").text()) {
                "(Complete)" -> SManga.COMPLETED
                "(Series in Progress)" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            description = buildString {
                document.selectFirst(".content")!!.childNodes().drop(5).takeWhile {
                    it.nodeName() != "table"
                }.forEach {
                    if (it is TextNode) {
                        append(it.text())
                    } else if (it is Element) {
                        if (it.tagName() == "br") {
                            append("\n")
                        } else {
                            append(it.text())
                        }
                    }
                }
            }
        }

        val chapters = document.select(".chaptertable tbody tr").map { element ->
            SChapter.create().apply {
                element.selectFirst("td > a")!!.let {
                    name = it.text()
                    url = it.attr("href")
                }

                // append the name if it exists & remove the occasional hyphen
                element.selectFirst("td:last-child:not(:first-child)")?.let {
                    name += " - ${it.text().substringAfter("- ")}"
                }

                val fullUrl = "$homeUrl$url".toHttpUrl()

                // hardcode special chapter numbers for Three Days of Happiness
                chapter_number = fullUrl.queryParameter("c")?.toFloat()
                    ?: (16.5f + fullUrl.queryParameter("b")!!.toFloat() / 10)
                scanlator = "vgperson"
            }
        }.reversed()

        return SMangaUpdate(manga, chapters)
    }

    override fun getMangaUrl(manga: SManga): String = homeUrl + manga.url

    override fun getChapterUrl(chapter: SChapter): String = homeUrl + chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(homeUrl + chapter.url).asJsoup()

        return document.select("img").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = MangasPage(
        mangas = getPopularManga(1).mangas.filter { it.title.contains(query, true) },
        false,
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[1] != "mangaviewer.php" || !url.queryParameterNames.contains("m")) {
            return null
        }

        val slug = "?m=${url.queryParameter("m")}"
        val manga = SManga.create().apply {
            this.url = "?m=${url.queryParameter("m")}"
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                this.url = slug
                initialized = true
            }
    }

    // get known manga covers from imgur
    private fun getCover(title: String) = when (title) {
        "The Festive Monster's Cheerful Failure" -> "kEK10GL.png"
        "Azure and Claude" -> "buXnlmh.jpg"
        "Three Days of Happiness" -> "kL5dvnp.jpg"
        else -> null
    }?.let { "https://i.imgur.com/$it" }

    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()
}
