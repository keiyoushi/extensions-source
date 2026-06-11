package eu.kanade.tachiyomi.extension.ar.hizomanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import okhttp3.FormBody
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class Hizomanga :
    Madara(
        "Hizo Manga",
        "https://hizomanga.net",
        "ar",
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
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

            add("vars[meta_query][0][key]", "_wp_manga_chapter_type")
            add("vars[meta_query][0][value]", "manga")

            add("vars[post_type]", "wp-manga")
            add("vars[post_status]", "publish")
            add("vars[meta_key]", if (popular) "_wp_manga_views" else "_latest_update")
            add("vars[order]", "desc")

            add("vars[sidebar]", "right")
            add("vars[manga_archives_item_layout]", "big_thumbnail")
        }.build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, formBody)
    }

    override val mangaDetailsSelectorDescription = ".manga-excerpt"
    override val mangaDetailsSelectorStatus = ".manga-status"
}
