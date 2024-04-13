package eu.kanade.tachiyomi.extension.all.asmhentai

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import org.jsoup.nodes.Element

class AsmHentai(
    lang: String = "all",
    override val mangaLang: String = "",
) : GalleryAdults("AsmHentai", "https://asmhentai.com", lang) {
    protected open val displayFullTitle: Boolean
        get() = when (preferences.getString(TITLE_PREF, "full")) {
            "full" -> true
            else -> false
        }

    protected open val shortenTitleRegex = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")

    private fun String.shortenTitle() = this.replace(shortenTitleRegex, "").trim()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = TITLE_PREF
            title = TITLE_PREF
            entries = arrayOf("Full Title", "Short Title")
            entryValues = arrayOf("full", "short")
            summary = "Requires restart\n%s"
            setDefaultValue("full")
        }.also(screen::addPreference)

        super.setupPreferenceScreen(screen)
    }

    override fun Element.mangaTitle(selector: String) =
        mangaFullTitle(selector).let {
            if (displayFullTitle) it else it?.shortenTitle()
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
                if (!displayFullTitle) {
                    "\nFull title: ${mangaFullTitle("h1")}"
                } else {
                    ""
                },
            )
    }

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

            val xhrHeaders = headers.newBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
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

    companion object {
        private const val TITLE_PREF = "Display manga title as:"
    }
}
