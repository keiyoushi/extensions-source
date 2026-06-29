package eu.kanade.tachiyomi.extension.es.chochox

import eu.kanade.tachiyomi.multisrc.vercomics.VerComics

class Chochox : VerComics("ChoChoX", "https://chochox.com", "es") {

    override val urlSuffix = "porno"
    override val genreSuffix = "tag"
    override val useSuffixOnSearch = false

    override var genres =
        arrayOf(
            Pair("Ver todos", ""),
            Pair("Anal", "anal-xxx-comics"),
            Pair("Comics Porno 3D", "comics-3d"),
            Pair("Culonas", "culonas-comicsporno-xxx"),
            Pair("Dragon Ball", "dragon-ball-porno"),
            Pair("Full Color", "full-color"),
            Pair("Furry Hentai", "furry-hentai-comics"),
            Pair("Futanari", "futanari-comics"),
            Pair("Hinata XXX", "hinata-xxx"),
            Pair("Lesbianas", "lesbianas"),
            Pair("Mamadas", "mamadas-comics-porno"),
            Pair("Milfs", "milfs-porno-comics"),
            Pair("My Hero Academia XXX", "my-hero-academia-xxx"),
            Pair("Naruto Hentai XXX", "naruto-hentai-xxx"),
            Pair("Parodia Porno", "parodia-porno"),
            Pair("Parodias Porno", "parodias-porno-comics-porno"),
            Pair("Series TV Porno", "series-tv-xxx-comics-porno"),
            Pair("Sonic", "sonic"),
            Pair("Steven Universe", "steven-universe-xxx"),
            Pair("Tetonas", "tetonas-comics"),
            Pair("Vaginal", "vaginal-comics-porno"),
        )
}
