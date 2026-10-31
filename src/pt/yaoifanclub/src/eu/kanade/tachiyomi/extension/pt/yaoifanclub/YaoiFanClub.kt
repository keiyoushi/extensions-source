package eu.kanade.tachiyomi.extension.pt.yaoifanclub

import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class YaoiFanClub : MadaraNoAjax() {
    override val mangaSubString = "obra"
    override val genreDirectory = "obra-genre"

    // Skip novel cards ("page-item-detail text")
    override fun archiveSelector() = "div.page-item-detail.manga"

    // Full genre list is only on the advanced search form
    override suspend fun fetchFilterData(): JsonElement {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", "")
            .addQueryParameter("post_type", "wp-manga")
            .build()
        return client.get(url).asJsoup().select("div.checkbox-group input[name='genre[]']").map { input ->
            val slug = input.attr("value")
            GenreRoute(input.nextElementSibling()!!.text(), slug, "/$genreDirectory/$slug/")
        }.toJsonElement()
    }
}
