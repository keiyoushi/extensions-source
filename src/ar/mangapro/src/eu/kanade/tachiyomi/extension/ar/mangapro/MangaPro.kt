package eu.kanade.tachiyomi.extension.ar.mangapro

import eu.kanade.tachiyomi.multisrc.iken.Iken

class MangaPro : Iken(
    "Manga Pro",
    "ar",
    "https://prochan.net/",
    "https://api.promanga.net",
) {
    override val versionId = 4
}
