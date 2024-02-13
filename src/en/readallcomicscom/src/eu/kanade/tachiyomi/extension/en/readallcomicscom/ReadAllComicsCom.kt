package eu.kanade.tachiyomi.extension.en.readallcomicscom

import eu.kanade.tachiyomi.multisrc.readallcomics.ReadAllComics
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element

class ReadAllComicsCom : ReadAllComics("ReadAllComics", "https://readallcomics.com", "en") {

    override fun nullablePopularManga(element: Element): SManga? {
        return super.nullablePopularManga(element)?.apply {
            title = title.let {
                titleRegex.find(it)?.value?.trim()
                    ?.removeSuffix("v")?.trim()
                    ?.substringBeforeLast("vol")
                    ?: it
            }
        }
    }

    override fun pageListSelector() = "body img:not(body div[id=\"logo\"] img)"

    companion object {
        private val titleRegex = Regex("""^([a-zA-Z_.\s\-–:]*)""")
    }
}
