package eu.kanade.tachiyomi.extension.ja.ohtawebcomic

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.speedbinb.SpeedBinbInterceptor
import keiyoushi.lib.speedbinb.fetchPages
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import okhttp3.OkHttpClient
import org.jsoup.nodes.Element

@Source
abstract class OhtaWebComic : KeiSource() {
    override val supportsLatest = false

    override fun OkHttpClient.Builder.configureClient() = addInterceptor(SpeedBinbInterceptor())

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/list/").asJsoup().select(".bnrList ul li a").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.absUrl("href"))
                title = it.selectFirst(".title")!!.text()
                thumbnail_url = it.selectFirst(".pic img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = client.get("$baseUrl/list/").asJsoup().select(".bnrList ul li a")
            .filter { it.selectFirst(".title")?.text()?.contains(query, true) == true }
            .map {
                SManga.create().apply {
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = it.selectFirst(".title")!!.text()
                    thumbnail_url = it.selectFirst(".pic img")?.absUrl("src")
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
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val details = SManga.create().apply {
            title = document.selectFirst("[itemprop=name]")!!.text()
            author = document.selectFirst("[itemprop=author]")?.text()
            thumbnail_url = document.selectFirst(".contentHeader")
                ?.attr("style")
                ?.substringAfter("background-image:url(")
                ?.substringBefore(");")
            description = buildString {
                var currentNode = document.selectFirst("h3.titleBoader:contains(作品について) + dl")
                    ?: return@buildString

                while (true) {
                    val nextSibling = currentNode.nextElementSibling()
                        ?: break

                    if (nextSibling.nodeName() != "p") {
                        break
                    }

                    appendLine(nextSibling.text())
                    currentNode = nextSibling
                }
            }
        }

        val chapterList = document.select(".backnumberList a[onclick*=openBook]")
            .sortedByDescending { it.selectFirst("dt.number")!!.ownText().toInt() }
            .map {
                SChapter.create().apply {
                    url = it.getChapterId()
                    name = it.selectFirst("div.title")!!.text()
                }
            }
            .ifEmpty {
                document.select(".headBtnList a[onclick*=openBook]").map {
                    SChapter.create().apply {
                        url = it.getChapterId()
                        name = it.ownText()
                    }
                }
            }

        return SMangaUpdate(
            details,
            chapterList,
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "https://www.yondemill.jp/contents/${chapter.url}?view=1&u0=1"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val readerUrl = client.get(getChapterUrl(chapter)).asJsoup()
            .selectFirst("script:containsData(location.href)")!!
            .data()
            .substringAfter("location.href='")
            .substringBefore("';")

        return client.fetchPages(client.get(readerUrl).asJsoup())
    }
}

private fun Element.getChapterId(): String = attr("onclick")
    .substringAfter("openBook('")
    .substringBefore("')")
