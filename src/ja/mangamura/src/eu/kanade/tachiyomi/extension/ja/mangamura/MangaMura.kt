package eu.kanade.tachiyomi.extension.ja.mangamura

import eu.kanade.tachiyomi.multisrc.mangareader.MangaReader
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

@Source
abstract class MangaMura : MangaReader() {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(2)

    override val chapterIdSelect = "ja-chaps"

    override fun addPage(page: Int, builder: HttpUrl.Builder) {
        builder.addQueryParameter("p", page.toString())
    }

    override fun getAjaxUrl(id: String): String = "$baseUrl/json/chapter?mode=vertical&id=$id"

    override val searchPathSegment = ""
    override val searchKeyword = "q"

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Note,
        TypeFilter(),
        StatusFilter(),
        LanguageFilter(),
        SortFilter(),
    )
}
