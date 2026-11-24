package eu.kanade.tachiyomi.extension.es.vcpvmp

import eu.kanade.tachiyomi.multisrc.vercomics.VerComics
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class VCPVMP : VerComics() {
    override val urlSuffix = when (name) {
        "VCP" -> "sex-comics"
        "VMP" -> "manga-hentai-xxx"
        else -> super.urlSuffix
    }

    override val genreSuffix = "tags"

    override fun popularMangaSelector() = "section.pp-section:not(:has(.pp-random-more)) article.pp-card:not(.pp-card-pinned)"

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst("a.pp-card-link")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = link.selectFirst("h3.pp-card-title")!!.text()
        thumbnail_url = link.selectFirst("img")?.imgAttr()
    }

    override val detailsSelector = "article.pp-single"

    override val genreSelector = "div.pp-taglist a.pp-tag"

    override val pageListSelector = "div.pp-comic-content img"

    override var genres = when (name) {
        "VCP" -> arrayOf(
            Pair("Ver todos", ""),
            Pair("Anal", "anal"),
            Pair("Big Ass", "big-ass-xxx"),
            Pair("Big Breasts", "big-breasts-xxx"),
            Pair("Big Cock", "big-cock"),
            Pair("Big Penis", "big-penis"),
            Pair("Big Tits", "big-tits"),
            Pair("Blowjob", "blowjob"),
            Pair("Culonas", "culonas-xxx"),
            Pair("Cum", "cum"),
            Pair("Dark Skin", "dark-skin"),
            Pair("Furry", "furry-xxx"),
            Pair("Hot Girls", "hot-girls-xxx"),
            Pair("Incest", "incest"),
            Pair("Mamadas", "mamadas"),
            Pair("Muscle", "muscle"),
            Pair("Nakadashi", "nakadashi-xxx"),
            Pair("Sole Female", "sole-female-xxx"),
            Pair("Sole Male", "sole-male"),
            Pair("Tetonas", "tetonas-xxx"),
        )
        "VMP" -> arrayOf(
            Pair("Ver todos", ""),
            Pair("Ahegao", "ahegao-xxx"),
            Pair("Anal", "anal"),
            Pair("Big Ass", "big-ass-xxx"),
            Pair("Big Breasts", "big-breasts-xxx"),
            Pair("Big Penis", "big-penis-xxx"),
            Pair("BlowJob", "blowjob-xxx"),
            Pair("Creampie", "creampie-xxx"),
            Pair("Cum", "cum-xxx"),
            Pair("Hairy", "hairy-xxx"),
            Pair("Incest", "incest"),
            Pair("Manga Hentai", "xxx-manga-hentai"),
            Pair("Milf", "milfs-xxx-xxx"),
            Pair("Mosaic Censorship", "mosaic-censorship-xxx"),
            Pair("Nakadashi", "nakadashi-xxx"),
            Pair("Paizuri", "paizuri"),
            Pair("Schoolgirl Uniform", "schoolgirl-uniform"),
            Pair("Sin Censura", "sin-censura-xxx"),
            Pair("Squirting", "squirting-xxx"),
            Pair("Student", "student-xxx"),
            Pair("Unusual Pupils", "unusual-pupils-xxx"),
        )
        else -> super.genres
    }
}
