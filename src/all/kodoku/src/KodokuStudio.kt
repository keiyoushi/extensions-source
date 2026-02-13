package eu.kanade.tachiyomi.extension.all.kodoku

import eu.kanade.tachiyomi.multisrc.madara.Madara

class KodokuStudio :
    Madara(
        "Kodoku Studio",
        "https://kodokustudio.com",
        "all",
    ) {
    override val mangaSubString = "manhua"
}
