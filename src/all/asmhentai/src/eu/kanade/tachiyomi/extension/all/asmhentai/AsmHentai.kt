package eu.kanade.tachiyomi.extension.all.asmhentai

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdultsUtils.imgAttr
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AsmHentai(
    lang: String = "all",
    override val mangaLang: String = "",
) : GalleryAdults(
    "AsmHentai",
    "https://asmhentai.com",
    lang = lang,
) {

    override val supportsLatest = mangaLang.isNotBlank()
    private val SharedPreferences.shortTitle
        get() = getBoolean(PREF_SHORT_TITLE, false)

    private val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")

    private fun String.shortenTitle() = this.replace(shortenTitleRegex, "").trim()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHORT_TITLE
            title = "Display Short Titles"
            summaryOff = "Showing Long Titles"
            summaryOn = "Showing short Titles"
            setDefaultValue(false)
        }.also(screen::addPreference)

        super.setupPreferenceScreen(screen)
    }

    override fun Element.mangaTitle(selector: String) =
        mangaFullTitle(selector).let {
            if (preferences.shortTitle) it?.shortenTitle() else it
        }

    private fun Element.mangaFullTitle(selector: String) =
        selectFirst(selector)?.text()
            ?.replace("\"", "")?.trim()

    override fun Element.mangaUrl() =
        selectFirst(".image a")?.attr("abs:href")

    override fun Element.mangaThumbnail() =
        selectFirst(".image img")?.imgAttr()

    override fun Element.mangaLang() =
        select("a:has(.flag)").attr("href")
            .removeSuffix("/").substringAfterLast("/")

    override fun popularMangaSelector() = ".preview_item"

    override fun Element.getTag(tag: String): String {
        return select(".tags:contains($tag:) .tag")
            .joinToString { it.ownText().cleanTag() }
    }
    private fun String.cleanTag(): String = replace(Regex("\\(.*\\)"), "").trim()

    override fun Element.getDescription(): String {
        return (
            listOf("Parodies", "Characters", "Languages", "Category")
                .mapNotNull { tag ->
                    getTag(tag)
                        .let { if (it.isNotEmpty()) "$tag: $it" else null }
                } +
                listOfNotNull(
                    selectFirst(".book_page .pages h3")?.ownText()?.cleanTag(),
                    selectFirst(".book_page h1 + h2")?.ownText()?.cleanTag()
                        .let { altTitle -> if (!altTitle.isNullOrBlank()) "Alternate Title: $altTitle" else null },
                )
            )
            .joinToString("\n")
            .plus(
                if (preferences.shortTitle) {
                    "\nFull title: ${mangaFullTitle("h1")}"
                } else {
                    ""
                },
            )
    }

    /* Search */
    override val favoritePath = "inc/user.php?act=favs"

    override val mangaDetailInfoSelector = ".book_page"

    override val galleryIdSelector = "load_id"
    override val totalPagesSelector = "t_pages"
    override val pageUri = "gallery"
    override val pageSelector = ".preview_thumb"

    override fun pageListRequest(document: Document): List<Page> {
        val pageUrls = document.select("$pageSelector a")
            .map { it.absUrl("href") }
            .toMutableList()

        // input only exists if pages > 10 and have to make a request to get the other thumbnails
        val totalPages = document.inputIdValueOf(totalPagesSelector)

        if (totalPages.isNotEmpty()) {
            val token = document.select("[name=csrf-token]").attr("content")

            val form = FormBody.Builder()
                .add("_token", token)
                .add("id", document.inputIdValueOf(loadIdSelector))
                .add("dir", document.inputIdValueOf(loadDirSelector))
                .add("visible_pages", "10")
                .add("t_pages", totalPages)
                .add("type", "2") // 1 would be "more", 2 is "all remaining"
                .build()

            client.newCall(POST("$baseUrl/inc/thumbs_loader.php", xhrHeaders, form))
                .execute()
                .asJsoup()
                .select("a")
                .mapTo(pageUrls) { it.absUrl("href") }
        }
        return pageUrls.mapIndexed { i, url -> Page(i, url) }
    }

    override fun imageUrlParse(document: Document): String {
        return document.selectFirst("img#fimg")?.imgAttr()!!
    }

    /* Filters */
    override fun tagsParser(document: Document): List<Pair<String, String>> {
        return document.select(".tags_page ul.tags li")
            .mapNotNull {
                Pair(
                    it.selectFirst("a.tag")?.ownText() ?: "",
                    it.select("a.tag").attr("href")
                        .removeSuffix("/").substringAfterLast('/'),
                )
            }
    }

    override fun getFilterList() = FilterList(
        listOf(
            Filter.Header("HINT: Separate search term with comma (,)"),
        ) + super.getFilterList().list,
    )

    companion object {
        private const val PREF_SHORT_TITLE = "pref_short_title"
    }
}
