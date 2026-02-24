package eu.kanade.tachiyomi.extension.all.kodoku.eu.kanade.tachiyomi.extension.all.kodokustudio

import eu.kanade.tachiyomi.multisrc.madara.Madara

class `KodokuStudio.kt` :
    Madara(
        "Kodoku Studio",
        "https://kodokustudio.com",
        "all",
    ) {
    override val useNewChapterEndpoint = true
    override val mangaSubString = "manhua"
}
