package eu.kanade.tachiyomi.extension.id.apkomik

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.FilterList
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class Apkomik : MangaThemesia() {
    override val hasProjectPage = true

    // The project list pages through /project/page/N/ and redirects ?page=N to its first page
    override fun searchMangaUrl(page: Int, query: String, filters: FilterList) = if (filters.firstInstanceOrNull<ProjectFilter>()?.selectedValue() == "project-filter-on") {
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(projectPageString.drop(1))
            if (page > 1) addPathSegment("page").addPathSegment(page.toString())
            addPathSegment("")
        }
    } else {
        super.searchMangaUrl(page, query, filters)
    }

    // apkomik.com links to the current domain of the site
    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = listOf(
        client.get(HUB_URL, Fetch.DOCUMENT).asJsoup()
            .selectFirst("a.link-card[href^=https://]")!!
            .absUrl("href"),
    )

    private companion object {
        const val HUB_URL = "https://apkomik.com/"
    }
}
