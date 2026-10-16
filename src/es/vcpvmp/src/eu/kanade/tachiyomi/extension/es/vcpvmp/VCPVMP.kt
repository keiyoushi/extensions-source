package eu.kanade.tachiyomi.extension.es.vcpvmp

import eu.kanade.tachiyomi.multisrc.vercomics.VerComics
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Element

@Source
abstract class VCPVMP : VerComics() {
    override val urlSuffix = when (name) {
        "VCP" -> "sex-comics"
        "VMP" -> "xxx"
        else -> super.urlSuffix
    }

    override val genreSuffix = when (name) {
        "VCP" -> "tags"
        "VMP" -> "tag"
        else -> super.genreSuffix
    }

    override fun popularMangaSelector() = when (name) {
        "VCP" -> "article.pp-card:not(.pp-card-pinned)"
        else -> super.popularMangaSelector()
    }

    override fun popularMangaNextPageSelector() = when (name) {
        "VCP" -> "a.next.page-numbers"
        else -> super.popularMangaNextPageSelector()
    }

    override fun popularMangaFromElement(element: Element) = when (name) {
        "VCP" -> SManga.create().apply {
            val link = element.selectFirst("a.pp-card-link")!!
            setUrlWithoutDomain(link.absUrl("href"))
            title = link.selectFirst("h3.pp-card-title")!!.text()
            thumbnail_url = link.selectFirst("img")?.imgAttr()
        }
        else -> super.popularMangaFromElement(element)
    }

    override val detailsSelector = when (name) {
        "VCP" -> "div.pp-single"
        else -> super.detailsSelector
    }

    override val genreSelector = when (name) {
        "VCP" -> "div.pp-taglist a.pp-tag"
        else -> super.genreSelector
    }

    override val pageListSelector = when (name) {
        "VCP" -> "div.pp-comic-content img"
        else -> super.pageListSelector
    }

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
            Pair("Ahegao", "ahegao"),
            Pair("Anal", "anal"),
            Pair("Big Ass", "big-ass"),
            Pair("Big Breasts", "big-breasts"),
            Pair("Big Penis", "big-penis"),
            Pair("BlowJob", "blowjob"),
            Pair("Creampie", "creampie"),
            Pair("Cum", "cum"),
            Pair("Hairy", "hairy"),
            Pair("Incest", "incest"),
            Pair("Manga Hentai", "manga-hentai"),
            Pair("Milf", "milf"),
            Pair("Mosaic Censorship", "mosaic-censorship"),
            Pair("Nakadashi", "nakadashi"),
            Pair("Paizuri", "paizuri"),
            Pair("Schoolgirl Uniform", "schoolgirl-uniform"),
            Pair("Sin Censura", "sin-censura"),
            Pair("Squirting", "squirting"),
            Pair("Student", "student"),
            Pair("Unusual Pupils", "unusual-pupils"),
        )
        else -> super.genres
    }
}
