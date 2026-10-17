package eu.kanade.tachiyomi.extension.all.manhwadashraw

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.Normalizer
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class ManhwaDashRaw : MadaraNoAjax() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override val mangaDetailsSelectorStatus = "div.post-content_item:contains(Status) > div.summary-content"
    override val mangaDetailsSelectorDescription = "div.post-content_item:contains(Summary) div.summary-container"
    override val pageListParseSelector = "div.page-break img.wp-manga-chapter-img"

    private var searchToken: String? = null

    // The site answers WordPress search (?s=) with HTTP 410 and serves results from
    // /search/<token>/<slug>/ instead, the token coming from the homepage search form.
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return super.getSearchMangaList(page, query, filters)

        val slug = query.toSearchSlug()
        if (slug.isEmpty()) return MangasPage(emptyList(), false)

        val token = searchToken?.takeIf { page > 1 }
            ?: client.get(baseUrl).asJsoup().selectFirst("input[name=mbk_token]")!!.attr("value").also { searchToken = it }

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("search/$token/$slug/")
            if (page > 1) addPathSegments("page/$page/")
        }.build()

        val document = client.get(url).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst(nextPageSelector()) != null)
    }

    // Same transformation as the site's own search form script.
    private fun String.toSearchSlug() = Normalizer.normalize(lowercase().trim(), Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace(NON_SLUG_CHARS, "-")
        .trim('-')

    companion object {
        private val DIACRITICS = Regex("[\\u0300-\\u036f]")
        private val NON_SLUG_CHARS = Regex("[^a-z0-9]+")
    }
}
