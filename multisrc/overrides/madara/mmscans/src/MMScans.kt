package eu.kanade.tachiyomi.extension.en.mmscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.nodes.Element

class MMScans : Madara("MMScans", "https://mm-scans.org", "en") {

    // The site customized the listing and does not include a .manga class.
    override val filterNonMangaItems = false

    override val popularMangaUrlSelector = "div.item-summary a"
    override fun chapterListSelector() = "li.chapter-li"
    override fun searchMangaSelector() = ".search-wrap >.tab-content-wrap > a"
    override fun searchMangaNextPageSelector(): String? = "body:not(:has(.no-posts))"

    fun oldLoadMoreRequest(page: Int, metaKey: String): Request {
        val form = FormBody.Builder()
            .add("action", "madara_load_more")
            .add("page", page.toString())
            .add("template", "madara-core/content/content-archive")
            .add("vars[paged]", "1")
            .add("vars[orderby]", "meta_value_num")
            .add("vars[template]", "archive")
            .add("vars[sidebar]", "right")
            .add("vars[post_type]", "wp-manga")
            .add("vars[post_status]", "publish")
            .add("vars[meta_key]", metaKey)
            .add("vars[meta_query][0][paged]", "1")
            .add("vars[meta_query][0][orderby]", "meta_value_num")
            .add("vars[meta_query][0][template]", "archive")
            .add("vars[meta_query][0][sidebar]", "right")
            .add("vars[meta_query][0][post_type]", "wp-manga")
            .add("vars[meta_query][0][post_status]", "publish")
            .add("vars[meta_query][0][meta_key]", metaKey)
            .add("vars[meta_query][relation]", "AND")
            .add("vars[manga_archives_item_layout]", "default")
            .build()

        val xhrHeaders = headersBuilder()
            .add("Content-Length", form.contentLength().toString())
            .add("Content-Type", form.contentType().toString())
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, form)
    }

    override fun popularMangaRequest(page: Int): Request {
        return oldLoadMoreRequest(page - 1, "_wp_manga_views")
    }
    override fun latestUpdatesRequest(page: Int): Request {
        return oldLoadMoreRequest(page - 1, "_latest_update")
    }
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select(popularMangaUrlSelector).first()?.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.selectFirst("h3")!!.ownText()
            }

            select("img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            select(chapterUrlSelector).first()?.let { urlElement ->
                chapter.url = urlElement.attr("abs:href").let {
                    it.substringBefore("?style=paged") + if (!it.endsWith(chapterUrlSuffix)) chapterUrlSuffix else ""
                }
                chapter.name = urlElement.selectFirst(".chapter-title-date p")!!.text()
            }
            chapter.date_upload = parseChapterDate(select(chapterDateSelector()).firstOrNull()?.text())
        }

        return chapter
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            manga.setUrlWithoutDomain(attr("abs:href"))
            select("div.post-title h3").first()?.let {
                manga.title = it.ownText()
            }
            select("img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    override val mangaDetailsSelectorDescription = "div.summary-text p"
    override val mangaDetailsSelectorGenre = "div.genres-content"
}
