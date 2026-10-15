package eu.kanade.tachiyomi.extension.ja.jmanga

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import keiyoushi.annotation.Source
import okhttp3.HttpUrl

@Source
abstract class Jmanga : MangaReader() {

    override fun addPage(page: Int, builder: HttpUrl.Builder) {
        builder.addQueryParameter("p", page.toString())
    }

    // =============================== Search ===============================

    override val searchPathSegment = ""
    override val searchKeyword = "q"

    // ============================== Chapters ==============================

    override val chapterIdSelect = "ja-chaps"

    // =============================== Pages ================================

    override fun getAjaxUrl(id: String): String = "$baseUrl/json/chapter?mode=vertical&id=$id"
}
