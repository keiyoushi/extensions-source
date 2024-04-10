package eu.kanade.tachiyomi.extension.all.asmhentai

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.galleryadults.GalleryAdults
import org.jsoup.nodes.Element

open class AsmHentai(
    lang: String = "all",
    final override val mangaLang: String = "",
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

    companion object {
        private const val TITLE_PREF = "Display manga title as:"
    }
}
