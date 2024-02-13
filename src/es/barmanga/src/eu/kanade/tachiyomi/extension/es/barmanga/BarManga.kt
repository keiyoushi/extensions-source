package eu.kanade.tachiyomi.extension.es.barmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class BarManga : Madara(
    "BarManga",
    "https://barmanga.com",
    "es",
    SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
) {
    override fun popularMangaNextPageSelector() = "body:not(:has(.no-posts))"
    override val mangaDetailsSelectorDescription = "div.flamesummary > div.manga-excerpt"

    private fun loadMoreRequest(page: Int, metaKey: String): Request {
        val formBody = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", page.toString())
            add("template", "madara-core/content/content-archive")
            add("vars[paged]", "1")
            add("vars[orderby]", "meta_value_num")
            add("vars[template]", "archive")
            add("vars[sidebar]", "full")
            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[meta_key]", metaKey)
            add("vars[order]", "desc")
            add("vars[meta_query][relation]", "AND")
            add("vars[manga_archives_item_layout]", "big_thumbnail")
        }.build()

        val xhrHeaders = headersBuilder()
            .add("Content-Length", formBody.contentLength().toString())
            .add("Content-Type", formBody.contentType().toString())
            .add("X-Requested-With", "XMLHttpRequest")
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, formBody)
    }

    override fun popularMangaRequest(page: Int): Request {
        return loadMoreRequest(page - 1, "_wp_manga_views")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return loadMoreRequest(page - 1, "_latest_update")
    }
}
