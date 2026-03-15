package eu.kanade.tachiyomi.extension.all.ninehentaimtl

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Ninehentaimtl :
    Madara(
        "9hentai MTL",
        "https://9hentai.lol",
        "all",
        dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US),
    ) {
    override val useNewChapterEndpoint = true

    override fun headersBuilder() = super.headersBuilder()
        .set("User-Agent", USER_AGENTS.random())
        .add("Referer", "$baseUrl/")

    override fun getFilterList() = FilterList()

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select("div.popular-item-wrap").map { item: Element ->
            SManga.create().apply {
                val a = item.selectFirst("div.popular-img a")!!
                setUrlWithoutDomain(a.attr("href"))
                title = item.selectFirst("h5.widget-title a")!!.text()
                thumbnail_url = item.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val body = FormBody.Builder()
            .add("action", "madara_load_more")
            .add("template", "madara-core/content/content-archive")
            .add("page", (page - 1).toString())
            .add("vars[orderby]", "meta_value_num")
            .add("vars[paged]", "1")
            .add("vars[posts_per_page]", "20")
            .add("vars[post_type]", "wp-manga")
            .add("vars[post_status]", "publish")
            .add("vars[meta_key]", "_latest_update")
            .add("vars[order]", "desc")
            .add("vars[sidebar]", "right")
            .add("vars[manga_archives_item_layout]", "default")
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", headers, body)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select("div.manga-card").map { card: Element ->
            SManga.create().apply {
                val a = card.selectFirst("a.manga-thumb")!!
                setUrlWithoutDomain(a.attr("href"))
                title = card.selectFirst("div.manga-title a")!!.text()
                thumbnail_url = card.selectFirst("img")?.absUrl("src")
            }
        }
        return MangasPage(mangas, mangas.isNotEmpty())
    }

    companion object {
        private val USER_AGENTS = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:135.0) Gecko/20100101 Firefox/135.0",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
        )
    }
}
