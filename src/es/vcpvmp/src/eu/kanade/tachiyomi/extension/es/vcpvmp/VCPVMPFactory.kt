package eu.kanade.tachiyomi.extension.es.vcpvmp

import eu.kanade.tachiyomi.multisrc.vercomics.VerComics
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class VCPVMPFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        VCP(),
        VMP(),
    )
}

class VCP : VerComics("VCP", "https://vercomicsporno.com", "es") {

    override val urlSuffix = "comics-porno"
    override val genreSuffix = "etiquetas"
    override var genres =
        arrayOf(
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
}

class VMP : VerComics("VMP", "https://vermangasporno.com", "es") {

    override val urlSuffix = "xxx"
    override val genreSuffix = "tag"

    override var genres =
        arrayOf(
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
}
