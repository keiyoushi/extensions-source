package eu.kanade.tachiyomi.extension.es.vcpvmp

import eu.kanade.tachiyomi.multisrc.vercomics.VerComics
import keiyoushi.annotation.Source

@Source
abstract class VCPVMP : VerComics() {
    override val urlSuffix = when (name) {
        "VCP" -> "comics-porno"
        "VMP" -> "xxx"
        else -> super.urlSuffix
    }

    override val genreSuffix = when (name) {
        "VCP" -> "etiquetas"
        "VMP" -> "tag"
        else -> super.genreSuffix
    }

    override var genres = when (name) {
        "VCP" -> arrayOf(
            Pair("Ver todos", ""),
            Pair("Anal", "anal"),
            Pair("Big Ass", "big-ass"),
            Pair("Big Breasts", "big-breasts"),
            Pair("Big Cock", "big-cock"),
            Pair("Big Penis", "big-penis"),
            Pair("Big Tits", "big-tits"),
            Pair("Blowjob", "blowjob"),
            Pair("Culonas", "culonas"),
            Pair("Cum", "cum"),
            Pair("Dark Skin", "dark-skin"),
            Pair("Furry", "furry"),
            Pair("Hot Girls", "hot-girls"),
            Pair("Incest", "incest"),
            Pair("Mamadas", "mamadas"),
            Pair("Milf", "milf"),
            Pair("Muscle", "muscle"),
            Pair("Nakadashi", "nakadashi"),
            Pair("Sole Female", "sole-female"),
            Pair("Sole Male", "sole-male"),
            Pair("Tetonas", "tetonas"),
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
