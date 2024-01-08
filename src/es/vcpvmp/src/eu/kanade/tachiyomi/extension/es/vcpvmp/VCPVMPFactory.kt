package eu.kanade.tachiyomi.extension.es.vcpvmp

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter

class VCPVMPFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        VCP(),
        VMP(),
    )
}

class VCP : VCPVMP("VCP", "https://vercomicsporno.com") {

    override val urlSuffix = "comics-porno"
    override val genreSuffix = "etiquetas"
    override var genres =
        arrayOf(
            Pair("Ver todos", ""),
            Pair("Anales", "anales"),
            Pair("Anime", "anime"),
            Pair("Aprobado por c1b3r3y3", "aprobado-por-c1b3r3y3"),
            Pair("Comics Incesto", "incesto-xxx"),
            Pair("Culonas", "culonas"),
            Pair("Furry", "furry-3"),
            Pair("Futanari", "futanari-2"),
            Pair("Lesbianas", "lesbianas"),
            Pair("Madre Hijo", "madre-hijo"),
            Pair("Mamadas", "mamadas"),
            Pair("Manga Hentai", "manga-hentai-3"),
            Pair("Masturbaciones", "madre-hijo"),
            Pair("Milfs", "milfs-xxx"),
            Pair("Orgias", "orgias"),
            Pair("Parodias Porno", "parodias-porno-xxx"),
            Pair("Rubias", "rubias"),
            Pair("Tetonas", "tetonas"),
            Pair("Trios", "trios"),
            Pair("Videojuegos", "videojuegos-2"),
            Pair("Yuri", "yuri-xxx"),
        )
}

class VMP : VCPVMP("VMP", "https://vermangasporno.com") {

    override val urlSuffix = "xxx"
    override val genreSuffix = "genero"

    override var genres =
        arrayOf(
            Pair("Ver todos", ""),
            Pair("Ahegao", "ahegao"),
            Pair("Big Ass", "big-ass"),
            Pair("Big Breasts", "big-breasts"),
            Pair("Blowjob", "blowjob"),
            Pair("Cheating", "cheating"),
            Pair("Creampie", "creampie"),
            Pair("Cum", "cum"),
            Pair("Group", "group"),
            Pair("Hairy", "hairy"),
            Pair("Kissing", "kissing"),
            Pair("Milf", "milf"),
            Pair("Mosaic Censorship", "mosaic-censorship"),
            Pair("Nakadashi", "nakadashi"),
            Pair("Schoolgirl Uniform", "schoolgirl-uniform"),
            Pair("Sin Censura", "sin-censura"),
            Pair("Sole Female", "sole-female"),
            Pair("Sole Male", "sole-male"),
            Pair("Squirting", "squirting"),
            Pair("Stockings", "stockings"),
            Pair("Unusual Pupils", "unusual-pupils"),
        )
}

open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
}
