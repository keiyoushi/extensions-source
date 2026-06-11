package eu.kanade.tachiyomi.extension.ar.hizomanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Request

class Hizomanga :
    Madara(
        "Hizo Manga",
        "https://hizomanga.net",
        "ar",
    ) {

    override val mangaSubString = "serie"

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun loadMoreRequest(page: Int, popular: Boolean): Request {
        val formBody = FormBody.Builder().apply {
            add("action", "madara_load_more")
            add("page", (page - 1).toString())

            add("template", "madara-core/content/content-archive")
            add("vars[orderby]", "meta_value_num")
            add("vars[paged]", "1")

            add("vars[tax_query][relation]", "OR")

            add("vars[meta_query][0][0][key]", "_wp_manga_chapter_type")
            add("vars[meta_query][0][0][value]", "manga")
            add("vars[meta_query][0][relation]", "AND")
            add("vars[meta_query][relation]", "AND")

            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[meta_key]", if (popular) "_wp_manga_views" else "_latest_update")
            add("vars[order]", "desc")

            add("vars[sidebar]", "right")
            add("vars[manga_archives_item_layout]", "big_thumbnail")
        }.build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, formBody)
    }
}
