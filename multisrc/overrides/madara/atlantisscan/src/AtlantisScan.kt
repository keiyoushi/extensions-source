package eu.kanade.tachiyomi.extension.es.atlantisscan

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class AtlantisScan : Madara(
    "Atlantis Scan",
    "https://scansatlanticos.com",
    "es",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US),
) {
    override val id: Long = 2237642340381856331

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override val useNewChapterEndpoint = true

    override fun popularMangaNextPageSelector() = "body:not(:has(.no-posts))"

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
