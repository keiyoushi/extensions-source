package eu.kanade.tachiyomi.extension.fr.sirenscansfr

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class SirenScansFR :
    Keyoapp(
        "Siren Scans FR",
        "https://sirenscans.fr",
        "fr",
    ) {
    override fun popularMangaSelector(): String = "section.splide.series-splide a.splide__slide"
}
