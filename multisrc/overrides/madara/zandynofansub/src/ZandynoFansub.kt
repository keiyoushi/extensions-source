package eu.kanade.tachiyomi.extension.en.zandynofansub

import eu.kanade.tachiyomi.multisrc.madara.Madara
class ZandynoFansub : Madara("Zandy no Fansub", "https://zandynofansub.aishiteru.org", "en") {
    // Migrating from FoolSlide to Madara
    override val versionId = 2
}
